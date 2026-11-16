package com.evernox.service;

import com.evernox.dto.AdminOrgOverviewResponse;

import java.util.List;

/**
 * 管理员组织总览（仅管理员）
 */
public interface AdminOrgOverviewService {

    /** 所有账号创建的组织 + 创建者 + 已加入的平台用户 */
    List<AdminOrgOverviewResponse> listAll();
}
