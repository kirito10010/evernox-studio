package com.evernox.dto;

import com.evernox.entity.PerformanceLate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 迟到记录响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LateResponse {

    private Long id;
    private LocalDate workDate;
    private Integer lateMinutes;
    private BigDecimal lateDays;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static LateResponse from(PerformanceLate l) {
        return LateResponse.builder()
                .id(l.getId())
                .workDate(l.getWorkDate())
                .lateMinutes(l.getLateMinutes())
                .lateDays(l.getLateDays())
                .createdAt(l.getCreatedAt())
                .updatedAt(l.getUpdatedAt())
                .build();
    }
}
