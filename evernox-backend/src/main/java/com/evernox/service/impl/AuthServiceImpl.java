package com.evernox.service.impl;

import com.evernox.common.ResultCode;
import com.evernox.common.UserRole;
import com.evernox.dto.AuthResponse;
import com.evernox.dto.LoginRequest;
import com.evernox.dto.RegisterRequest;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.UserRepository;
import com.evernox.security.Argon2idPasswordEncoder;
import com.evernox.security.JwtTokenProvider;
import com.evernox.security.LoginAttemptService;
import com.evernox.service.AuthService;
import com.evernox.service.SalaryService;
import com.evernox.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 认证服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final SalaryService salaryService;
    private final Argon2idPasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final LoginAttemptService loginAttemptService;

    /**
     * 用户状态: 激活
     */
    private static final int STATUS_ACTIVE = 1;

    /**
     * 用户状态: 禁用
     */
    private static final int STATUS_DISABLED = 0;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        log.info("用户注册: username={}, email={}", request.getUsername(), request.getEmail());

        // 检查用户名是否已存在
        if (userService.existsByUsername(request.getUsername())) {
            throw new BusinessException(ResultCode.USERNAME_ALREADY_EXISTS);
        }

        // 检查邮箱是否已存在
        if (userService.existsByEmail(request.getEmail())) {
            throw new BusinessException(ResultCode.EMAIL_ALREADY_EXISTS);
        }

        // 创建用户
        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(UserRole.MEMBER) // 默认角色为普通成员
                .status(STATUS_ACTIVE) // 默认状态为激活
                .points(0) // 初始积分为0
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        userRepository.insert(user);
        // 注册即生成默认工资配置
        salaryService.createDefault(user.getId());
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request, String clientIp) {
        log.info("用户登录: username={}", request.getUsername());

        long lockMinutes = loginAttemptService.remainingLockMinutes(request.getUsername(), clientIp);
        if (lockMinutes > 0) {
            throw new BusinessException(ResultCode.LOGIN_ERROR,
                    "登录失败次数过多，请在 " + lockMinutes + " 分钟后重试");
        }

        // 查询用户
        User user = userService.findByUsername(request.getUsername());
        // 用户不存在与密码错误必须返回同一句文案：区分开等于提供了用户名枚举接口，
        // 攻击者可以先跑字典筛出真实账号，再集中爆破
        if (user == null) {
            loginAttemptService.recordFailure(request.getUsername(), clientIp);
            throw new BusinessException(ResultCode.LOGIN_ERROR, "用户名或密码错误");
        }

        // 检查账号状态
        // 这里保留独立提示：虽然会暴露「该用户名存在」，但被封的用户否则会一直
        // 以为是自己记错密码。取舍上优先可用性。
        if (STATUS_DISABLED == user.getStatus()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 验证密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(request.getUsername(), clientIp);
            throw new BusinessException(ResultCode.LOGIN_ERROR, "用户名或密码错误");
        }

        loginAttemptService.recordSuccess(request.getUsername(), clientIp);

        // 更新最后登录时间
        userService.updateLastLoginTime(user.getId());

        // 生成Token
        AuthResponse response = jwtTokenProvider.generateAuthResponse(user);
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());

        return response;
    }

    @Override
    public AuthResponse refreshToken(String refreshToken) {
        log.info("刷新Token");

        // 验证RefreshToken
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BusinessException(ResultCode.TOKEN_INVALID, "RefreshToken无效");
        }

        // 解析Token获取用户信息
        Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

        // 查询用户
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 检查账号状态
        if (STATUS_DISABLED == user.getStatus()) {
            throw new BusinessException(ResultCode.USER_DISABLED);
        }

        // 生成新的Token
        AuthResponse response = jwtTokenProvider.generateAuthResponse(user);

        // 旧 refresh token 立即作废：不轮换的话，一个泄露的 refresh token
        // 在整个有效期（7 天）内都能不断换出新的 access token
        jwtTokenProvider.invalidateToken(refreshToken);

        log.info("Token刷新成功: userId={}, username={}", user.getId(), user.getUsername());

        return response;
    }

    @Override
    public void logout(String token) {
        log.info("用户登出");

        // 将Token加入黑名单
        jwtTokenProvider.invalidateToken(token);
        log.info("用户登出成功");
    }
}
