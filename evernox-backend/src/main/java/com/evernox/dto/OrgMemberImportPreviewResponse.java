package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织成员 Excel 导入差异预览
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberImportPreviewResponse {

    /** 新增候选（Excel 有、组织无） */
    private List<OrgMemberImportCandidate> toAdd;

    /** 无变动（两边都存在，且当前在组织）的成员名 */
    private List<String> unchangedNames;

    /** 恢复候选（组织已离开成员、Excel 再次出现，可恢复为在组织） */
    private List<OrgMemberResponse> toRestore;

    /** 职务替换候选（名字匹配但职务不一致） */
    private List<OrgMemberImportUpdateCandidate> toUpdate;

    /** 离开候选（组织在组织成员、Excel 未匹配） */
    private List<OrgMemberResponse> toLeave;
}
