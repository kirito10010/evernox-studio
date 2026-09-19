package com.evernox.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.evernox.entity.AiNewsItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AI 编程资讯采集器：Hacker News（Algolia API，英文，需翻译）。
 *
 * 只负责「拉取 + 解析 + 关键词过滤 + 标签分类」，入库去重交给 Service。
 */
@Slf4j
@Component
public class AiNewsCrawler {

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

    public List<AiNewsItem> crawlAll() {
        return fetchHackerNews();
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

    private LocalDateTime toLocalDateTime(long epochSeconds) {
        if (epochSeconds <= 0) {
            return LocalDateTime.now();
        }
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
