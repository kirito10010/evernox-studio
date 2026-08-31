package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织积分 Excel 导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgImportResponse {

    /** 成功导入的成员名 */
    private List<String> importedNames;

    /** 未导入的成员名（Excel 里有、组织里没有） */
    private List<String> unmatchedNames;

    /** 导入后仍为空数据的成员名 */
    private List<String> emptyNames;
}
