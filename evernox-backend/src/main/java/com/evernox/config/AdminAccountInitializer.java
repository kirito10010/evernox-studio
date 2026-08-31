package com.evernox.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.common.UserRole;
import com.evernox.entity.User;
import com.evernox.repository.UserRepository;
import com.evernox.security.Argon2idPasswordEncoder;
import com.evernox.service.SalaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 启动时自动注册/同步管理员账号
 *
 * 根据 evernox.admin.username/password/email 确保管理员账号存在：
 * - 未配置时跳过；
 * - 不存在则创建（role=admin）；
 * - 已存在则同步角色/状态/邮箱并重置密码哈希。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminAccountInitializer implements ApplicationRunner {

    private static final int STATUS_ACTIVE = 1;

    private final AdminProperties adminProperties;
    private final UserRepository userRepository;
    private final Argon2idPasswordEncoder passwordEncoder;
    private final SalaryService salaryService;

    @Override
    @SuppressWarnings("null")
    public void run(ApplicationArguments args) {
        String username = trimToNull(adminProperties.getUsername());
        String password = adminProperties.getPassword();
        if (username == null || !StringUtils.hasText(password)) {
            log.warn("未配置管理员账号（evernox.admin.username/password 为空），跳过自动注册");
            return;
        }

        User existing = userRepository.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        if (existing == null) {
            createAdmin(username, password);
        } else {
            syncAdmin(existing, password);
        }
    }

    @SuppressWarnings("null")
    private void createAdmin(String username, String password) {
        String email = trimToNull(adminProperties.getEmail());
        if (email == null) {
            email = username + "@example.com";
        }
        Long emailCount = userRepository.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
        if (emailCount != null && emailCount > 0) {
            log.error("管理员邮箱已被占用，跳过自动注册: email={}", email);
            return;
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(password))
                .email(email)
                .role(UserRole.ADMIN)
                .status(STATUS_ACTIVE)
                .points(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        userRepository.insert(user);
        salaryService.createDefault(user.getId());
        log.info("已自动创建管理员账号: userId={}, username={}", user.getId(), username);
    }

    private void syncAdmin(User existing, String password) {
        existing.setRole(UserRole.ADMIN);
        existing.setStatus(STATUS_ACTIVE);
        String email = trimToNull(adminProperties.getEmail());
        if (email != null) {
            existing.setEmail(email);
        }
        existing.setPassword(passwordEncoder.encode(password));
        userRepository.updateById(existing);
        log.info("已同步管理员账号: userId={}, username={}", existing.getId(), existing.getUsername());
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
