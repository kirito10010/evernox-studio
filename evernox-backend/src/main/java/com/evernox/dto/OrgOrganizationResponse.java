package com.evernox.dto;

import com.evernox.entity.OrgOrganization;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 组织响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgOrganizationResponse {

    private Long id;
    private String name;
    /** 创建者用户ID：前端据此判断「自己创建的组织无需申请」 */
    private Long ownerId;
    /** 成员数量（含已离开），用于管理弹窗展示与删除前置判断 */
    private Long memberCount;
    private LocalDateTime createdAt;

    public static OrgOrganizationResponse from(OrgOrganization o) {
        return OrgOrganizationResponse.builder()
                .id(o.getId())
                .name(o.getName())
                .ownerId(o.getOwnerId())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
