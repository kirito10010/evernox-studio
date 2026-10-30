package com.evernox.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 发布项目列表/详情响应
 */
@Data
@Builder
public class ReleaseProjectResponse {

    private Long id;
    private String name;
    private String slug;
    private String description;
    private Integer sort;

    private long releaseCount;
    private long publishedCount;
    private String latestTag;
    private LocalDateTime latestPublishedAt;
    private long assetCount;
    private long storageBytes;
    private long downloadTotal;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
