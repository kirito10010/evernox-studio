package com.evernox.service;

import com.evernox.dto.ArenaRankDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Code Arena 采集器（arena.ai / Code Arena | WebDev）。
 *
 * 只负责「拉取 + 解析」，入库与增删比对交给 Service。
 *
 * 【2026-10 起官网行为】榜单页由 Cloudflare 保护：普通网页请求（文档导航）会收到
 * 托管挑战并返回 HTTP 403（Cf-Mitigated: challenge）。带 Next.js RSC 头的请求
 * 不会触发挑战，返回 text/x-component 格式的 RSC 数据，其中 "entries" 数组
 * 已包含整张榜单（rating/置信区间/votes/价格/上下文）。
 *
 * 因此这里抓 RSC 数据；同时兼容旧版「整页 HTML」数据包（历史 zip）。
 * overall 是默认分类，地址为 /leaderboard/code/webdev；其余分类为 .../{slug}。
 * 项目约定不使用站点自带翻译，固定英文请求头，保留英文原文。
 */
@Slf4j
@Component
public class AiArenaCrawler {

    /** 默认分类（overall）的榜单地址；其它分类为 BASE + "/" + slug */
    private static final String BASE = "https://arena.ai/leaderboard/code/webdev";

    /**
     * 榜单页由 Cloudflare 保护：普通网页请求（文档导航）会收到托管挑战并返回 403，
     * 带 Next.js RSC 头的请求不会触发挑战，返回的就是包含 entries 数组的榜单数据。
     */
    private static final String RSC_ACCEPT = "text/x-component";
    private static final String RSC_HEADER = "RSC";
    private static final String RSC_HEADER_VALUE = "1";

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * 用真实浏览器 UA，保留英文原文。
     * RSC 请求同样需要浏览器特征，否则仍可能被 Cloudflare 判定为脚本。
     */
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/127.0.0.0 Safari/537.36";

    /** 固定英文，避免站点侧本地化 */
    private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";

    private static final int REQUEST_TIMEOUT_MS = 30000;

    /** 分类之间的请求间隔，避免给官网压力 */
    private static final long THROTTLE_MS = 400L;

    /** 表格列数（Rank/Rank Spread/Model/Score/Votes/Price/Context），不足则视为表头或异常行 */
    private static final int COLUMN_COUNT = 7;

    /** 分数形如 "1758 +14/-14" */
    private static final Pattern SCORE = Pattern.compile("(\\d+)\\s*\\+(\\d+)\\s*/\\s*-?(\\d+)");

    /** 价格形如 "$10 / $50" */
    private static final Pattern PRICE = Pattern.compile("\\$([\\d.]+)\\s*/\\s*\\$([\\d.]+)");

    /** 上下文形如 "262.1K" / "1M" / "1.1M" */
    private static final Pattern CONTEXT = Pattern.compile("([\\d.]+)\\s*([KMkm])?");

    private static final Pattern INTEGER = Pattern.compile("\\d+");

    private static final String PRELIMINARY = "Preliminary";

    /** 分类：slug(URL 路径段) / 中文标签 / 站点英文标签 / 分组 */
    public record Category(String slug, String labelZh, String labelEn, String group) {
    }

    /**
     * 站点侧边栏「Category」「Domain」两组共 12 个叶子分类（实测 URL slug 均可用）。
     * 注：technology 只是折叠分组头，不是叶子分类，不采集。
     */
    public static final List<Category> CATEGORIES = List.of(
            new Category("overall", "综合", "Overall", "category"),
            new Category("fullstack", "全栈", "Fullstack", "category"),
            new Category("frontend", "前端", "Frontend", "category"),
            new Category("html", "HTML", "HTML", "category"),
            new Category("react", "React", "React", "category"),
            new Category("brand-marketing", "品牌与营销", "Brand & Marketing", "domain"),
            new Category("reference-based-design", "参考设计还原", "Reference-Based Design", "domain"),
            new Category("data-analytics", "数据与分析", "Data & Analytics", "domain"),
            new Category("consumer-product", "消费产品", "Consumer Product", "domain"),
            new Category("gaming", "游戏", "Gaming", "domain"),
            new Category("simulations", "模拟仿真", "Simulations", "domain"),
            new Category("content-creation-tools", "内容创作工具", "Content Creation Tools", "domain")
    );

    /** 单次抓取失败（网络不可达 / 被官网拒绝 / 页面结构变化）；reason 会展示到管理页面上 */
    public static class ArenaFetchException extends RuntimeException {
        public ArenaFetchException(String message) {
            super(message);
        }
    }

    /**
     * 抓取单个分类的完整榜单。
     * 失败时抛 {@link ArenaFetchException}，调用方据此跳过该分类的插入与删除，保留库中旧数据。
     */
    public List<ArenaRankDto> fetchCategory(Category category) {
        String url = pageUrl(category);
        try {
            String payload = fetchPayload(url);
            List<ArenaRankDto> rows = parsePayload(payload, category, url);
            if (rows.isEmpty()) {
                throw new ArenaFetchException("页面未解析到任何数据（官网可能改版）");
            }
            log.info("Code Arena 抓取完成：{} ({}) 共 {} 行", category.labelZh(), category.slug(), rows.size());
            return rows;
        } catch (ArenaFetchException e) {
            log.warn("Code Arena 抓取失败 category={}: {}", category.slug(), e.getMessage());
            throw e;
        } catch (Exception e) {
            String reason = friendlyReason(e);
            log.warn("Code Arena 抓取失败 category={}: {} ({})", category.slug(), reason, e.getClass().getSimpleName());
            throw new ArenaFetchException(reason);
        }
    }

    /** 把底层异常翻译成运维在管理页面上看得懂的一句话 */
    private static String friendlyReason(Exception e) {
        if (e instanceof HttpStatusException http) {
            int status = http.getStatusCode();
            if (status == 403 || status == 429 || status == 503) {
                return "官网拒绝访问（HTTP " + status + "，疑似风控/反爬拦截）";
            }
            return "官网返回 HTTP " + status;
        }
        if (e instanceof UnknownHostException) {
            return "域名解析失败";
        }
        if (e instanceof SocketTimeoutException) {
            return "请求超时";
        }
        if (e instanceof SSLException) {
            return "TLS 握手失败";
        }
        if (e instanceof SocketException) {
            return "连接被重置";
        }
        String message = e.getMessage();
        return (message == null || message.isBlank()) ? e.getClass().getSimpleName() : message;
    }

    /** 分类之间的礼貌间隔 */
    public void throttle() {
        try {
            Thread.sleep(THROTTLE_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 按 slug 找分类（导入数据包时用于识别文件名）；未知 slug 返回 null */
    public static Category categoryBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        String key = slug.trim().toLowerCase(Locale.ROOT);
        for (Category c : CATEGORIES) {
            if (c.slug().equals(key)) {
                return c;
            }
        }
        return null;
    }

    /**
     * 解析一份已经下载好的榜单数据 —— 「本地抓取 → 上传导入」通道。
     * 兼容新版 RSC 数据（text/x-component）与旧版整页 HTML。
     *
     * 【为什么要这条通道】官网对文档导航启用了 Cloudflare 托管挑战，
     * 某些网络下服务器侧拿不到页面；于是由本地脚本下载数据、上传后在服务端解析入库，
     * 这样解析规则仍然只有本类这一份，不会出现两份实现各自漂移。
     */
    public List<ArenaRankDto> parseCategoryPayload(String payload, Category category) {
        List<ArenaRankDto> rows = parsePayload(payload, category, pageUrl(category));
        if (rows.isEmpty()) {
            throw new ArenaFetchException("页面未解析到任何数据（下载不完整或官网改版）");
        }
        return rows;
    }

    /** overall 是默认分类，路径不带 slug；其它分类为 /webdev/{slug} */
    private static String pageUrl(Category category) {
        return "overall".equals(category.slug()) ? BASE : BASE + "/" + category.slug();
    }

    /**
     * 取回榜单数据。
     *
     * Cloudflare 会按客户端（TLS/HTTP 栈）指纹拦截 .NET 与 Java 的请求，但 Windows/Linux
     * 自带的 curl 能正常通过，所以优先调用 curl；curl 不存在时再退回 Jsoup
     * （适用于未被拦截的环境）。可用环境变量 CURL_EXE 指定 curl 路径。
     */
    private static String fetchPayload(String url) throws Exception {
        try {
            return fetchWithCurl(url);
        } catch (IOException curlMissing) {
            return Jsoup.connect(url)
                    .userAgent(UA)
                    .header("Accept", RSC_ACCEPT)
                    .header(RSC_HEADER, RSC_HEADER_VALUE)
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .ignoreContentType(true)
                    .timeout(REQUEST_TIMEOUT_MS)
                    .maxBodySize(0)
                    .execute()
                    .body();
        }
    }

    private static String fetchWithCurl(String url) throws Exception {
        String curl = System.getenv("CURL_EXE");
        if (curl == null || curl.isBlank()) {
            curl = "curl";
        }
        Process process = new ProcessBuilder(
                curl, "-sS", "-L", "--fail", "--max-time", "60",
                "-H", "Accept: " + RSC_ACCEPT,
                "-H", RSC_HEADER + ": " + RSC_HEADER_VALUE,
                "-H", "Accept-Language: " + ACCEPT_LANGUAGE,
                "-H", "User-Agent: " + UA,
                url)
                .start();
        byte[] body = process.getInputStream().readAllBytes();
        byte[] error = process.getErrorStream().readAllBytes();
        int exit = process.waitFor();
        if (exit != 0) {
            String reason = new String(error, StandardCharsets.UTF_8).trim();
            throw new ArenaFetchException("curl 抓取失败（exit " + exit + "）"
                    + (reason.isEmpty() ? "" : "：" + reason));
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /** 兼容两种数据：新版 RSC 数据（text/x-component）与旧版整页 HTML。 */
    private static List<ArenaRankDto> parsePayload(String payload, Category category, String url) {
        if (payload == null || payload.isBlank()) {
            return List.of();
        }
        String head = payload.stripLeading();
        if (head.startsWith("<!DOCTYPE") || head.startsWith("<html") || payload.contains("<table")) {
            return parseTable(Jsoup.parse(payload), category, url);
        }
        return parseEntries(payload, category, url);
    }

    /** 从 Next.js RSC 文本里取出 "entries":[...]，映射成与 HTML 解析一致的榜单行。 */
    private static List<ArenaRankDto> parseEntries(String payload, Category category, String url) {
        String json = extractFirstJsonArray(payload, "\"entries\"");
        if (json == null) {
            return List.of();
        }
        JsonNode entries;
        try {
            entries = JSON.readTree(json);
        } catch (Exception e) {
            return List.of();
        }
        List<ArenaRankDto> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonNode entry : entries) {
            String modelName = stringValue(entry, "modelDisplayName");
            if (modelName == null || !seen.add(modelName)) {
                continue; // 无模型名或同名重复（保留排名靠前者）
            }
            Integer score = intValue(entry, "rating");
            Integer upper = intValue(entry, "ratingUpper");
            Integer lower = intValue(entry, "ratingLower");
            Long contextTokens = longValue(entry, "contextLength");
            rows.add(ArenaRankDto.builder()
                    .category(category.slug())
                    .categoryGroup(category.group())
                    .sourceUrl(url)
                    .rank(intValue(entry, "rank"))
                    .rankSpreadMin(intValue(entry, "rankUpper"))
                    .rankSpreadMax(intValue(entry, "rankLower"))
                    .modelName(modelName)
                    .modelUrl(stringValue(entry, "modelUrl"))
                    .org(stringValue(entry, "modelOrganization"))
                    .license(stringValue(entry, "license"))
                    .score(score)
                    .scoreCiPlus(score != null && upper != null ? Math.max(0, upper - score) : null)
                    .scoreCiMinus(score != null && lower != null ? Math.max(0, score - lower) : null)
                    .votes(intValue(entry, "votes"))
                    .inputPrice(decimalValue(entry, "inputPricePerMillion"))
                    .outputPrice(decimalValue(entry, "outputPricePerMillion"))
                    .contextText(formatContext(contextTokens))
                    .contextTokens(contextTokens)
                    .isPreliminary(stringValue(entry, "releaseType") == null ? 0 : 1)
                    .build());
        }
        return rows;
    }

    /** 取出 `"key":[ ... ]` 的数组文本；跳过字符串内的括号与转义。 */
    private static String extractFirstJsonArray(String text, String quotedKey) {
        int key = text.indexOf(quotedKey);
        if (key < 0) {
            return null;
        }
        int open = text.indexOf('[', key + quotedKey.length());
        if (open < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = open; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(open, i + 1);
                }
            }
        }
        return null;
    }

    private static String stringValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : trimToNull(value.asText());
    }

    private static Integer intValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : (int) Math.round(value.asDouble());
    }

    private static Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.asLong();
    }

    private static BigDecimal decimalValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.decimalValue();
    }

    /** 1000000 → "1M"，1050000 → "1.1M"，262144 → "262.1K" */
    private static String formatContext(Long tokens) {
        if (tokens == null || tokens <= 0) {
            return null;
        }
        if (tokens >= 1_000_000L) {
            return compact(tokens, 1_000_000L) + "M";
        }
        if (tokens >= 1_000L) {
            return compact(tokens, 1_000L) + "K";
        }
        return String.valueOf(tokens);
    }

    private static String compact(long value, long unit) {
        return BigDecimal.valueOf(value)
                .divide(BigDecimal.valueOf(unit), 1, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static List<ArenaRankDto> parseTable(Document doc, Category category, String url) {
        List<ArenaRankDto> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Element tr : doc.select("table tr")) {
            Elements tds = tr.children().select("td");
            if (tds.size() < COLUMN_COUNT) {
                continue; // 表头行或异常行
            }
            String modelName = modelName(tds.get(2));
            if (modelName == null || !seen.add(modelName)) {
                continue; // 无模型名或同名重复（保留排名靠前者）
            }

            Element modelCell = tds.get(2);
            Element link = modelCell.selectFirst("a[href]");
            String[] orgLicense = splitOrgLicense(textOf(modelCell.selectFirst("span.text-text-secondary")));
            Integer[] score = parseScore(textOf(tds.get(3)));
            BigDecimal[] price = parsePrice(textOf(tds.get(5)));
            int[] spread = parseRankSpread(tds.get(1));

            rows.add(ArenaRankDto.builder()
                    .category(category.slug())
                    .categoryGroup(category.group())
                    .sourceUrl(url)
                    .rank(parseInt(textOf(tds.get(0))))
                    .rankSpreadMin(spread[0])
                    .rankSpreadMax(spread[1])
                    .modelName(modelName)
                    .modelUrl(link == null ? null : trimToNull(link.attr("href")))
                    .org(orgLicense[0])
                    .license(orgLicense[1])
                    .score(score[0])
                    .scoreCiPlus(score[1])
                    .scoreCiMinus(score[2])
                    .votes(parseInt(textOf(tds.get(4))))
                    .inputPrice(price[0])
                    .outputPrice(price[1])
                    .contextText(trimToNull(textOf(tds.get(6))))
                    .contextTokens(parseContextTokens(textOf(tds.get(6))))
                    .isPreliminary(textOf(tds.get(3)).contains(PRELIMINARY) ? 1 : 0)
                    .build());
        }
        return rows;
    }

    /** 模型名优先取 <a> 内 span 的 title 属性（最干净），退化到 <a> 文本 */
    private static String modelName(Element modelCell) {
        Element link = modelCell.selectFirst("a[href]");
        if (link == null) {
            return null;
        }
        Element nameSpan = link.selectFirst("span[title]");
        if (nameSpan != null && !nameSpan.attr("title").isBlank()) {
            return nameSpan.attr("title").trim();
        }
        return trimToNull(link.text());
    }

    /** "OpenAI · Proprietary" → ["OpenAI", "Proprietary"]；无分隔符时 license 为 null */
    private static String[] splitOrgLicense(String raw) {
        if (raw == null || raw.isBlank()) {
            return new String[]{null, null};
        }
        String text = raw.trim();
        int idx = text.indexOf('·');
        if (idx < 0) {
            return new String[]{text, null};
        }
        return new String[]{trimToNull(text.substring(0, idx)), trimToNull(text.substring(idx + 1))};
    }

    /** 排名区间：该单元格内通常有两个数字 span（下限、上限），只有一个时上下限相同 */
    private static int[] parseRankSpread(Element cell) {
        List<Integer> values = new ArrayList<>();
        for (Element span : cell.select("span")) {
            Integer v = parseInt(span.text());
            if (v != null) {
                values.add(v);
            }
        }
        if (values.isEmpty()) {
            return new int[]{0, 0};
        }
        int min = values.get(0);
        int max = values.size() > 1 ? values.get(values.size() - 1) : min;
        return new int[]{min, max};
    }

    /** "1758 +14/-14" → [1758, 14, 14]；无分数（仅 Preliminary）→ [null, null, null] */
    private static Integer[] parseScore(String cell) {
        Matcher m = SCORE.matcher(cell);
        if (m.find()) {
            return new Integer[]{parseInt(m.group(1)), parseInt(m.group(2)), parseInt(m.group(3))};
        }
        Matcher only = INTEGER.matcher(cell);
        if (only.find()) {
            return new Integer[]{parseInt(only.group()), null, null};
        }
        return new Integer[]{null, null, null};
    }

    /** "$10 / $50" → [10, 50]；N/A 或解析失败 → [null, null] */
    private static BigDecimal[] parsePrice(String cell) {
        Matcher m = PRICE.matcher(cell);
        if (!m.find()) {
            return new BigDecimal[]{null, null};
        }
        return new BigDecimal[]{toDecimal(m.group(1)), toDecimal(m.group(2))};
    }

    /** "262.1K" / "1M" / "1.1M" → tokens；N/A → null */
    private static Long parseContextTokens(String cell) {
        if (cell.isBlank() || !Character.isDigit(cell.trim().charAt(0))) {
            return null;
        }
        Matcher m = CONTEXT.matcher(cell.trim());
        if (!m.find()) {
            return null;
        }
        double value;
        try {
            value = Double.parseDouble(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
        String unit = m.group(2);
        if (unit != null && unit.equalsIgnoreCase("K")) {
            value *= 1_000L;
        } else if (unit != null && unit.equalsIgnoreCase("M")) {
            value *= 1_000_000L;
        }
        return (long) value;
    }

    private static Integer parseInt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Matcher m = INTEGER.matcher(raw.replace(",", ""));
        if (!m.find()) {
            return null;
        }
        try {
            return Integer.valueOf(m.group());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(String raw) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String textOf(Element el) {
        return el == null ? "" : el.text().trim();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
