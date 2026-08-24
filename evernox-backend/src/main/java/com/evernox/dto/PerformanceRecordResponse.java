package com.evernox.dto;

import com.evernox.entity.PerformanceRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 绩效记录响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceRecordResponse {

    private Long id;
    private Long projectId;
    private String projectName;
    private LocalDate workDate;
    private Integer processType;
    private BigDecimal quota;
    private BigDecimal actualWorkload;
    private BigDecimal performanceDays;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PerformanceRecordResponse from(PerformanceRecord record, String projectName) {
        return PerformanceRecordResponse.builder()
                .id(record.getId())
                .projectId(record.getProjectId())
                .projectName(projectName)
                .workDate(record.getWorkDate())
                .processType(record.getProcessType())
                .quota(record.getQuota())
                .actualWorkload(record.getActualWorkload())
                .performanceDays(record.getPerformanceDays())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .build();
    }
}
