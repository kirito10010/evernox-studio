package com.evernox.service;

import com.evernox.dto.OrgMemberRequest;
import com.evernox.dto.OrgMemberResponse;

import java.util.List;

/**
 * 组织成员服务
 */
public interface OrgMemberService {

    List<OrgMemberResponse> list();

    OrgMemberResponse create(OrgMemberRequest request);

    OrgMemberResponse update(Long id, OrgMemberRequest request);

    /** 切换成员状态：1在组织/0已离开（假删除） */
    void updateStatus(Long id, Integer status);
}
