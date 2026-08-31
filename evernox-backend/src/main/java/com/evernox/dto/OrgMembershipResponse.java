package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织成员关系（当前用户视角）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMembershipResponse {

    private Long id;
    private Long organizationId;
    private String organizationName;
    /** 状态: 0待审批/1已加入/2已拒绝 */
    private Integer status;
}
