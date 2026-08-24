package com.evernox.service;

import com.evernox.dto.AuthResponse;
import com.evernox.dto.LoginRequest;
import com.evernox.dto.RegisterRequest;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户注册
     */
    void register(RegisterRequest request);

    /**
     * 用户登录
     *
     * @param clientIp 客户端 IP，用于登录失败计数与锁定
     */
    AuthResponse login(LoginRequest request, String clientIp);

    /**
     * 刷新Token
     */
    AuthResponse refreshToken(String refreshToken);

    /**
     * 登出
     */
    void logout(String token);
}
