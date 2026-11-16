package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商城特惠「期」概览：带 7 天各自的商品数与窗口
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolMallPeriodResponse {

    private Long id;

    private Long activityId;

    private Long shopId;

    private String name;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    /** 便于前端展示：2026-11-05 ~ 2026-11-11 */
    private String label;

    /** 本期天数（正常为 7） */
    private Integer dayCount;

    /** 本期商品总数 */
    private Integer itemCount;

    /** 已导入数据的天数 */
    private Integer filledDayCount;

    /** 逐日概览，按日期升序 */
    private List<HyolMallDayResponse> days;
}
