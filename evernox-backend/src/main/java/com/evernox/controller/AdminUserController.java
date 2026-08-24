package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.AdminUserCreateRequest;
import com.evernox.dto.AdminUserUpdateRequest;
import com.evernox.dto.UserInfoResponse;
import com.evernox.dto.UserOptionResponse;
import com.evernox.dto.UserStatsResponse;
import com.evernox.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员用户管理控制器
 *
 * 类级 @PreAuthorize 是唯一可信的权限防线；前端菜单与路由守卫仅影响体验。
 * JwtAuthenticationFilter 已把角色写为 ROLE_{role} 授权，故 hasRole('admin') 直接可用。
 */
@RestController
@RequestMapping("/admin/user")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    /**
     * 分页查询用户（不含管理员）
     */
    @GetMapping("/list")
    public Result<IPage<UserInfoResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(adminUserService.listUsers(
                page, size, keyword, role, status, startDate, endDate, sortField, sortOrder));
    }

    /**
     * 用户统计
     */
    @GetMapping("/stats")
    public Result<UserStatsResponse> stats() {
        return Result.success(adminUserService.getStats());
    }

    /**
     * 筛选下拉用的用户清单（含管理员本人）
     */
    @GetMapping("/options")
    public Result<List<UserOptionResponse>> options() {
        return Result.success(adminUserService.listUserOptions());
    }

    /**
     * 用户详情
     */
    @GetMapping("/{id}")
    public Result<UserInfoResponse> detail(@PathVariable Long id) {
        return Result.success(adminUserService.getUser(id));
    }

    /**
     * 创建用户
     */
    @PostMapping
    public Result<UserInfoResponse> create(@Valid @RequestBody AdminUserCreateRequest request) {
        return Result.success(adminUserService.createUser(request));
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public Result<UserInfoResponse> update(@PathVariable Long id,
                                           @Valid @RequestBody AdminUserUpdateRequest request) {
        return Result.success(adminUserService.updateUser(id, request));
    }

    /**
     * 启用/禁用
     */
    @PutMapping("/{id}/status")
    public Result<UserInfoResponse> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        return Result.success(adminUserService.updateStatus(id, status));
    }

    /**
     * 重置密码
     */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequest request) {
        adminUserService.resetPassword(id, request.getPassword());
        return Result.success();
    }

    /**
     * 删除用户（级联清理其图片、相册与磁盘文件，不可恢复）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminUserService.deleteUser(id);
        return Result.success();
    }

    /**
     * 批量删除
     */
    @DeleteMapping("/batch")
    public Result<Void> deleteBatch(@RequestParam List<Long> ids) {
        adminUserService.deleteUsers(ids);
        return Result.success();
    }

    /**
     * 重置密码请求体
     */
    @Data
    public static class ResetPasswordRequest {
        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 100, message = "密码长度必须在8-100字符之间")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", message = "密码必须包含大小写字母和数字")
        private String password;
    }
}
