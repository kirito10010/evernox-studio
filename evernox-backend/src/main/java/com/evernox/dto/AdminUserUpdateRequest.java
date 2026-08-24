package com.evernox.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员更新用户请求
 *
 * 不含 username：用户名是登录凭据，改名会导致对方无法登录，本模块不支持。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserUpdateRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100字符")
    private String email;

    /** 角色：仅允许 member / super_member，服务层再次校验 */
    @NotBlank(message = "角色不能为空")
    private String role;

    /** 状态：1启用 / 0禁用 */
    @NotNull(message = "状态不能为空")
    private Integer status;

    @NotNull(message = "积分不能为空")
    @Min(value = 0, message = "积分不能为负数")
    private Integer points;

    /** 新密码：留空表示不修改 */
    @Size(min = 8, max = 100, message = "密码长度必须在8-100字符之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "密码必须包含大小写字母和数字")
    private String password;
}
