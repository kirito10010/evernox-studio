package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 组织加入申请（管理员审批视角）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMembershipApplicationResponse {

    private Long id;
    private Long organizationId;
    private String organizationName;
    private Long userId;
    private String username;
    private String email;
    private LocalDateTime appliedAt;
}
