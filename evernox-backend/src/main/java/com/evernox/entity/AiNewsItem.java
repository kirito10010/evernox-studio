package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 编程资讯条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ai_news_item")
public class AiNewsItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 来源: hackernews/reddit/bilibili/juejin/sspai */
    private String source;

    /** 源内唯一 ID（HN objectID / Reddit id / RSS guid） */
    private String externalId;

    /** 标题 */
    private String title;

    /** 中文标题（自动翻译，可能为空） */
    private String titleZh;

    /** 原文链接 */
    private String url;

    /** 摘要（可选） */
    private String summary;

    /** 中文摘要（自动翻译，可能为空） */
    private String summaryZh;

    /** 分类标签 */
    private String tag;

    /** 热度分数 */
    private Integer score;

    /** 发布时间 */
    private LocalDateTime publishedAt;

    /** 入库时间 */
    private LocalDateTime createdAt;
}
