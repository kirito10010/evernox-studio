package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织成员 Excel 导入确认应用请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberImportApplyRequest {

    private Long organizationId;

    /** 勾选要新增的成员 */
    private List<OrgMemberImportCandidate> add;

    /** 勾选要替换职务的成员 */
    private List<OrgMemberImportUpdateCandidate> updates;

    /** 勾选要恢复为「在组织」的成员 ID */
    private List<Long> restoreIds;

    /** 勾选要置为「离开组织」的成员 ID */
    private List<Long> leaveIds;
}
