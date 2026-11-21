package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 战场的召唤期响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolBattlePeriodResponse {

    private Long id;
    private Long activityId;
    private String name;

    /** 活动结束时间（期唯一键） */
    private LocalDateTime endTime;

    /** 截止 2026-12-03 */
    private String label;

    /** 本期商品件数 */
    private int itemCount;

    /** 阵营（波风之力 / 宇智波之力） */
    private List<HyolBattleCampResponse> camps;
}
