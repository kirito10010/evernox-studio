package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.common.UserRole;
import com.evernox.dto.AuthResponse;
import com.evernox.dto.LoginRequest;
import com.evernox.dto.PasswordResetConfirmRequest;
import com.evernox.dto.PasswordResetSendCodeRequest;
import com.evernox.dto.RegisterRequest;
import com.evernox.dto.UserInfoResponse;
import com.evernox.entity.User;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.AuthService;
import com.evernox.service.PasswordResetService;
import com.evernox.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 用户注册
     */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success("注册成功", null);
    }

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        AuthResponse response = authService.login(request, resolveClientIp(servletRequest));
        return Result.success("登录成功", response);
    }

    /**
     * 发送密码重置验证码
     */
    @PostMapping("/password-reset/send-code")
    public Result<Void> sendPasswordResetCode(@Valid @RequestBody PasswordResetSendCodeRequest request) {
        passwordResetService.sendCode(request.getEmail());
        return Result.success("验证码已发送（若该邮箱已注册）", null);
    }

    /**
     * 校验验证码并重置密码
     */
    @PostMapping("/password-reset/confirm")
    public Result<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request);
        return Result.success("密码重置成功", null);
    }

    /**
     * 取客户端 IP：反向代理场景优先取 X-Forwarded-For 的首段
     */
    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserInfoResponse> getCurrentUser(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return Result.fail(401, "未授权");
        }
        
        String token = authorization.substring(7);
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        User user = userService.getById(userId);
        
        if (user == null) {
            return Result.fail(404, "用户不存在");
        }
        
        UserInfoResponse resp = UserInfoResponse.from(user);
        resp.setRole(UserRole.effective(user.getRole(), user.getSuperMemberExpiresAt()));
        return Result.success(resp);
    }

    /**
     * 刷新Token
     */
    @PostMapping("/refresh")
    public Result<AuthResponse> refreshToken(@RequestHeader("Refresh-Token") String refreshToken) {
        AuthResponse response = authService.refreshToken(refreshToken);
        return Result.success("Token刷新成功", response);
    }

    /**
     * 登出
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            authService.logout(token);
        }
        return Result.success("登出成功", null);
    }
}
