package com.evernox.service.impl;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 笔记正文消毒器
 *
 * 前端渲染前也会用 DOMPurify 过一遍，但服务端这一道是必须的：请求可以绕过前端直发，
 * 一旦把带脚本的 HTML 存进库，任何读到这条笔记的人都会中招（存储型 XSS）。
 */
@Component
public class NoteHtmlSanitizer {

    /** 摘要长度上限，与 note.summary 字段一致 */
    private static final int SUMMARY_MAX = 300;

    /**
     * 白名单
     *
     * 关键点：
     * - img 只保留 data-image-id，不放行 src：正文里不应出现任何可直接访问的地址，
     *   图片一律由前端按 id 取流后填充。
     * - a 的协议限定 http/https，挡住 javascript: / data:。
     * - 不放行 style 与任何 on* 事件属性（Safelist 默认即不放行）。
     */
    private static final Safelist SAFELIST = new Safelist()
            .addTags("p", "br", "strong", "b", "em", "i", "u", "s", "h1", "h2", "h3",
                    "ul", "ol", "li", "blockquote", "pre", "code", "a", "img", "span")
            .addAttributes("a", "href", "title")
            .addAttributes("img", "data-image-id")
            .addAttributes("span", "class")
            .addAttributes("pre", "class")
            .addAttributes("code", "class")
            .addProtocols("a", "href", "http", "https");

    /**
     * 消毒正文
     *
     * @return 安全的 HTML 片段，入参为空时返回空串
     */
    public String sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return "";
        }
        return Jsoup.clean(rawHtml, "", SAFELIST);
    }

    /**
     * 从已消毒的 HTML 提取纯文本摘要
     */
    public String toSummary(String safeHtml) {
        if (safeHtml == null || safeHtml.isBlank()) {
            return "";
        }
        String text = Jsoup.parse(safeHtml).text().trim();
        return text.length() <= SUMMARY_MAX ? text : text.substring(0, SUMMARY_MAX);
    }

    /**
     * 提取正文引用的插图 id（保持出现顺序，去重）
     *
     * 非法的 data-image-id 直接跳过：这类脏值只会来自手工构造的请求。
     */
    public Set<Long> extractImageIds(String safeHtml) {
        Set<Long> ids = new LinkedHashSet<>();
        if (safeHtml == null || safeHtml.isBlank()) {
            return ids;
        }
        Document doc = Jsoup.parse(safeHtml);
        for (Element img : doc.select("img[data-image-id]")) {
            try {
                ids.add(Long.parseLong(img.attr("data-image-id").trim()));
            } catch (NumberFormatException ignored) {
                // 脏值跳过
            }
        }
        return ids;
    }
}
