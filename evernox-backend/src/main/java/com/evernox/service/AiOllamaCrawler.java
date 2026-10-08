package com.evernox.service;

import com.evernox.dto.OllamaModelDto;
import com.evernox.dto.OllamaTagDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ollama 模型库采集器：抓取 ollama.com 本地可下载模型（模型族 + 尺寸变体）。
 *
 * 只负责「拉取 + 解析」，入库与增删比对交给 Service。
 * 不走站点翻译，固定英文请求头，保存英文原文。
 */
@Slf4j
@Component
public class AiOllamaCrawler {

    private static final String BASE = "https://ollama.com";
    /** 与 AiNewsCrawler 同风格的 UA */
    private static final String UA = "evernox-ollama-lib/1.0";
    /** 固定英文，避免站点侧本地化 */
    private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    /** 最多翻页数，兜底防死循环 */
    private static final int PAGE_SIZE_GUARD = 60;
    /**
     * 按需补全（按关键词）时的翻页上限。
     *
     * 30 页 × 20 条 ≈ 最多 600 个模型：实测宽泛关键词的深度都在这个范围内
     * （Qwen3.8 约 19 页、abliterated 约 29 页）。
     * 它是**安全上界而不是目标**——窄关键词拿到末页就停（如 Bonsai 2 27B 只有 1 页）。
     * 触到上限时 complete=false，Service 会在状态消息里如实提示「可能还有更多」。
     */
    public static final int ADHOC_PAGE_LIMIT = 30;
    /** 请求间隔，避免给官网压力 */
    private static final long THROTTLE_MS = 300L;
    private static final int REQUEST_TIMEOUT_MS = 15000;

    /** 列表页数组形态的参数量标签，如 27b / 35b / 128b / 35b-a3b */
    private static final Pattern SIZE_TAG = Pattern.compile("(?i)^\\d+(\\.\\d+)?b(-a\\d+b)?$");
    /** 从变体名提取参数量，如 27b-coding-mtp-q4_K_M → 27b */
    private static final Pattern PARAM_SIZE = Pattern.compile("(\\d+(?:\\.\\d+)?b)", Pattern.CASE_INSENSITIVE);
    /** 官网绝对时间形如 Sep 1, 2026 5:04 PM UTC */
    private static final DateTimeFormatter US_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US);

    private static final String CLOUD = "cloud";
    private static final String ABLITERATED = "abliterated";

    /** 采集入口：官方库。默认列表页只返回官方 /library/* 模型，不含任何社区模型 */
    private static final String PATH_OFFICIAL = "/search";
    /**
     * 采集入口：abliterated 定向检索。
     * 社区模型（如 huihui_ai/Qwen3.6-abliterated）不会出现在默认列表页，必须显式带 q 才拿得到；
     * 该检索约 16~29 页、≈400 个模型，分页哨兵会保留 &q=abliterated。
     */
    private static final String PATH_ABLITERATED = "/search?q=abliterated";

    /** 厂商关键词表（有序，先命中先生效） */
    private static final List<VendorRule> VENDOR_RULES = buildVendorRules();

    /** 一次全量抓取的结果：models + 是否完整翻完（中途请求失败则 complete=false，用于禁止删除） */
    public record ScrapeOutcome(List<OllamaModelDto> models, boolean complete) {
    }

    /**
     * 抓取全量本地可下载模型（云端模型已剔除）。
     *
     * 【多采集入口·重要】ollama.com 的默认列表页是「官方库」视图，只返回 /library/* 的官方模型，
     * 社区模型（带命名空间的，如 huihui_ai/Qwen3.6-abliterated）一条都不会出现。
     * 而 abliterated 模型几乎全是社区模型，因此必须额外走 `?q=abliterated` 定向检索，
     * 否则库中永远抓不到任何 abliterated 模型（版本筛选会恒为空）。
     *
     * 【分页机制·重要】ollama.com 的搜索页是 htmx 驱动的懒加载：
     *  - 列表接口只有在带 `HX-Request: true` 头时才认 `page` 参数；
     *    不带该头时服务端会把 `/search?page=N` 退回第一页（浏览器地址栏直接访问也会被重定向），
     *    于是每一页都解析出同一批数据，看起来"只同步到第一页"。
     *  - 真正的"还有下一页"信号是片段末尾的哨兵 `<li hx-get="/search?page=N+1">`，
     *    最后一页没有哨兵。这里直接跟随哨兵，不自己拼页码。
     *
     * complete=false 表示任一入口出现失败或分页失效，调用方不应据此删除库中数据。
     */
    public ScrapeOutcome fetchAllModels() {
        // 按 name 去重且保序：先官方、后 abliterated
        Map<String, OllamaModelDto> merged = new LinkedHashMap<>();
        boolean officialComplete = crawlInto(PATH_OFFICIAL, false, merged, PAGE_SIZE_GUARD);
        sleepQuietly();
        boolean abliteratedComplete = crawlInto(PATH_ABLITERATED, true, merged, PAGE_SIZE_GUARD);

        List<OllamaModelDto> all = new ArrayList<>(merged.values());
        // 任一入口抓取失败都算本次不完整 → 调用方据此跳过删除阶段，
        // 否则上次同步进来的社区模型会被整批误删
        boolean complete = officialComplete && abliteratedComplete;

        log.info("Ollama 模型列表抓取完成：{} 个模型（official complete={}，abliterated complete={}），complete={}",
                all.size(), officialComplete, abliteratedComplete, complete);
        return new ScrapeOutcome(all, complete);
    }

    /**
     * 按关键词抓取官网搜索结果，供「按需补全」使用。
     *
     * 背景：官网默认列表页只返回官方库，社区模型无法枚举（无 sitemap、无"列出全部"入口），
     * 所以「官网有、本地没有」的模型只能靠指定关键词抓回来。
     *
     * 只抓这一个查询入口，不与官方库 / abliterated 合并；是否删除由调用方决定
     * （补全场景必须「只增不删」）。空格编码成 `+`，与官网地址栏形态一致
     * （实测 `?q=Bonsai+2+27B` 可用）。
     */
    public ScrapeOutcome fetchByKeyword(String keyword, int maxPages) {
        String path = "/search?q=" + URLEncoder.encode(keyword.trim(), StandardCharsets.UTF_8);
        Map<String, OllamaModelDto> merged = new LinkedHashMap<>();
        // forceAbliterated=false：通用关键词不该把结果一律标成 abliterated，只按名字判定
        boolean complete = crawlInto(path, false, merged, maxPages);
        log.info("Ollama 关键词抓取：q={}，{} 个模型，complete={}", keyword, merged.size(), complete);
        return new ScrapeOutcome(new ArrayList<>(merged.values()), complete);
    }

    /**
     * 按分页哨兵抓完一条采集入口，把结果并入 out。
     * 同名模型若已由另一入口收录，则 is_abliterated 取「或」。
     *
     * @param maxPages 本入口最多翻几页（全量抓取传 {@link #PAGE_SIZE_GUARD}，按需补全传
     *                 {@link #ADHOC_PAGE_LIMIT}）
     * @return 该入口是否完整抓完；false = 中途请求失败、分页未生效或触到页数上限
     */
    private boolean crawlInto(String initialPath, boolean forceAbliterated,
                              Map<String, OllamaModelDto> out, int maxPages) {
        Set<String> seenHere = new HashSet<>();
        String path = initialPath;
        for (int page = 1; page <= maxPages; page++) {
            Document doc = fetchSearchDocument(BASE + path);
            if (doc == null) {
                // 抓取失败：停止翻页，并标记该入口不完整
                return false;
            }
            int fresh = 0;
            for (OllamaModelDto m : parseModelList(doc, forceAbliterated)) {
                if (!seenHere.add(m.getName())) {
                    continue;  // 本入口内的重复行
                }
                fresh++;
                OllamaModelDto exist = out.get(m.getName());
                if (exist == null) {
                    out.put(m.getName(), m);
                } else if (isAbliterated(m) && !isAbliterated(exist)) {
                    exist.setIsAbliterated(1);  // 另一入口已收录，标记取「或」
                }
            }
            String next = nextPagePath(doc);
            if (fresh == 0) {
                // 没有新模型：有下一页说明分页未生效（拿到的是重复页）→ 该入口不完整
                return next == null;
            }
            if (next == null) {
                // 无哨兵即最后一页
                return true;
            }
            path = next;
            sleepQuietly();
        }
        return false;  // 触到页数上限
    }

    private boolean isAbliterated(OllamaModelDto dto) {
        return dto.getIsAbliterated() != null && dto.getIsAbliterated() == 1;
    }

    /**
     * 抓取指定模型详情页的全部变体标签。
     * 返回空列表表示抓取失败或无变体，调用方应保留旧数据不做删除。
     */
    public List<OllamaTagDto> fetchTags(String detailUrl) {
        List<OllamaTagDto> tags = new ArrayList<>();
        if (detailUrl == null || detailUrl.isBlank()) {
            return tags;
        }
        String url = detailUrl.endsWith("/") ? detailUrl + "tags" : detailUrl + "/tags";
        Document doc = fetchDocument(url);
        if (doc == null) {
            return tags;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Element row : doc.select("div.group.px-4.py-3")) {
            // 桌面结构才有 input.command；移动端结构没有，天然避免重复
            Element cmd = row.selectFirst("input.command");
            if (cmd == null) {
                continue;
            }
            String fullName = cmd.attr("value").trim();
            if (fullName.isEmpty() || !seen.add(fullName)) {
                continue;
            }
            // 列顺序：大小 / 上下文 / 输入类型（详情页第三列是 div，列表页是 p）
            Elements cols = row.select("p.col-span-2, div.col-span-2");
            String sizeText = cols.size() > 0 ? cols.get(0).text().trim() : null;
            String contextText = cols.size() > 1 ? cols.get(1).text().trim() : null;
            String inputs = cols.size() > 2 ? cols.get(2).text().trim() : null;

            int isLatest = 0;
            int isMlx = 0;
            for (Element badge : row.select("span.rounded-full")) {
                String t = badge.text().trim();
                if ("latest".equalsIgnoreCase(t)) {
                    isLatest = 1;
                } else if ("MLX".equalsIgnoreCase(t)) {
                    isMlx = 1;
                }
            }

            Element digestEl = row.selectFirst("span.font-mono");
            String digest = digestEl == null ? null : digestEl.text().trim();
            String updatedText = null;
            if (digestEl != null && digestEl.parent() != null) {
                String metaText = digestEl.parent().text();
                int dot = metaText.indexOf('·');
                if (dot >= 0) {
                    updatedText = metaText.substring(dot + 1).trim();
                }
            }

            String shortName = shortTagName(fullName);
            tags.add(OllamaTagDto.builder()
                    .name(fullName)
                    .shortName(shortName)
                    .digest(digest == null || digest.isEmpty() ? null : digest)
                    .sizeText(blankToNull(sizeText))
                    .sizeGb(parseSizeGb(sizeText))
                    .contextText(blankToNull(contextText))
                    .contextTokens(parseContextTokens(contextText))
                    .inputs(blankToNull(inputs))
                    .isLatest(isLatest)
                    .isMlx(isMlx)
                    .isAbliterated(containsAbliterated(fullName) ? 1 : 0)
                    .paramSize(parseParamSize(shortName))
                    .command("ollama run " + fullName)
                    .sourceUpdatedText(blankToNull(updatedText))
                    .build());
        }
        return tags;
    }

    // ===== 列表页解析 =====

    private List<OllamaModelDto> parseModelList(Document doc, boolean forceAbliterated) {
        List<OllamaModelDto> models = new ArrayList<>();
        // 列表页与 htmx 片段结构不同：片段是裸 <li> 列表（无 ul[role=list] 包裹），
        // 所以按 li 全量扫描，再用「有 a[href] 且有 h2」筛出真正的结果行（分页哨兵两者皆无）。
        for (Element li : doc.select("li")) {
            Element link = li.selectFirst("a[href]");
            if (link == null || li.selectFirst("h2") == null) {
                continue;
            }
            String href = link.attr("href");
            String name = normalizeName(href);
            if (name == null || name.isBlank()) {
                continue;
            }

            Element descEl = li.selectFirst("div.flex.flex-col.mb-1 > p");
            if (descEl == null) {
                descEl = li.selectFirst("p.max-w-lg");
            }
            String description = descEl == null ? null : truncate(descEl.text(), 2000);

            List<String> capabilities = new ArrayList<>();
            List<String> sizes = new ArrayList<>();
            for (Element badge : li.select("div.flex.flex-wrap.space-x-2 span")) {
                String t = badge.text().trim();
                if (t.isEmpty()) {
                    continue;
                }
                if (SIZE_TAG.matcher(t).matches()) {
                    String size = t.toLowerCase(Locale.ROOT);
                    if (!sizes.contains(size)) {
                        sizes.add(size);
                    }
                } else {
                    String cap = t.toLowerCase(Locale.ROOT);
                    if (!capabilities.contains(cap)) {
                        capabilities.add(cap);
                    }
                }
            }

            // 云端模型排除：带 cloud 能力且没有任何本地参数量
            if (capabilities.contains(CLOUD) && sizes.isEmpty()) {
                continue;
            }

            String pullsText = null;
            Long pulls = 0L;
            Integer tagCount = 0;
            LocalDateTime sourceUpdatedAt = null;
            for (Element stat : li.select("p.my-1 > span.flex.items-center")) {
                String title = stat.attr("title");
                if (!title.isBlank()) {
                    sourceUpdatedAt = parseUsDate(title);
                    continue;
                }
                String whole = stat.text();
                String value = statValue(stat);
                if (whole.contains("Pull") || whole.contains("Download")) {
                    pullsText = value;
                    pulls = parsePulls(value);
                } else if (whole.contains("Tag")) {
                    tagCount = parseInt(value);
                }
            }

            String namespace = null;
            String baseName = name;
            int slash = name.indexOf('/');
            if (slash > 0 && slash < name.length() - 1) {
                namespace = name.substring(0, slash);
                baseName = name.substring(slash + 1);
            }
            String[] vendor = classifyVendor(baseName, namespace);

            models.add(OllamaModelDto.builder()
                    .name(name)
                    .namespace(namespace)
                    .baseName(baseName)
                    .url(BASE + href)
                    .description(description)
                    .vendor(vendor[0])
                    .vendorLabel(vendor[1])
                    .pulls(pulls)
                    .pullsText(pullsText)
                    .tagCount(tagCount)
                    .capabilities(capabilities)
                    .sizes(sizes)
                    .isAbliterated((forceAbliterated || containsAbliterated(name)) ? 1 : 0)
                    .sourceUpdatedAt(sourceUpdatedAt)
                    .build());
        }
        return models;
    }

    /** 统计项数值：跳过 svg 与 .hidden 标签，取第一个有文本的直接子元素 */
    private String statValue(Element stat) {
        for (Element child : stat.children()) {
            if (child.hasClass("hidden") || "svg".equals(child.tagName())) {
                continue;
            }
            String t = child.text().trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return stat.text().trim();
    }

    /**
     * 从列表项 href 归一化出 ollama 模型名。
     * /library/qwen3.6 → qwen3.6 ；/library/richardyoung/qwen3-8b-abliterated → richardyoung/qwen3-8b-abliterated
     */
    private String normalizeName(String href) {
        if (href == null) {
            return null;
        }
        String path = href.trim();
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        int hash = path.indexOf('#');
        if (hash >= 0) {
            path = path.substring(0, hash);
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (path.startsWith("library/")) {
            path = path.substring("library/".length());
        }
        if (path.isBlank()) {
            return null;
        }
        String[] segments = path.split("/");
        // 只接受 name 或 namespace/name
        if (segments.length < 1 || segments.length > 2) {
            return null;
        }
        for (String s : segments) {
            if (s.isBlank()) {
                return null;
            }
        }
        return path;
    }

    // ===== 厂商判定 =====

    /**
     * 判定厂商：关键词命中优先；未命中时社区模型归 community，其余归 other。
     * 返回 [vendorKey, vendorLabel]
     */
    public String[] classifyVendor(String baseName, String namespace) {
        if (baseName != null && !baseName.isBlank()) {
            String lower = baseName.toLowerCase(Locale.ROOT);
            for (VendorRule rule : VENDOR_RULES) {
                if (rule.pattern().matcher(lower).find()) {
                    return new String[]{rule.key(), rule.label()};
                }
            }
        }
        if (namespace != null && !namespace.isBlank()) {
            return new String[]{"community", "社区模型"};
        }
        return new String[]{"other", "其他"};
    }

    private static List<VendorRule> buildVendorRules() {
        return List.of(
                rule("qwen", "阿里 Qwen", "qwen", "qwq", "qvq"),
                rule("deepseek", "深度求索 DeepSeek", "deepseek"),
                rule("zhipu", "智谱 GLM", "chatglm", "codegeex", "cogvlm", "glm"),
                rule("mistral", "Mistral AI", "mistral", "mixtral", "magistral", "devstral", "codestral", "pixtral"),
                rule("microsoft", "微软 Microsoft", "phi", "wizard"),
                rule("google", "Google", "paligemma", "medgemma", "gemma", "gemini"),
                rule("meta", "Meta", "meta-llama", "llama", "muse"),
                rule("nvidia", "NVIDIA", "nemotron", "nvidia"),
                rule("bytedance", "字节跳动", "bytedance", "doubao", "seed"),
                rule("moonshot", "月之暗面 Kimi", "moonshot", "kimi"),
                rule("minimax", "MiniMax", "minimax"),
                rule("ibm", "IBM", "granite"),
                rule("internlm", "上海 AI Lab", "internlm"),
                rule("inclusionai", "蚂蚁 InclusionAI", "inclusionai", "ling", "ring"),
                rule("stepfun", "阶跃星辰", "stepfun", "step"),
                rule("baichuan", "百川智能", "baichuan"),
                rule("tencent", "腾讯", "hunyuan"),
                rule("huawei", "华为", "pangu"),
                rule("baidu", "百度", "ernie"),
                rule("rednote", "小红书", "dots"),
                rule("01ai", "零一万物", "yi"),
                rule("cohere", "Cohere", "command", "c4ai", "aya"),
                rule("nousresearch", "Nous Research", "hermes"),
                rule("huggingface", "Hugging Face", "zephyr", "smollm", "smol"),
                rule("bigcode", "BigCode", "starcoder"),
                rule("tii", "TII", "falcon"),
                rule("allenai", "Ai2", "olmo"),
                rule("upstage", "Upstage", "solar"));
    }

    /** 词边界 + 数字前瞻：保证 qwen 命中 qwen3.6，且不误匹配 incident 之类 */
    private static VendorRule rule(String key, String label, String... keywords) {
        String alternatives = Arrays.stream(keywords).map(Pattern::quote).collect(Collectors.joining("|"));
        Pattern pattern = Pattern.compile("\\b(?:" + alternatives + ")(?=$|[^a-z0-9]|[0-9])");
        return new VendorRule(key, label, pattern);
    }

    private record VendorRule(String key, String label, Pattern pattern) {
    }

    // ===== 数值 / 日期解析 =====

    /** 6.7M → 6700000 */
    public Long parsePulls(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0L;
        }
        String s = raw.trim().replace(",", "").toUpperCase(Locale.ROOT);
        try {
            if (s.endsWith("B")) {
                return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000_000_000L);
            }
            if (s.endsWith("M")) {
                return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000_000L);
            }
            if (s.endsWith("K")) {
                return (long) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1_000L);
            }
            return (long) Double.parseDouble(s);
        } catch (Exception e) {
            return 0L;
        }
    }

    /** 23GB → 23.0 */
    public Double parseSizeGb(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        try {
            if (s.endsWith("GB")) {
                return Double.parseDouble(s.substring(0, s.length() - 2));
            }
            if (s.endsWith("MB")) {
                return Double.parseDouble(s.substring(0, s.length() - 2)) / 1024d;
            }
            if (s.endsWith("KB")) {
                return Double.parseDouble(s.substring(0, s.length() - 2)) / (1024d * 1024d);
            }
            if (s.endsWith("B")) {
                return Double.parseDouble(s.substring(0, s.length() - 1)) / (1024d * 1024d * 1024d);
            }
            return Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 256K → 262144 */
    public Integer parseContextTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT).replace(" ", "").replace(",", "");
        try {
            if (s.endsWith("K")) {
                return (int) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1024);
            }
            if (s.endsWith("M")) {
                return (int) (Double.parseDouble(s.substring(0, s.length() - 1)) * 1024 * 1024);
            }
            return (int) Double.parseDouble(s);
        } catch (Exception e) {
            return null;
        }
    }

    /** 27b-coding-mtp-q4_K_M → 27b */
    public String parseParamSize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = PARAM_SIZE.matcher(text);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null;
    }

    /** Sep 1, 2026 5:04 PM UTC → 服务器本地时间 */
    public LocalDateTime parseUsDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.endsWith("UTC")) {
            s = s.substring(0, s.length() - 3).trim();
        }
        try {
            LocalDateTime utc = LocalDateTime.parse(s, US_DATE);
            return utc.atZone(ZoneId.of("UTC")).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    // ===== 通用工具 =====

    /** qwen3.6:27b-coding → 27b-coding；无冒号按 latest */
    private String shortTagName(String fullName) {
        int colon = fullName.indexOf(':');
        if (colon < 0 || colon == fullName.length() - 1) {
            return "latest";
        }
        return fullName.substring(colon + 1);
    }

    private boolean containsAbliterated(String text) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(ABLITERATED);
    }

    /**
     * 取分页哨兵的下一页地址。
     * 哨兵形如 &lt;li hx-get="/search?page=2" hx-trigger="revealed"&gt;；末页没有哨兵。
     * 只认带 page 参数的 /search 链接，避免误取表单/筛选框的 hx-get="/search"（那会造成死循环）。
     */
    private String nextPagePath(Document doc) {
        for (Element el : doc.select("[hx-get]")) {
            String url = el.attr("hx-get").trim();
            if (url.startsWith("/search") && url.contains("page=")) {
                return url;
            }
        }
        return null;
    }

    /** 列表接口必须带 HX-Request 头，否则服务端会忽略 page 参数退回第一页 */
    private Document fetchSearchDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .header("HX-Request", "true")
                    .timeout(REQUEST_TIMEOUT_MS)
                    .maxBodySize(0)
                    .get();
        } catch (Exception e) {
            log.warn("Ollama 列表抓取失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private Document fetchDocument(String url) {
        try {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .maxBodySize(0)
                    .get();
        } catch (Exception e) {
            log.warn("Ollama 页面抓取失败 {}: {}", url, e.getMessage());
            return null;
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(THROTTLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }
}
