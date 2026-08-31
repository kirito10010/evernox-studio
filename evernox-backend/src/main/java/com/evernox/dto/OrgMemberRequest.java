package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 组织成员请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgMemberRequest {

    @NotNull(message = "请选择所属组织")
    private Long organizationId;

    @NotBlank(message = "玩家名不能为空")
    @Size(max = 50, message = "玩家名长度不能超过50字符")
    private String name;

    @Size(max = 50, message = "职务长度不能超过50字符")
    private String position;
}
