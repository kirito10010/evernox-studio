package com.evernox.dto;

import com.evernox.entity.TopicPost;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话题帖子响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicPostResponse {

    private Long id;
    private Long circleId;
    private String circleName;
    private Long userId;
    private String authorName;
    private String title;
    private String content;
    private Integer likeCount;
    private Integer commentCount;
    private Integer favoriteCount;

    /** 当前用户是否已点赞/收藏 */
    private Boolean liked;
    private Boolean favorited;

    private LocalDateTime createdAt;

    /** 列表/详情共用的基础字段（内容按需清空） */
    public static TopicPostResponse base(TopicPost p) {
        return TopicPostResponse.builder()
                .id(p.getId())
                .circleId(p.getCircleId())
                .userId(p.getUserId())
                .title(p.getTitle())
                .content(p.getContent())
                .likeCount(p.getLikeCount())
                .commentCount(p.getCommentCount())
                .favoriteCount(p.getFavoriteCount())
                .createdAt(p.getCreatedAt())
                .build();
    }
}
