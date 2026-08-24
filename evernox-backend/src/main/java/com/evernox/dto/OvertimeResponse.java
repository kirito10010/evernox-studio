package com.evernox.dto;

import com.evernox.entity.PerformanceOvertime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加班记录响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OvertimeResponse {

    private Long id;
    private LocalDate workDate;
    private BigDecimal overtimeHours;
    private BigDecimal overtimeDays;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static OvertimeResponse from(PerformanceOvertime o) {
        return OvertimeResponse.builder()
                .id(o.getId())
                .workDate(o.getWorkDate())
                .overtimeHours(o.getOvertimeHours())
                .overtimeDays(o.getOvertimeDays())
                .createdAt(o.getCreatedAt())
                .updatedAt(o.getUpdatedAt())
                .build();
    }
}
