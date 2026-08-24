package com.evernox.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败计数与锁定
 *
 * 单实例内存态实现（项目未启用 Redis）：多实例部署时各实例计数独立，
 * 需要换成集中式存储才能真正生效。
 *
 * 计数键刻意包含客户端 IP：只用用户名做键的话，攻击者可以靠故意输错密码
 * 把任意账号锁死，等于送了个拒绝服务入口。
 */
@Slf4j
@Component
public class LoginAttemptService {

    /** 连续失败多少次后锁定 */
    private static final int MAX_ATTEMPTS = 5;

    /** 锁定时长 */
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static final class Attempt {
        int count;
        Instant lockedUntil;
        Instant lastFailure;
    }

    private String key(String username, String clientIp) {
        return (username == null ? "" : username.trim().toLowerCase()) + "@" + (clientIp == null ? "-" : clientIp);
    }

    /**
     * 剩余锁定分钟数；未锁定返回 0
     */
    public long remainingLockMinutes(String username, String clientIp) {
        Attempt attempt = attempts.get(key(username, clientIp));
        if (attempt == null || attempt.lockedUntil == null) {
            return 0;
        }
        Duration left = Duration.between(Instant.now(), attempt.lockedUntil);
        if (left.isNegative() || left.isZero()) {
            return 0;
        }
        // 向上取整，避免显示「剩余 0 分钟」却仍被拒绝
        return left.toMinutes() + 1;
    }

    public boolean isLocked(String username, String clientIp) {
        return remainingLockMinutes(username, clientIp) > 0;
    }

    public void recordFailure(String username, String clientIp) {
        String k = key(username, clientIp);
        attempts.compute(k, (ignored, existing) -> {
            Attempt attempt = existing != null ? existing : new Attempt();
            attempt.count++;
            attempt.lastFailure = Instant.now();
            if (attempt.count >= MAX_ATTEMPTS) {
                attempt.lockedUntil = Instant.now().plus(LOCK_DURATION);
                attempt.count = 0;
                log.warn("登录失败次数过多，已锁定: username={}, ip={}", username, clientIp);
            }
            return attempt;
        });
    }

    public void recordSuccess(String username, String clientIp) {
        attempts.remove(key(username, clientIp));
    }

    /** 定时清理：过期锁定与长时间无活动的计数，避免内存无界增长 */
    @Scheduled(fixedDelay = 10 * 60 * 1000L)
    public void cleanup() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> {
            Attempt attempt = entry.getValue();
            boolean lockExpired = attempt.lockedUntil == null || attempt.lockedUntil.isBefore(now);
            boolean idle = attempt.lastFailure == null
                    || attempt.lastFailure.isBefore(now.minus(LOCK_DURATION));
            return lockExpired && idle;
        });
    }
}
