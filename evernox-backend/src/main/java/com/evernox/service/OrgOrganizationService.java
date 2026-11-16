package com.evernox.service;

import com.evernox.dto.OrgOrganizationRequest;
import com.evernox.dto.OrgOrganizationResponse;

import java.util.List;

/**
 * 组织服务
 */
public interface OrgOrganizationService {

    /** 管理端：只返回当前账号创建的组织 */
    List<OrgOrganizationResponse> list(Long ownerId);

    /** 公开看板：所有组织（用户用来申请加入） */
    List<OrgOrganizationResponse> listAll();

    OrgOrganizationResponse create(OrgOrganizationRequest request, Long ownerId);

    /** 仅组织创建者可改 */
    OrgOrganizationResponse update(Long id, OrgOrganizationRequest request, Long ownerId);

    /** 仅组织创建者可删 */
    void delete(Long id, Long ownerId);
}
