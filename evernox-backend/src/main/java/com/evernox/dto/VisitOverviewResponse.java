package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 访问概览数字
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VisitOverviewResponse {

    /** 今日活跃用户数 */
    private Long todayActiveUsers;

    /** 今日访问量 */
    private Long todayVisits;

    /** 今日登录次数 */
    private Long todayLogins;

    /** 近7天活跃用户数 */
    private Long weekActiveUsers;
}
