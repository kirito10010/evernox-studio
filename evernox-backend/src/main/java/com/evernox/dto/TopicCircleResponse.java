package com.evernox.dto;

import com.evernox.entity.TopicCircle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话题圈响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicCircleResponse {

    private Long id;
    private String name;
    private String description;
    private Long ownerId;
    private String ownerName;
    private Integer postCount;
    private Integer memberCount;

    /** 当前用户是否已关注 */
    private Boolean followed;

    private LocalDateTime createdAt;

    public static TopicCircleResponse from(TopicCircle c) {
        return TopicCircleResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .description(c.getDescription())
                .ownerId(c.getOwnerId())
                .postCount(c.getPostCount())
                .memberCount(c.getMemberCount())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
