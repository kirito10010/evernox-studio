package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织成员导入——职务替换候选（名字匹配但职务不一致）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberImportUpdateCandidate {

    /** 组织成员 ID */
    private Long memberId;

    /** 玩家名 */
    private String name;

    /** 组织内当前职务 */
    private String oldPosition;

    /** Excel 中的新职务 */
    private String newPosition;
}
