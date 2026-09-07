package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
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

    /** 后台分页列出卡密，支持多条件筛选 */
    IPage<RedemptionCodeResponse> list(int page, int size, String keyword, Integer days, Integer status,
                                       String username, String startDate, String endDate,
                                       String sortField, String sortOrder);

    /** 删除单张卡密 */
    void delete(Long id);

    /** 批量删除卡密 */
    void deleteBatch(List<Long> ids);
}
