package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织成员 Excel 导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberImportResponse {

    /** 成功导入（新增）的成员名 */
    private List<String> importedNames;

    /** 未导入的成员名（已存在/重复/超长） */
    private List<String> skippedNames;
}
