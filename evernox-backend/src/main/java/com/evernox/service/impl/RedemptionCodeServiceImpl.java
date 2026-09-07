package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.ResultCode;
import com.evernox.dto.RedemptionCodeResponse;
import com.evernox.entity.RedemptionCode;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.RedemptionCodeRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.PointsService;
import com.evernox.service.RedemptionCodeService;
import com.evernox.util.SortColumnResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    /** 排序字段白名单：前端传入的字符串绝不能直接拼进 SQL */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "created_at",
            "usedAt", "used_at",
            "days", "days"
    );

    private static final int MAX_PAGE_SIZE = 100;

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
    public IPage<RedemptionCodeResponse> list(int page, int size, String keyword, Integer days, Integer status,
                                              String username, String startDate, String endDate,
                                              String sortField, String sortOrder) {
        QueryWrapper<RedemptionCode> wrapper = new QueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            wrapper.like("code", keyword.trim());
        }
        if (days != null) {
            wrapper.eq("days", days);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (StringUtils.hasText(username)) {
            List<Long> uids = userRepository.selectList(
                            new LambdaQueryWrapper<User>().like(User::getUsername, username.trim()))
                    .stream()
                    .map(User::getId)
                    .toList();
            if (uids.isEmpty()) {
                return emptyPage(page, size);
            }
            wrapper.in("used_by", uids);
        }

        LocalDate start = parseDate(startDate);
        if (start != null) {
            wrapper.ge("created_at", start.atStartOfDay());
        }
        LocalDate end = parseDate(endDate);
        if (end != null) {
            // 上界取次日 00:00 开区间，避免漏掉当天带时分秒的记录
            wrapper.lt("created_at", end.plusDays(1).atStartOfDay());
        }

        String column = SortColumnResolver.resolve(SORT_COLUMNS, sortField, "created_at");
        wrapper.orderBy(true, "asc".equalsIgnoreCase(sortOrder), column);

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        IPage<RedemptionCode> result = redemptionCodeRepository.selectPage(
                new Page<>(Math.max(page, 1), safeSize), wrapper);

        // 批量回填用户名，避免逐条查询
        List<Long> usedIds = result.getRecords().stream()
                .map(RedemptionCode::getUsedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> nameMap = new HashMap<>();
        for (Long uid : usedIds) {
            User u = userRepository.selectById(uid);
            if (u != null) {
                nameMap.put(uid, u.getUsername());
            }
        }

        Page<RedemptionCodeResponse> resp = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        resp.setRecords(result.getRecords().stream()
                .map(rc -> toResponse(rc, nameMap))
                .toList());
        return resp;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        RedemptionCode rc = redemptionCodeRepository.selectById(id);
        if (rc == null) {
            throw new BusinessException("卡密不存在");
        }
        redemptionCodeRepository.deleteById(id);
        log.warn("管理员删除卡密: id={}, code={}, status={}", id, rc.getCode(), rc.getStatus());
    }

    @Override
    @Transactional
    public void deleteBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "未选择要删除的卡密");
        }
        int count = redemptionCodeRepository.deleteBatchIds(ids);
        log.warn("管理员批量删除卡密: count={}", count);
    }

    private RedemptionCodeResponse toResponse(RedemptionCode rc, Map<Long, String> nameMap) {
        return RedemptionCodeResponse.builder()
                .id(rc.getId())
                .code(rc.getCode())
                .days(rc.getDays())
                .status(rc.getStatus())
                .usedBy(rc.getUsedBy())
                .username(rc.getUsedBy() == null ? null : nameMap.get(rc.getUsedBy()))
                .usedAt(rc.getUsedAt())
                .createdAt(rc.getCreatedAt())
                .build();
    }

    private IPage<RedemptionCodeResponse> emptyPage(int page, int size) {
        Page<RedemptionCodeResponse> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), 0);
        p.setRecords(List.of());
        return p;
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
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
