package com.evernox.service;

import cn.hutool.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 火影忍者OL 官网活动抓取与解析。
 *
 * 官网是 GBK 静态页；公告列表第 4 页起编号变成 list_n{N}.shtml（N 递减），
 * 所以翻页只跟随页面底部的「下一页」链接，不硬编码页码。
 * 活动信息不在列表里，而在「停机更新公告」正文的【限时热点】段：
 * 形如「活动-仙人の宝库上线  *开启时间：4月25日停机更新后—5月1日23:59:59」。
 */
@Slf4j
@Component
public class HyolActivityCrawler {

    public static final String LIST_URL =
            "https://huoying.qq.com/webplat/info/news_version3/5491/5492/5494/m3987/list_1.shtml";

    private static final String BASE_URL = "https://huoying.qq.com";
    private static final Charset GBK = Charset.forName("GBK");
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int TIMEOUT_MS = 15_000;
    /** 两次请求之间的最小间隔，避免把官网打出限流 */
    private static final long THROTTLE_MS = 250L;

    /** 活动标题：活动-XXX上线 / 活动-XXX开启（活动名保持原文，只去掉首尾空白） */
    private static final Pattern ACTIVITY = Pattern.compile("活动\\s*[-－—]\\s*(.{1,40}?)(?:上线|开启)");
    /** 开启时间：4月25日停机更新后—5月1日23:59:59 */
    private static final Pattern OPEN_TIME = Pattern.compile("开启时间\\s*[:：]\\s*([^*＊]{4,60})");
    /** 日期片段：5月1日 / 5月1日23:59:59 / 5月1日 5:00 */
    private static final Pattern DATE_TOKEN =
            Pattern.compile("(\\d{1,2})月(\\d{1,2})日(?:\\s*(\\d{1,2})[:：](\\d{2})(?:[:：](\\d{2}))?)?");

    /** 限流：所有线程共用，保证相邻请求至少间隔 THROTTLE_MS */
    private static long lastRequestAt = 0L;

    public record ListItem(String title, String url, LocalDate date) {
    }

    public record ListPage(List<ListItem> items, String nextUrl) {
    }

    public record ParsedActivity(String name, String category, LocalDateTime startTime, LocalDateTime endTime) {
    }

    /** 抓一页公告列表，同时带回「下一页」链接 */
    public ListPage fetchListPage(String url) {
        String html = fetchGbk(url);
        if (html == null || html.isBlank()) {
            return new ListPage(List.of(), null);
        }
        Document doc = Jsoup.parse(html);
        List<ListItem> items = new ArrayList<>();
        for (Element li : doc.select("ul.list_con li")) {
            Element link = li.selectFirst("a.n-title");
            if (link == null) {
                continue;
            }
            String title = link.attr("title");
            if (title.isBlank()) {
                title = link.text();
            }
            String href = link.attr("href");
            if (href.isBlank() || title.isBlank()) {
                continue;
            }
            Element dateEl = li.selectFirst("span.date");
            LocalDate date = parseDate(dateEl == null ? "" : dateEl.text());
            items.add(new ListItem(title.trim(), absolutize(href), date));
        }
        return new ListPage(items, findNextUrl(doc));
    }

    /** 抓公告正文并转成规范化纯文本 */
    public String fetchDetailText(String url) {
        String html = fetchGbk(url);
        if (html == null || html.isBlank()) {
            return "";
        }
        Document doc = Jsoup.parse(html);
        Element content = doc.selectFirst("#detail_con");
        if (content == null) {
            return "";
        }
        String text = content.text().replace('\u00a0', ' ');
        return text.replaceAll("\\s+", " ").trim();
    }

    /** 从正文提取【限时热点】段里的活动 */
    public List<ParsedActivity> parseActivities(String text, LocalDate announceDate) {
        if (text == null || text.isBlank() || announceDate == null) {
            return List.of();
        }
        String section = limitSection(text);
        if (section.isBlank()) {
            return List.of();
        }
        List<Integer> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Matcher m = ACTIVITY.matcher(section);
        while (m.find()) {
            String name = cleanName(m.group(1));
            if (name.isEmpty()) {
                continue;
            }
            starts.add(m.start());
            names.add(name);
        }
        List<ParsedActivity> result = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            int from = starts.get(i);
            int to = i + 1 < starts.size() ? starts.get(i + 1) : section.length();
            String segment = section.substring(from, to);
            ParsedActivity parsed = buildActivity(names.get(i), segment, announceDate);
            if (parsed != null) {
                result.add(parsed);
            }
        }
        return result;
    }

    // ==================== 内部方法 ====================

    /** 截取【限时热点】/【限时活动】段，遇到下一个【…】结束 */
    private String limitSection(String text) {
        int start = text.indexOf("【限时热点】");
        if (start < 0) {
            start = text.indexOf("【限时活动】");
        }
        if (start < 0) {
            return "";
        }
        int from = start + 6;
        int end = text.indexOf("【", from);
        return end > from ? text.substring(from, end) : text.substring(from);
    }

    private ParsedActivity buildActivity(String name, String segment, LocalDate announceDate) {
        Matcher timeMatcher = OPEN_TIME.matcher(segment);
        if (!timeMatcher.find()) {
            return new ParsedActivity(name, categorize(name), null, null);
        }
        String raw = timeMatcher.group(1).trim();
        Matcher tokenMatcher = DATE_TOKEN.matcher(raw);
        List<int[]> tokens = new ArrayList<>();
        while (tokenMatcher.find()) {
            int[] token = new int[6];
            token[0] = parseInt(tokenMatcher.group(1));
            token[1] = parseInt(tokenMatcher.group(2));
            token[2] = tokenMatcher.group(3) == null ? -1 : parseInt(tokenMatcher.group(3));
            token[3] = tokenMatcher.group(4) == null ? -1 : parseInt(tokenMatcher.group(4));
            token[4] = tokenMatcher.group(5) == null ? -1 : parseInt(tokenMatcher.group(5));
            tokens.add(token);
        }
        if (tokens.isEmpty()) {
            return new ParsedActivity(name, categorize(name), null, null);
        }
        LocalDateTime start = toDateTime(tokens.get(0), announceDate, true);
        LocalDateTime end = tokens.size() > 1 ? toDateTime(tokens.get(1), announceDate, false) : null;
        if (start != null && end != null && end.isBefore(start)) {
            end = end.plusYears(1);
        }
        return new ParsedActivity(name, categorize(name), start, end);
    }

    /**
     * 把「M月D日[时:分[:秒]]」补成完整时间：
     * 年份以公告发布日期为基准，明显早于公告月份视为跨年；
     * 只有日期没有时刻时，开始默认 10:00（停机更新后开服），结束默认 23:59:59。
     */
    private LocalDateTime toDateTime(int[] token, LocalDate announceDate, boolean start) {
        int month = token[0];
        int day = token[1];
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            return null;
        }
        int year = announceDate.getYear();
        if (month < announceDate.getMonthValue() - 1) {
            year += 1;
        }
        LocalTime time;
        if (token[2] >= 0) {
            int hour = Math.min(token[2], 23);
            int minute = token[3] >= 0 ? Math.min(token[3], 59) : 0;
            int second = token[4] >= 0 ? Math.min(token[4], 59) : 0;
            time = LocalTime.of(hour, minute, second);
        } else {
            time = start ? LocalTime.of(10, 0) : LocalTime.of(23, 59, 59);
        }
        try {
            return LocalDateTime.of(LocalDate.of(year, month, day), time);
        } catch (Exception e) {
            return null;
        }
    }

    /** 活动名保持官网原文：只去首尾空白，不删引号/书名号，方便核对抓取是否准确 */
    private String cleanName(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    static String categorize(String name) {
        if (name == null) {
            return "其他";
        }
        if (name.matches(".*(返利|返点|返券|返还).*")) {
            return "返利";
        }
        if (name.contains("团购")) {
            return "团购";
        }
        if (name.matches(".*(宝库|夺宝|藏宝).*")) {
            return "宝库";
        }
        if (name.contains("总动员")) {
            return "总动员";
        }
        if (name.matches(".*(签到|登录礼).*")) {
            return "签到";
        }
        if (name.matches(".*(抽奖|转盘|扭蛋|许愿|福袋|盲盒).*")) {
            return "抽奖";
        }
        if (name.matches(".*(抢购|折扣|特惠|限时购|精品店|商城).*")) {
            return "折扣";
        }
        if (name.matches(".*(礼包|福利|回馈|赠礼|领取).*")) {
            return "礼包";
        }
        return "其他";
    }

    private String findNextUrl(Document doc) {
        for (Element a : doc.select("ul.pager a")) {
            String href = a.attr("href");
            if (href.contains("list_") && a.text().contains("下一页")) {
                return absolutize(href);
            }
        }
        return null;
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim().replace('/', '-');
        if (value.length() < 8) {
            return null;
        }
        try {
            return LocalDate.parse(value.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    private String absolutize(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        if (url.startsWith("//")) {
            return "https:" + url;
        }
        if (url.startsWith("/")) {
            return BASE_URL + url;
        }
        return url;
    }

    /** 抓 GBK 页面，失败重试一次 */
    private String fetchGbk(String url) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                throttle();
                byte[] bytes = HttpRequest.get(url)
                        .header("User-Agent", UA)
                        .timeout(TIMEOUT_MS)
                        .execute()
                        .bodyBytes();
                return new String(bytes, GBK);
            } catch (Exception e) {
                log.warn("抓取官网失败({}): url={}, err={}", attempt + 1, url, e.getMessage());
            }
        }
        return null;
    }

    private static synchronized void throttle() {
        long now = System.currentTimeMillis();
        long wait = lastRequestAt + THROTTLE_MS - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastRequestAt = System.currentTimeMillis();
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return -1;
        }
    }
}
