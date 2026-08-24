package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 消费趋势响应 DTO（按日聚合，供曲线图使用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseChartResponse {

    /** 每日数据点（仅包含有记录的日期，升序） */
    private List<Point> points;

    /** 范围内总支出 */
    private BigDecimal totalAmount;

    /** 范围内消费笔数 */
    private Long count;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private LocalDate date;
        private BigDecimal total;
        private List<Breakdown> breakdown;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Breakdown {
        private Long categoryId;
        private String categoryName;
        private BigDecimal amount;
    }
}
