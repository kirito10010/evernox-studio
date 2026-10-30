package com.evernox.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 版本详情
 */
@Data
@Builder
public class ReleaseDetailResponse {

    private Long id;
    private Long projectId;
    private String tag;
    private String title;
    private String notes;
    private Integer status;
    private boolean latest;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private long downloadCount;
    private long assetCount;

    private long totalSize;
    private List<ReleaseAssetResponse> assets;
}
