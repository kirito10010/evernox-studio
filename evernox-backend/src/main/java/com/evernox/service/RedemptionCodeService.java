package com.evernox.service;

import com.evernox.dto.RedemptionCodeResponse;

import java.util.List;

/**
 * 超级会员卡密服务
 */
public interface RedemptionCodeService {

    /** 生成卡密（days=7或30），返回本次生成的卡密列表 */
    List<RedemptionCodeResponse> generate(Long adminId, Integer days, Integer count);

    /** 兑换卡密：标记已使用并给用户续费超级会员 */
    void redeem(Long userId, String code);

    /** 后台列出全部卡密 */
    List<RedemptionCodeResponse> list();
}
