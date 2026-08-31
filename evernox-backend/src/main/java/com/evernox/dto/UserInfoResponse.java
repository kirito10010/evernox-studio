package com.evernox.dto;

import com.evernox.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户信息响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoResponse {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 角色: admin/super_member/member
     */
    private String role;

    /**
     * 状态: 1激活/0禁用
     */
    private Integer status;

    /**
     * 积分
     */
    private Integer points;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 超级会员到期时间
     */
    private LocalDateTime superMemberExpiresAt;

    /**
     * 上次签到时间
     */
    private LocalDateTime lastSigninAt;

    /**
     * 从 User 实体转换
     */
    public static UserInfoResponse from(User user) {
        if (user == null) {
            return null;
        }
        
        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole())
                .status(user.getStatus())
                .points(user.getPoints())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .superMemberExpiresAt(user.getSuperMemberExpiresAt())
                .lastSigninAt(user.getLastSigninAt())
                .build();
    }
}
