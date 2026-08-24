package com.evernox.dto;

import com.evernox.entity.TopicComment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话题评论响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicCommentResponse {

    private Long id;
    private Long postId;
    private String postTitle;
    private Long userId;
    private String authorName;
    private String content;
    private LocalDateTime createdAt;

    public static TopicCommentResponse from(TopicComment c) {
        return TopicCommentResponse.builder()
                .id(c.getId())
                .postId(c.getPostId())
                .userId(c.getUserId())
                .content(c.getContent())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
