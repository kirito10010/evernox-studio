package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 待办统计（当前用户）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TodoStatsResponse {

    /** 未完成总数 */
    private Long pending;

    /** 今天到期且未完成 */
    private Long dueToday;

    /** 已逾期且未完成 */
    private Long overdue;

    /** 已完成总数 */
    private Long done;
}
