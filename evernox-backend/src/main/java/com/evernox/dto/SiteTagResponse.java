package com.evernox.dto;

import com.evernox.entity.SiteTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 站点标签响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SiteTagResponse {

    private Long id;
    private String name;
    private Integer sort;

    /** 关联的站点数量，仅标签管理场景填充 */
    private Long siteCount;

    public static SiteTagResponse from(SiteTag tag) {
        return SiteTagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .sort(tag.getSort())
                .build();
    }
}
