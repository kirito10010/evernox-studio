package com.evernox.service;

import com.evernox.entity.OrgPointsConfig;

/**
 * 组织积分换算比配置服务
 */
public interface OrgPointsConfigService {

    /** 获取指定组织的换算比配置（不存在则创建默认） */
    OrgPointsConfig get(Long organizationId);

    /** 保存指定组织的换算比配置 */
    OrgPointsConfig save(Long organizationId, OrgPointsConfig config);
}
