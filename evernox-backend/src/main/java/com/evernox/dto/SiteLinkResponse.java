package com.evernox.dto;

import com.evernox.entity.SiteLink;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 网站分享响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteLinkResponse {

    private Long id;
    private Long userId;
    private String title;
    private String url;
    private String description;
    private Long coverImageId;
    private Integer status;
    private String rejectReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 分享者用户名 */
    private String ownerName;

    /** 审批管理员用户名 */
    private String reviewerName;

    /** 站点标签 */
    private List<SiteTagResponse> tags;

    public static SiteLinkResponse from(SiteLink site) {
        return SiteLinkResponse.builder()
                .id(site.getId())
                .userId(site.getUserId())
                .title(site.getTitle())
                .url(site.getUrl())
                .description(site.getDescription())
                .coverImageId(site.getCoverImageId())
                .status(site.getStatus())
                .rejectReason(site.getRejectReason())
                .submittedAt(site.getSubmittedAt())
                .reviewedAt(site.getReviewedAt())
                .createdAt(site.getCreatedAt())
                .updatedAt(site.getUpdatedAt())
                .build();
    }
}
