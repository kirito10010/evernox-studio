package com.evernox.dto;

import com.evernox.entity.PerformanceProject;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 生产项目响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceProjectResponse {

    private Long id;
    private String name;
    private BigDecimal workQuota;
    private BigDecimal inspectQuota;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PerformanceProjectResponse from(PerformanceProject project) {
        return PerformanceProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .workQuota(project.getWorkQuota())
                .inspectQuota(project.getInspectQuota())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
