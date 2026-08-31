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
    private LocalDateTime createdAt;

    public static OrgOrganizationResponse from(OrgOrganization o) {
        return OrgOrganizationResponse.builder()
                .id(o.getId())
                .name(o.getName())
                .createdAt(o.getCreatedAt())
                .build();
    }
}
