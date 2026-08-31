package com.evernox.service.impl;

import com.evernox.common.UserRole;
import com.evernox.entity.User;
import com.evernox.entity.UserPointsLog;
import com.evernox.exception.BusinessException;
import com.evernox.repository.UserPointsLogRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 积分与会员服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsServiceImpl implements PointsService {

    private static final int SIGN_IN_POINTS = 10;

    private final UserRepository userRepository;
    private final UserPointsLogRepository pointsLogRepository;

    @Override
    @Transactional
    public void signIn(Long userId) {
        User user = requireUser(userId);
        LocalDate today = LocalDate.now();
        if (user.getLastSigninAt() != null && user.getLastSigninAt().toLocalDate().equals(today)) {
            throw new BusinessException("今日已签到");
        }
        int balance = (user.getPoints() == null ? 0 : user.getPoints()) + SIGN_IN_POINTS;
        user.setPoints(balance);
        user.setLastSigninAt(LocalDateTime.now());
        userRepository.updateById(user);
        insertLog(userId, SIGN_IN_POINTS, balance, "signin", "每日签到", null);
        log.info("每日签到: userId={}, +{}", userId, SIGN_IN_POINTS);
    }

    @Override
    @Transactional
    public void recharge(Long adminId, Long userId, Integer points, String description) {
        if (points == null || points <= 0) {
            throw new BusinessException("充值积分必须大于0");
        }
        User user = requireManageableUser(userId);
        int balance = (user.getPoints() == null ? 0 : user.getPoints()) + points;
        user.setPoints(balance);
        userRepository.updateById(user);
        insertLog(userId, points, balance, "recharge", description, adminId);
        log.info("充值积分: adminId={}, userId={}, +{}", adminId, userId, points);
    }

    @Override
    @Transactional
    public void setSuperMember(Long userId, Integer days) {
        if (days == null || days <= 0) {
            throw new BusinessException("天数必须大于0");
        }
        User user = requireManageableUser(userId);
        extendMembership(user, days);
        userRepository.updateById(user);
        log.info("设置超级会员: userId={}, days={}, expiresAt={}", userId, days, user.getSuperMemberExpiresAt());
    }

    @Override
    @Transactional
    public void upgradeSuperMember(Long userId, Integer days) {
        int cost = pointsForDays(days);
        User user = requireManageableUser(userId);
        int balance = (user.getPoints() == null ? 0 : user.getPoints());
        if (balance < cost) {
            throw new BusinessException("积分不足");
        }
        user.setPoints(balance - cost);
        extendMembership(user, days);
        userRepository.updateById(user);
        insertLog(userId, -cost, user.getPoints(), "upgrade", "开通超级会员", null);
        log.info("积分升级超级会员: userId={}, days={}, cost={}, expiresAt={}",
                userId, days, cost, user.getSuperMemberExpiresAt());
    }

    /** 续费累加：有未到期时长则叠加，否则从当前时间开始 */
    private void extendMembership(User user, int days) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime base = (user.getSuperMemberExpiresAt() != null
                && user.getSuperMemberExpiresAt().isAfter(now))
                ? user.getSuperMemberExpiresAt()
                : now;
        user.setRole(UserRole.SUPER_MEMBER);
        user.setSuperMemberExpiresAt(base.plusDays(days));
    }

    private int pointsForDays(Integer days) {
        if (days == null) {
            throw new BusinessException("天数不能为空");
        }
        return switch (days) {
            case 7 -> 150;
            case 30 -> 600;
            case 60 -> 1200;
            case 90 -> 1800;
            case 365 -> 7200;
            default -> throw new BusinessException("不支持的会员时长");
        };
    }

    private User requireUser(Long userId) {
        User user = userRepository.selectById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        return user;
    }

    private User requireManageableUser(Long userId) {
        User user = requireUser(userId);
        if (UserRole.ADMIN.equals(user.getRole())) {
            throw new BusinessException("不允许操作管理员账号");
        }
        return user;
    }

    private void insertLog(Long userId, int amount, int balance, String type, String description, Long operatorId) {
        pointsLogRepository.insert(UserPointsLog.builder()
                .userId(userId)
                .amount(amount)
                .balance(balance)
                .type(type)
                .description(description)
                .createdBy(operatorId)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
