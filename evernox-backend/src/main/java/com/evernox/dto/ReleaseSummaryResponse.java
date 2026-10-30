package com.evernox.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本列表项
 */
@Data
@Builder
public class ReleaseSummaryResponse {

    private Long id;
    private Long projectId;
    private String tag;
    private String title;
    private String summary;
    private Integer status;
    private boolean latest;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private long downloadCount;
    private long assetCount;
    private long totalSize;
}

