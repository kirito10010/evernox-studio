package com.evernox.service;

/**
 * 积分与会员服务
 */
public interface PointsService {

    /** 每日签到（固定 +10） */
    void signIn(Long userId);

    /** 管理员充值积分 */
    void recharge(Long adminId, Long userId, Integer points, String description);

    /** 设置超级会员时长（天） */
    void setSuperMember(Long userId, Integer days);

    /** 用户自助用积分开通/续费超级会员 */
    void upgradeSuperMember(Long userId, Integer days);
}
