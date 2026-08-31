package com.evernox.service;

import com.evernox.dto.OrgOrganizationRequest;
import com.evernox.dto.OrgOrganizationResponse;

import java.util.List;

/**
 * 组织服务
 */
public interface OrgOrganizationService {

    List<OrgOrganizationResponse> list();

    OrgOrganizationResponse create(OrgOrganizationRequest request, Long ownerId);

    OrgOrganizationResponse update(Long id, OrgOrganizationRequest request);

    void delete(Long id);
}
