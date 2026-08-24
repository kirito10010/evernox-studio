package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话题圈成员响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicMemberResponse {

    private Long userId;
    private String username;

    /** 是否圈主 */
    private Boolean isOwner;

    private LocalDateTime createdAt;
}
