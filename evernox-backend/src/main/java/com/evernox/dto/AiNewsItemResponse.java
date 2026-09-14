package com.evernox.dto;

import com.evernox.entity.AiNewsItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 编程资讯条目响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiNewsItemResponse {

    private Long id;
    private String source;
    private String title;
    private String titleZh;
    private String url;
    private String summary;
    private String summaryZh;
    private String tag;
    private Integer score;
    private LocalDateTime publishedAt;
    /** 当前用户是否已收藏 */
    private Boolean favorited;

    public static AiNewsItemResponse from(AiNewsItem item, boolean favorited) {
        return AiNewsItemResponse.builder()
                .id(item.getId())
                .source(item.getSource())
                .title(item.getTitle())
                .titleZh(item.getTitleZh())
                .url(item.getUrl())
                .summary(item.getSummary())
                .summaryZh(item.getSummaryZh())
                .tag(item.getTag())
                .score(item.getScore())
                .publishedAt(item.getPublishedAt())
                .favorited(favorited)
                .build();
    }
}
