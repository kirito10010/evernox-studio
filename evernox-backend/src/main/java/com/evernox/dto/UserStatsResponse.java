package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户统计响应（不含管理员账号）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserStatsResponse {

    /** 非管理员账号总数 */
    private long total;

    /** 普通成员数 */
    private long members;

    /** 超级会员数 */
    private long superMembers;

    /** 已禁用账号数 */
    private long disabled;
}
