package com.evernox.dto;

import com.evernox.entity.OrgMember;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 组织成员响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberResponse {

    private Long id;
    private Long organizationId;
    private String organizationName;
    private String name;
    private String position;
    private Integer status;
    private LocalDateTime createdAt;

    public static OrgMemberResponse from(OrgMember m) {
        return OrgMemberResponse.builder()
                .id(m.getId())
                .organizationId(m.getOrganizationId())
                .name(m.getName())
                .position(m.getPosition())
                .status(m.getStatus())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
