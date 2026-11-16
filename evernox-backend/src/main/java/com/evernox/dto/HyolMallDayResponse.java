package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商城特惠单日概览：一期固定 7 天，没有导入数据的天 itemCount = 0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolMallDayResponse {

    /** 归属日 yyyy-MM-dd */
    private String date;

    /** 星期文案：周一…周日 */
    private String weekday;

    /** 当日商品数 */
    private Integer itemCount;

    /** 当日窗口开始（05:00） */
    private LocalDateTime windowStart;

    /** 当日窗口结束（次日 04:59:59，末日取期结束时间） */
    private LocalDateTime windowEnd;
}
