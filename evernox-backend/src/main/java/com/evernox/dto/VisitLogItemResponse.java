package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 访问日志列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitLogItemResponse {

    private Long id;
    private Long userId;
    private String username;
    private String type;
    private String ip;
    private String userAgent;
    private String path;
    private LocalDateTime createdAt;
}
