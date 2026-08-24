package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.HyolNinjaResponse;
import com.evernox.dto.HyolRefreshResponse;

/**
 * 火影忍者OL忍者图鉴服务接口
 */
public interface HyolNinjaService {

    /** 从官网抓取并更新忍者/技能缓存（增量） */
    HyolRefreshResponse refresh();

    /** 分页列表（首次访问空表自动全量抓取；keyword 模糊匹配名称/昵称，attr 精确筛选属性，技能维度筛选命中任一技能槽） */
    IPage<HyolNinjaResponse> list(int page, int size, String keyword, String attr,
                                  String hurtType, String chaseStatus, String hurtStatus, String rare);
}
