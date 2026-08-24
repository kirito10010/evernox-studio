package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 绩效趋势响应 DTO（按日聚合绩效人天）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceChartResponse {

    /** 每日数据点（仅包含有记录的日期，升序） */
    private List<Point> points;

    /** 范围内净绩效总和（扣底量后） */
    private BigDecimal totalDays;

    /** 范围内上班天数（有记录的天数） */
    private Integer workDays;

    /** 范围内记录数 */
    private Long count;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private LocalDate date;
        private BigDecimal total;
    }
}
