package com.evernox.service;

import com.evernox.dto.OrgDistributionPlanRequest;
import com.evernox.dto.OrgDistributionPlanResponse;
import com.evernox.dto.OrgDistributionRunResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 组织一键发包方案服务
 */
public interface OrgDistributionService {

    /** 组织下的全部方案（含礼包队列与必发玩家） */
    List<OrgDistributionPlanResponse> list(Long organizationId);

    /** 新建方案 */
    OrgDistributionPlanResponse create(Long organizationId, OrgDistributionPlanRequest request);

    /** 修改方案（整体覆盖队列与必发） */
    OrgDistributionPlanResponse update(Long id, OrgDistributionPlanRequest request);

    /** 删除方案 */
    void delete(Long id);

    /** 按方案执行一键发包 */
    OrgDistributionRunResponse run(Long id, LocalDate weekDate);
}
