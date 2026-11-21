package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 彩旗献礼期响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolFlagPeriodResponse {

    private Long id;
    private Long activityId;
    private Long shopId;
    private String name;

    /** 本期开始 */
    private LocalDateTime startTime;

    /** 本期结束 */
    private LocalDateTime endTime;

    /** 2026-11-19 ~ 2026-12-03 */
    private String label;

    /** 本期商品件数 */
    private int itemCount;
}
