package com.evernox.service;

import com.evernox.dto.ArenaRankDto;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.math.BigDecimal;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
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
 * 榜单页是服务端渲染的，首屏 HTML 就带完整表格，无需登录、无需额外请求头；
 * 侧边栏「Category」按钮是前端状态，但 URL 路径段 /leaderboard/code/webdev/{slug} 会被服务端正确区分，
 * 因此按 slug 逐页抓取（每类一页，无分页）。
 * 项目约定不使用站点自带翻译，固定英文请求头，保留英文原文。
 */
@Slf4j
@Component
public class AiArenaCrawler {

    private static final String BASE = "https://arena.ai/leaderboard/code/webdev/";

    /**
     * 用真实浏览器 UA + 完整浏览器请求头。
     *
     * 【重要】不能改成自定义 UA：运维实测发现，从云服务器（机房 IP）用非浏览器特征请求时，
     * 官网的 CDN 风控会直接返回 403（同样代码在办公网/家宽上正常），
     * 表现为「12 个分类全部抓取失败、每个只隔不到 1 秒」。
     */
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/127.0.0.0 Safari/537.36";

    private static final String ACCEPT =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8";

    private static final String SEC_CH_UA = "\"Chromium\";v=\"127\", \"Not)A;Brand\";v=\"99\", \"Google Chrome\";v=\"127\"";

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
        String url = BASE + category.slug();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(UA)
                    .header("Accept", ACCEPT)
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("sec-ch-ua", SEC_CH_UA)
                    .header("sec-ch-ua-mobile", "?0")
                    .header("sec-ch-ua-platform", "\"Windows\"")
                    .timeout(REQUEST_TIMEOUT_MS)
                    .maxBodySize(0)
                    .get();
            List<ArenaRankDto> rows = parseTable(doc, category, url);
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
     * 解析一份已经下载好的榜单页 HTML —— 「本地抓取 → 上传导入」通道。
     *
     * 【为什么要这条通道】生产服务器是机房 IP，被官网 CDN 风控直接 403，
     * 服务器侧无论如何都拿不到页面；于是改由本地脚本下载页面、上传后在服务端解析入库，
     * 这样解析规则仍然只有本类这一份，不会出现两份实现各自漂移。
     */
    public List<ArenaRankDto> parseCategoryHtml(String html, Category category) {
        List<ArenaRankDto> rows = parseTable(Jsoup.parse(html), category, BASE + category.slug());
        if (rows.isEmpty()) {
            throw new ArenaFetchException("页面未解析到任何数据（下载不完整或官网改版）");
        }
        return rows;
    }

    private List<ArenaRankDto> parseTable(Document doc, Category category, String url) {
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
