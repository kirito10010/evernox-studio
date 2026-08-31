package com.evernox.service;

import com.evernox.dto.OrgMembershipApplicationResponse;
import com.evernox.dto.OrgMembershipResponse;

import java.util.List;

/**
 * 组织成员关系（平台用户 ↔ 组织）服务
 */
public interface OrgMembershipService {

    List<OrgMembershipResponse> listMine(Long userId);

    void apply(Long userId, Long organizationId);

    void leave(Long userId, Long organizationId);

    boolean canView(Long userId, Long organizationId);

    List<OrgMembershipApplicationResponse> listApplications(Long adminId);

    void approve(Long adminId, Long id);

    void reject(Long adminId, Long id);
}
