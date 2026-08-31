package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.RedemptionCodeResponse;
import com.evernox.entity.RedemptionCode;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.RedemptionCodeRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.PointsService;
import com.evernox.service.RedemptionCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 超级会员卡密服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class RedemptionCodeServiceImpl implements RedemptionCodeService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RedemptionCodeRepository redemptionCodeRepository;
    private final UserRepository userRepository;
    private final PointsService pointsService;

    @Override
    @Transactional
    public List<RedemptionCodeResponse> generate(Long adminId, Integer days, Integer count) {
        if (days == null || (days != 7 && days != 30)) {
            throw new BusinessException("时长只能是7天或30天");
        }
        int n = count == null ? 1 : count;
        if (n < 1 || n > 100) {
            throw new BusinessException("生成数量需在1-100之间");
        }
        List<RedemptionCodeResponse> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < n; i++) {
            String code = uniqueCode();
            RedemptionCode rc = RedemptionCode.builder()
                    .code(code)
                    .days(days)
                    .status(0)
                    .createdBy(adminId)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            redemptionCodeRepository.insert(rc);
            result.add(RedemptionCodeResponse.builder()
                    .id(rc.getId())
                    .code(rc.getCode())
                    .days(rc.getDays())
                    .status(0)
                    .createdAt(now)
                    .build());
        }
        log.info("生成卡密: adminId={}, days={}, count={}", adminId, days, n);
        return result;
    }

    @Override
    @Transactional
    public void redeem(Long userId, String code) {
        String normalized = normalize(code);
        if (normalized.isEmpty()) {
            throw new BusinessException("请输入卡密");
        }
        RedemptionCode rc = redemptionCodeRepository.selectOne(new LambdaQueryWrapper<RedemptionCode>()
                .eq(RedemptionCode::getCode, normalized));
        if (rc == null) {
            throw new BusinessException("卡密不存在");
        }
        if (Integer.valueOf(1).equals(rc.getStatus())) {
            throw new BusinessException("卡密已被使用");
        }
        rc.setStatus(1);
        rc.setUsedBy(userId);
        rc.setUsedAt(LocalDateTime.now());
        redemptionCodeRepository.updateById(rc);
        pointsService.setSuperMember(userId, rc.getDays());
        log.info("兑换卡密: userId={}, days={}", userId, rc.getDays());
    }

    @Override
    public List<RedemptionCodeResponse> list() {
        List<RedemptionCode> codes = redemptionCodeRepository.selectList(new LambdaQueryWrapper<RedemptionCode>()
                .orderByDesc(RedemptionCode::getId));
        Map<Long, String> usernames = new HashMap<>();
        List<RedemptionCodeResponse> result = new ArrayList<>();
        for (RedemptionCode rc : codes) {
            String username = null;
            if (rc.getUsedBy() != null) {
                username = usernames.computeIfAbsent(rc.getUsedBy(), uid -> {
                    User u = userRepository.selectById(uid);
                    return u == null ? null : u.getUsername();
                });
            }
            result.add(RedemptionCodeResponse.builder()
                    .id(rc.getId())
                    .code(rc.getCode())
                    .days(rc.getDays())
                    .status(rc.getStatus())
                    .usedBy(rc.getUsedBy())
                    .username(username)
                    .usedAt(rc.getUsedAt())
                    .createdAt(rc.getCreatedAt())
                    .build());
        }
        return result;
    }

    private String uniqueCode() {
        for (int i = 0; i < 5; i++) {
            String code = randomCode();
            Long exists = redemptionCodeRepository.selectCount(new LambdaQueryWrapper<RedemptionCode>()
                    .eq(RedemptionCode::getCode, code));
            if (exists == null || exists == 0) {
                return code;
            }
        }
        throw new BusinessException("卡密生成失败，请重试");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }
}
