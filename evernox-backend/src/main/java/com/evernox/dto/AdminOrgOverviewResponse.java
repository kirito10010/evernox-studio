package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员组织总览：所有账号创建的组织、创建者与已加入的平台用户
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminOrgOverviewResponse {

    private Long id;
    private String name;
    private Long ownerId;
    private String ownerUsername;
    private String ownerEmail;
    /** admin / super_member / member */
    private String ownerRole;
    private LocalDateTime createdAt;

    /** 已加入的平台用户数 */
    private int joinedUserCount;
    /** 游戏内成员数（org_member 在组织） */
    private int memberCount;

    private List<JoinedUser> users;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JoinedUser {
        private Long userId;
        private String username;
        private String email;
        private LocalDateTime appliedAt;
        private LocalDateTime reviewedAt;
    }
}
