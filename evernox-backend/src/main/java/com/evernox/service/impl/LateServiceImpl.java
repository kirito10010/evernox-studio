package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.LateRequest;
import com.evernox.dto.LateResponse;
import com.evernox.entity.PerformanceLate;
import com.evernox.exception.BusinessException;
import com.evernox.repository.PerformanceLateRepository;
import com.evernox.service.LateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 迟到记录服务实现（纯私有，严格按 userId 隔离）
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class LateServiceImpl implements LateService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal MINUTES_PER_DAY = BigDecimal.valueOf(480);

    private final PerformanceLateRepository lateRepository;

    @Override
    @Transactional
    public LateResponse createLate(LateRequest request, Long userId) {
        BigDecimal days = BigDecimal.valueOf(request.getLateMinutes())
                .divide(MINUTES_PER_DAY, 5, RoundingMode.HALF_UP);
        PerformanceLate l = PerformanceLate.builder()
                .userId(userId)
                .workDate(request.getWorkDate())
                .lateMinutes(request.getLateMinutes())
                .lateDays(days)
                .deleted(0)
                .build();
        lateRepository.insert(l);
        return LateResponse.from(l);
    }

    @Override
    public IPage<LateResponse> listLate(Long userId, LocalDate startDate, LocalDate endDate, int page, int size) {
        LambdaQueryWrapper<PerformanceLate> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PerformanceLate::getUserId, userId)
                .ge(startDate != null, PerformanceLate::getWorkDate, startDate)
                .le(endDate != null, PerformanceLate::getWorkDate, endDate)
                .orderByDesc(PerformanceLate::getWorkDate)
                .orderByDesc(PerformanceLate::getId);

        IPage<PerformanceLate> raw = lateRepository.selectPage(newPage(page, size), wrapper);
        Page<LateResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(LateResponse::from).collect(Collectors.toList()));
        return result;
    }

    @Override
    @Transactional
    public void deleteLate(Long id, Long userId) {
        requireOwned(id, userId);
        lateRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDeleteLate(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的记录");
        }
        List<Long> distinct = ids.stream().distinct().collect(Collectors.toList());
        Long owned = lateRepository.selectCount(new LambdaQueryWrapper<PerformanceLate>()
                .eq(PerformanceLate::getUserId, userId)
                .in(PerformanceLate::getId, distinct));
        if (owned == null || owned.longValue() != distinct.size()) {
            throw new BusinessException(403, "包含无权操作的记录");
        }
        lateRepository.deleteBatchIds(distinct);
    }

    private PerformanceLate requireOwned(Long id, Long userId) {
        PerformanceLate l = lateRepository.selectById(id);
        if (l == null) {
            throw new BusinessException("迟到记录不存在");
        }
        if (!l.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该迟到记录");
        }
        return l;
    }

    private Page<PerformanceLate> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
