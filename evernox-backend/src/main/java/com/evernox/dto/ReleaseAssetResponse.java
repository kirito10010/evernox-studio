package com.evernox.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 版本附件
 */
@Data
@Builder
public class ReleaseAssetResponse {

    private Long id;
    private String fileName;
    private Long size;
    private String sha512;
    private Long downloadCount;
    private LocalDateTime createdAt;
}
