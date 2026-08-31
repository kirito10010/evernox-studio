package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgOrganizationRequest {

    @NotBlank(message = "组织名称不能为空")
    @Size(max = 50, message = "组织名称长度不能超过50字符")
    private String name;
}
