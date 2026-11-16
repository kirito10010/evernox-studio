package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 组织成员批量操作结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberBatchResponse {

    /** 实际处理的成员数量 */
    private int affected;

    /** 被处理的成员玩家名，便于前端提示 */
    private List<String> names;
}
