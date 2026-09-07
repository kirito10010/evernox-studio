package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织成员导入候选（名字 + 职务）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberImportCandidate {

    private String name;

    private String position;
}
