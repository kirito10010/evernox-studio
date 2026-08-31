package com.evernox.service;

import com.evernox.dto.OrgRewardPackageRequest;
import com.evernox.entity.OrgRewardPackage;

import java.util.List;

/**
 * 组织奖励礼包服务
 */
public interface OrgRewardPackageService {

    List<OrgRewardPackage> list(Long organizationId);

    OrgRewardPackage create(Long organizationId, OrgRewardPackageRequest request);

    OrgRewardPackage update(Long id, OrgRewardPackageRequest request);

    void delete(Long id);
}
