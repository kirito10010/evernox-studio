package com.evernox.service;

import com.evernox.dto.OrgMemberBatchResponse;
import com.evernox.dto.OrgMemberRequest;
import com.evernox.dto.OrgMemberResponse;

import java.util.List;

/**
 * 组织成员服务
 */
public interface OrgMemberService {

    /** 管理端：只返回当前账号名下组织的成员 */
    List<OrgMemberResponse> listByOwner(Long ownerId);

    OrgMemberResponse create(OrgMemberRequest request);

    OrgMemberResponse update(Long id, OrgMemberRequest request);

    /** 切换成员状态：1在组织/0已离开（假删除） */
    void updateStatus(Long id, Integer status);

    /** 批量切换状态：1在组织/0已离开 */
    OrgMemberBatchResponse batchUpdateStatus(List<Long> ids, Integer status, Long ownerId);

    /** 批量移入指定组织 */
    OrgMemberBatchResponse batchMoveOrganization(List<Long> ids, Long organizationId, Long ownerId);

    /** 批量设置职务（null/空串 = 清空） */
    OrgMemberBatchResponse batchUpdatePosition(List<Long> ids, String position, Long ownerId);

    /** 批量彻底删除（周记录置空引用、必发玩家同步清理） */
    OrgMemberBatchResponse batchDelete(List<Long> ids, Long ownerId);

    /** 清空某组织全部成员，用于删除组织前的清理 */
    OrgMemberBatchResponse clearOrganizationMembers(Long organizationId, Long ownerId);
}
