package com.evernox.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.evernox.dto.AiModelRankItem;
import com.evernox.entity.AiNewsItem;
import com.evernox.entity.AiZhizhiRank;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AI 编程资讯采集器：Hacker News（英文，需翻译） + AIHOT（中文，免 token）。
 *
 * 只负责「拉取 + 解析 + 关键词过滤 + 标签分类」，入库去重交给 Service。
 */
@Slf4j
@Component
public class AiNewsCrawler {

    private static final String USER_AGENT = "evernox-ai-news/1.0";

    private static final String AIHOT_ITEMS_URL = "https://aihot.virxact.com/api/v1/items?limit=100";
    private static final String AIHOT_HOT_TOPICS_URL = "https://aihot.virxact.com/api/v1/hot-topics";
    private static final String AIHOT_LEADERBOARD_URL = "https://aihot.news/leaderboard";
    private static final String ZHIZHI_BASE = "https://llm2014.github.io/llm_benchmark/";
    private static final String ZHIZHI_DATASETS_URL = ZHIZHI_BASE + "data/datasets.json";

    /** AI 编程相关关键词白名单：标题命中任一即保留（只放 AI 专属词，通用编程词不算） */
    private static final List<String> RELEVANT_TERMS = List.of(
            "ai", "llm", "gpt", "claude", "gemini", "qwen", "deepseek", "mistral", "llama",
            "anthropic", "openai", "copilot", "cursor", "agent", "model", "openclaw",
            "模型", "大模型", "人工智能", "智能体", "豆包");

    /** 标签 → 关键词映射（LinkedHashMap 保证确定性顺序，先命中先生效） */
    private static final Map<String, List<String>> TAG_KEYWORDS = buildTagKeywords();

    private static Map<String, List<String>> buildTagKeywords() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("agent", List.of("agent", "coding agent", "智能体", "代理"));
        m.put("model", List.of("model", "llm", "gpt", "claude", "gemini", "qwen", "deepseek", "mistral", "llama", "模型", "大模型", "推理", "release"));
        m.put("ide", List.of("ide", "plugin", "cursor", "copilot", "vscode", "jetbrains", "插件", "编辑器"));
        m.put("tool", List.of("tool", "cli", "terminal", "终端", "命令行", "toolchain", "工具"));
        m.put("desktop", List.of("desktop", "桌面", "app", "客户端", "豆包", "openclaw"));
        m.put("benchmark", List.of("benchmark", "sota", "eval", "评测", "跑分", "榜单", "能力"));
        m.put("cost", List.of("price", "cost", "cheap", "free", "pricing", "性价比", "价格", "免费", "开源", "open source"));
        return m;
    }

    /** AIHOT 分类 → 本站标签 */
    private static final Map<String, String> AIHOT_CATEGORY_TAG = Map.of(
            "ai-models", "model",
            "ai-products", "tool",
            "paper", "benchmark",
            "tip", "tool",
            "industry", "other",
            "opinion", "other");

    public List<AiNewsItem> crawlAll() {
        List<AiNewsItem> items = new ArrayList<>();
        items.addAll(fetchHackerNews());
        items.addAll(fetchAiHot());
        items.addAll(fetchAiHotTopics());
        return items;
    }

    public boolean isRelevant(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String term : RELEVANT_TERMS) {
            if (matches(lower, term)) {
                return true;
            }
        }
        return false;
    }

    public String classifyTag(String text) {
        if (text == null || text.isBlank()) {
            return "other";
        }
        String lower = text.toLowerCase();
        for (Map.Entry<String, List<String>> e : TAG_KEYWORDS.entrySet()) {
            for (String kw : e.getValue()) {
                if (matches(lower, kw)) {
                    return e.getKey();
                }
            }
        }
        return "other";
    }

    /** 英文关键词按整词匹配（避免 ide 误匹配 incident、app 误匹配 apple）；中文按子串匹配 */
    private boolean matches(String lowerText, String kw) {
        if (isAscii(kw)) {
            return Pattern.compile("\\b" + Pattern.quote(kw) + "\\b").matcher(lowerText).find();
        }
        return lowerText.contains(kw);
    }

    private boolean isAscii(String kw) {
        for (int i = 0; i < kw.length(); i++) {
            char c = kw.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == ' ';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    // ===== Hacker News（Algolia API，英文） =====
    private List<AiNewsItem> fetchHackerNews() {
        List<AiNewsItem> items = new ArrayList<>();
        String[] queries = {"AI coding agent", "AI agent", "coding assistant", "LLM", "Claude", "GPT", "Cursor", "terminal AI"};
        for (String q : queries) {
            try {
                String url = "https://hn.algolia.com/api/v1/search_by_date?query="
                        + URLEncoder.encode(q, StandardCharsets.UTF_8)
                        + "&tags=story&hitsPerPage=50";
                String body = HttpRequest.get(url).timeout(8000).execute().body();
                JSONObject json = JSONUtil.parseObj(body);
                JSONArray hits = json.getJSONArray("hits");
                if (hits == null) {
                    continue;
                }
                for (int i = 0; i < hits.size(); i++) {
                    JSONObject h = hits.getJSONObject(i);
                    String title = h.getStr("title");
                    String objectId = h.getStr("objectID");
                    if (title == null || objectId == null || !isRelevant(title)) {
                        continue;
                    }
                    String link = h.getStr("url");
                    if (link == null || link.isBlank()) {
                        link = "https://news.ycombinator.com/item?id=" + objectId;
                    }
                    Long created = h.getLong("created_at_i");
                    Integer points = h.getInt("points");
                    items.add(AiNewsItem.builder()
                            .source("hackernews")
                            .externalId(objectId)
                            .title(title)
                            .url(link)
                            .tag(classifyTag(title))
                            .score(points == null ? 0 : points)
                            .publishedAt(toLocalDateTime(created == null ? 0L : created))
                            .build());
                }
            } catch (Exception e) {
                log.warn("HackerNews 采集失败: {}", e.getMessage());
            }
        }
        return items;
    }

    // ===== AIHOT（中文，免 token） =====
    private List<AiNewsItem> fetchAiHot() {
        List<AiNewsItem> items = new ArrayList<>();
        try {
            String body = HttpRequest.get(AIHOT_ITEMS_URL)
                    .header("User-Agent", USER_AGENT)
                    .timeout(8000)
                    .execute()
                    .body();
            JSONObject json = JSONUtil.parseObj(body);
            JSONArray arr = json.getJSONArray("items");
            if (arr == null) {
                return items;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject it = arr.getJSONObject(i);
                String id = it.getStr("id");
                String titleZh = it.getStr("title");
                String originalTitle = it.getStr("originalTitle");
                String summary = it.getStr("summary");
                if (id == null || titleZh == null || titleZh.isBlank()) {
                    continue;
                }
                // 相关性用中文标题 + 摘要判断
                String relevanceText = titleZh + " " + (summary == null ? "" : summary);
                if (!isRelevant(relevanceText)) {
                    continue;
                }
                // 跳过行业动态/观点（IPO、融资、评论等，非 AI 编程相关）
                String category = it.getStr("category");
                if ("industry".equals(category) || "opinion".equals(category)) {
                    continue;
                }
                JSONObject links = it.getJSONObject("links");
                String link = links == null ? null : links.getStr("aihot");
                if (link == null || link.isBlank()) {
                    link = links == null ? null : links.getStr("original");
                }
                Integer score = it.getInt("score");
                String publishedAt = it.getStr("publishedAt");
                String title = (originalTitle != null && !originalTitle.isBlank()) ? originalTitle : titleZh;
                items.add(AiNewsItem.builder()
                        .source("aihot")
                        .externalId(id)
                        .title(title)
                        .titleZh(titleZh)
                        .url(link == null ? "https://aihot.news" : link)
                        .summaryZh(truncate(summary, 500))
                        .tag(mapAiHotCategory(category))
                        .score(score == null ? 0 : score)
                        .publishedAt(parseIso(publishedAt))
                        .build());
            }
        } catch (Exception e) {
            log.warn("AIHOT 采集失败: {}", e.getMessage());
        }
        return items;
    }

    private String mapAiHotCategory(String category) {
        if (category == null) {
            return "other";
        }
        return AIHOT_CATEGORY_TAG.getOrDefault(category, "other");
    }

    // ===== AIHOT 热点榜（当前最热事件，免 token） =====
    private List<AiNewsItem> fetchAiHotTopics() {
        List<AiNewsItem> items = new ArrayList<>();
        try {
            String body = HttpRequest.get(AIHOT_HOT_TOPICS_URL)
                    .header("User-Agent", USER_AGENT)
                    .timeout(8000)
                    .execute()
                    .body();
            JSONObject json = JSONUtil.parseObj(body);
            JSONArray arr = json.getJSONArray("items");
            if (arr == null) {
                return items;
            }
            for (int i = 0; i < arr.size(); i++) {
                JSONObject it = arr.getJSONObject(i);
                String id = it.getStr("id");
                String title = it.getStr("title");
                if (id == null || title == null || title.isBlank()) {
                    continue;
                }
                JSONObject links = it.getJSONObject("links");
                String link = links == null ? null : links.getStr("aihot");
                if (link == null || link.isBlank()) {
                    link = links == null ? null : links.getStr("original");
                }
                Integer rank = it.getInt("rank");
                Integer signalCount = it.getInt("signalCount");
                // 热度评分：优先用信号数，并保证热点榜条目靠前（rank 越小越热）
                int score = Math.max(signalCount == null ? 0 : signalCount, rank == null ? 0 : 100 - rank);
                items.add(AiNewsItem.builder()
                        .source("aihot")
                        .externalId(id)
                        .title(title)
                        .titleZh(title)
                        .url(link == null ? "https://aihot.news" : link)
                        .tag(classifyTag(title))
                        .score(score)
                        .publishedAt(parseIso(it.getStr("latestAt")))
                        .build());
            }
        } catch (Exception e) {
            log.warn("AIHOT 热点榜采集失败: {}", e.getMessage());
        }
        return items;
    }

    private LocalDateTime toLocalDateTime(long epochSeconds) {
        if (epochSeconds <= 0) {
            return LocalDateTime.now();
        }
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private LocalDateTime parseIso(String iso) {
        if (iso == null || iso.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        String t = text.replaceAll("\\s+", " ").trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    // ===== AIHOT 模型排行榜（网页抓取，无公开 API） =====
    public List<AiModelRankItem> scrapeLeaderboard(String category) {
        List<AiModelRankItem> items = new ArrayList<>();
        String url = AIHOT_LEADERBOARD_URL;
        if (category != null && !category.isBlank() && !"overall".equals(category) && !"all".equals(category)) {
            url = AIHOT_LEADERBOARD_URL + "/category/" + category;
        }
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(12000)
                    .get();
            Elements rows = doc.select("table.lb-ranking-table tbody tr");
            for (Element row : rows) {
                String modelName = text(row, ".lb-name-cell strong");
                if (modelName.isBlank()) {
                    continue;
                }
                Elements prices = row.select(".lb-price-cell span");
                items.add(AiModelRankItem.builder()
                        .rank(parseInt(text(row, ".lb-rank-number span")))
                        .modelName(modelName)
                        .provider(text(row, ".lb-name-cell small"))
                        .releaseDate(text(row, ".lb-release-cell time"))
                        .evidence(text(row, ".lb-evidence-cell span"))
                        .confidence(text(row, ".lb-evidence-cell small"))
                        .inputPrice(prices.size() > 0 ? prices.get(0).text().trim() : "")
                        .outputPrice(prices.size() > 1 ? prices.get(1).text().trim() : "")
                        .score(parseDouble(text(row, ".lb-score-cell strong")))
                        .build());
            }
        } catch (Exception e) {
            log.warn("AIHOT 排行榜抓取失败 category={}: {}", category, e.getMessage());
        }
        return items;
    }

    private String text(Element row, String selector) {
        Element el = row.selectFirst(selector);
        return el == null ? "" : el.text().trim();
    }

    private Integer parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private Double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    // ===== 「致知」模型排行榜（GitHub Pages 静态站，JSON 索引 + CSV 数据） =====

    /** 模型国家判定正则（1:1 移植自官网 benchmark-domain.js） */
    private static final Pattern CHINESE_CHAR = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern K2_PREFIX = Pattern.compile("^k2(?:\\b|[.\\s-])", Pattern.CASE_INSENSITIVE);
    private static final Pattern CHINA_PATTERN = Pattern.compile(
            "\\b(?:baichuan|chatglm|deepseek|doubao|ernie|erine|glm|hunyuan|kat|kimi|ling|longcat|minimax|mimo|openpangu|pangu|qwen|qwn|qvq|qwq|ring|seed|sensechat|sensenova|spark|step|tencent|tiangong|yi|hy|dots)(?=$|[^a-z0-9]|[0-9])",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern USA_PATTERN = Pattern.compile(
            "\\b(?:anthropic|chatgpt|claude|fable|gemini|gemm3|gemma|gpt|grok|haiku|llama|muse|o1|o3|o4|openai|opus|sonnet)(?=$|[^a-z0-9]|[0-9])",
            Pattern.CASE_INSENSITIVE);

    /** 按模型名判定国家：先美国、再中国、其余 other（与官网一致） */
    public String classifyCountry(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "other";
        }
        String name = modelName.trim();
        if (USA_PATTERN.matcher(name).find()) {
            return "usa";
        }
        if (CHINESE_CHAR.matcher(name).find() || K2_PREFIX.matcher(name).find() || CHINA_PATTERN.matcher(name).find()) {
            return "china";
        }
        return "other";
    }

    /** 拉取并解析 datasets.json，返回 datasets 数组（失败返回 null） */
    private JSONArray fetchDatasets() {
        String dsBody = HttpRequest.get(ZHIZHI_DATASETS_URL)
                .header("User-Agent", USER_AGENT)
                .timeout(10000)
                .execute()
                .body();
        JSONObject dsJson = JSONUtil.parseObj(dsBody);
        return dsJson.getJSONArray("datasets");
    }

    /** 返回该分类所有「月榜」月份（reportDate），降序 */
    public List<String> fetchZhizhiMonths(String category) {
        List<String> months = new ArrayList<>();
        try {
            JSONArray datasets = fetchDatasets();
            if (datasets == null) {
                return months;
            }
            for (int i = 0; i < datasets.size(); i++) {
                JSONObject d = datasets.getJSONObject(i);
                if (!category.equals(d.getStr("category"))) {
                    continue;
                }
                if (d.getInt("tableIndex", 0) != 0) {
                    continue;
                }
                String date = d.getStr("reportDate");
                if (date != null && !date.isBlank() && !months.contains(date)) {
                    months.add(date);
                }
            }
            months.sort(Comparator.reverseOrder());
        } catch (Exception e) {
            log.warn("致知月份清单获取失败 category={}: {}", category, e.getMessage());
        }
        return months;
    }

    public List<AiZhizhiRank> fetchZhizhiRank(String category, String month) {
        List<AiZhizhiRank> items = new ArrayList<>();
        try {
            JSONArray datasets = fetchDatasets();
            if (datasets == null) {
                return items;
            }
            // 找该分类「月榜」中匹配 month（或最新）的 csv
            String csvPath = null;
            String latestDate = "";
            String reportDate = month;
            for (int i = 0; i < datasets.size(); i++) {
                JSONObject d = datasets.getJSONObject(i);
                if (!category.equals(d.getStr("category"))) {
                    continue;
                }
                if (d.getInt("tableIndex", 0) != 0) {
                    continue;
                }
                String date = d.getStr("reportDate");
                if (month != null && !month.isBlank()) {
                    if (month.equals(date)) {
                        csvPath = d.getStr("csv");
                        reportDate = date;
                        break;
                    }
                } else if (date != null && date.compareTo(latestDate) > 0) {
                    latestDate = date;
                    csvPath = d.getStr("csv");
                    reportDate = date;
                }
            }
            if (csvPath == null) {
                return items;
            }
            // 拉取并解析 csv
            String csvBody = HttpRequest.get(ZHIZHI_BASE + csvPath)
                    .header("User-Agent", USER_AGENT)
                    .timeout(10000)
                    .execute()
                    .body();
            String[] lines = csvBody.split("\\r?\\n");
            int rank = 0;
            for (int i = 1; i < lines.length; i++) {
                List<String> c = parseCsvLine(lines[i]);
                if (c.size() < 10) {
                    continue;
                }
                rank++;
                String modelName = c.get(0);
                items.add(AiZhizhiRank.builder()
                        .category(category)
                        .reportDate(reportDate)
                        .rank(rank)
                        .modelName(modelName)
                        .extremeScore(parseDouble(c.get(1)))
                        .medianScore(parseDouble(c.get(2)))
                        .medianGap(c.get(3))
                        .change(c.get(4))
                        .avgTime(c.get(5))
                        .token(c.get(6))
                        .testCost(c.get(7))
                        .price(c.get(8))
                        .releaseDate(c.get(9))
                        .think(c.size() > 10 && "1".equals(c.get(10)) ? 1 : 0)
                        .country(classifyCountry(modelName))
                        .build());
            }
        } catch (Exception e) {
            log.warn("致知排行榜抓取失败 category={} month={}: {}", category, month, e.getMessage());
        }
        return items;
    }

    /** 解析一行带引号的 CSV */
    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
            } else if (ch == ',' && !inQuotes) {
                fields.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        fields.add(sb.toString().trim());
        return fields;
    }
}
