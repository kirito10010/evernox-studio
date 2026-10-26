package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 叛忍车头 txt 导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgRenegadeLeaderImportResponse {

    /** 是否全部匹配（只有 true 时才真正写入） */
    private boolean allMatched;

    /** 文件中的有效行数（忽略空行） */
    private int totalLines;

    /** 匹配到的行数 */
    private int matchedCount;

    /** 未匹配的行数 */
    private int unmatchedCount;

    /** 实际标记为车头的成员数 */
    private int importedCount;

    private List<UnmatchedItem> unmatchedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UnmatchedItem {
        /** 文件中的行号（从 1 开始） */
        private int line;
        private String name;
    }
}
