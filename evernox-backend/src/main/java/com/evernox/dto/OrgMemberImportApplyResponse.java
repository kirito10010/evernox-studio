package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织成员 Excel 导入确认应用结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberImportApplyResponse {

    /** 成功新增的成员名 */
    private List<String> addedNames;

    /** 成功替换职务的成员名 */
    private List<String> updatedNames;

    /** 恢复为「在组织」的成员名 */
    private List<String> restoredNames;

    /** 置为「离开组织」的成员名 */
    private List<String> leftNames;

    /** 因重复/无效被跳过的成员名 */
    private List<String> skippedNames;
}
