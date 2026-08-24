package com.evernox.service.impl;

import com.evernox.common.ResultCode;
import com.evernox.dto.PasswordResetConfirmRequest;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.security.Argon2idPasswordEncoder;
import com.evernox.service.PasswordResetService;
import com.evernox.service.UserService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 邮箱找回密码服务实现
 *
 * 验证码采用单实例内存态存储（项目未启用 Redis），与 {@code LoginAttemptService}、
 * JWT 黑名单一致；多实例部署时需换成集中式存储。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    private final UserService userService;
    private final Argon2idPasswordEncoder passwordEncoder;
    private final JavaMailSender mailSender;

    /** 发件人邮箱（即 SMTP 账号） */
    @Value("${spring.mail.username}")
    private String from;

    /** 验证码有效期 */
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    /** 同一邮箱两次发送的最小间隔 */
    private static final Duration SEND_COOLDOWN = Duration.ofSeconds(60);
    /** 验证码连续输错上限 */
    private static final int MAX_VERIFY_ATTEMPTS = 5;

    private static final class ResetEntry {
        String code;
        Instant expiresAt;
        Instant lastSendAt;
        int verifyAttempts;
    }

    private final Map<String, ResetEntry> store = new ConcurrentHashMap<>();

    @Override
    public void sendCode(String email) {
        String key = email.trim().toLowerCase();
        ResetEntry existing = store.get(key);
        if (existing != null && existing.lastSendAt != null
                && Duration.between(existing.lastSendAt, Instant.now()).compareTo(SEND_COOLDOWN) < 0) {
            throw new BusinessException(ResultCode.RESET_CODE_RATE_LIMITED);
        }
        // 防枚举：邮箱不存在也走同一成功路径，不抛错、不发信
        if (!userService.existsByEmail(email)) {
            return;
        }
        String code = String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
        ResetEntry entry = new ResetEntry();
        entry.code = code;
        entry.expiresAt = Instant.now().plus(CODE_TTL);
        entry.lastSendAt = Instant.now();
        entry.verifyAttempts = 0;
        store.put(key, entry);
        sendMail(email, code);
        log.info("已发送密码重置验证码: email={}", email);
    }

    @Override
    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        String key = request.getEmail().trim().toLowerCase();
        ResetEntry entry = store.get(key);
        if (entry == null || entry.expiresAt == null || entry.expiresAt.isBefore(Instant.now())) {
            throw new BusinessException(ResultCode.RESET_CODE_INVALID);
        }
        if (entry.verifyAttempts >= MAX_VERIFY_ATTEMPTS) {
            throw new BusinessException(ResultCode.RESET_CODE_ATTEMPTS_EXCEEDED);
        }
        if (!entry.code.equals(request.getCode())) {
            entry.verifyAttempts++;
            throw new BusinessException(ResultCode.RESET_CODE_INVALID);
        }
        User user = userService.findByEmail(request.getEmail());
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        userService.updatePassword(user.getId(), passwordEncoder.encode(request.getNewPassword()));
        store.remove(key);
        log.info("密码重置成功: userId={}", user.getId());
    }

    private void sendMail(String to, String code) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(Objects.requireNonNull(from));
            helper.setTo(Objects.requireNonNull(to));
            helper.setSubject("【EverNox】密码重置验证码");
            helper.setText("您的验证码为：" + code + "，10 分钟内有效。如非本人操作请忽略本邮件。", false);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("密码重置邮件发送失败: to={}", to, e);
            throw new BusinessException(ResultCode.MAIL_SEND_FAILED);
        }
    }

    /** 定时清理过期条目，避免内存无界增长 */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void cleanup() {
        Instant now = Instant.now();
        store.entrySet().removeIf(e -> e.getValue().expiresAt == null
                || e.getValue().expiresAt.isBefore(now));
    }
}
