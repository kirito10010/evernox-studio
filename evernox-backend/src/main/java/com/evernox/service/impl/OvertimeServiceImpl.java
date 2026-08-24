package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.OvertimeRequest;
import com.evernox.dto.OvertimeResponse;
import com.evernox.entity.PerformanceOvertime;
import com.evernox.exception.BusinessException;
import com.evernox.repository.PerformanceOvertimeRepository;
import com.evernox.service.OvertimeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 加班记录服务实现（纯私有，严格按 userId 隔离）
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class OvertimeServiceImpl implements OvertimeService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final BigDecimal HOURS_PER_DAY = BigDecimal.valueOf(8);

    private final PerformanceOvertimeRepository overtimeRepository;

    @Override
    @Transactional
    public OvertimeResponse createOvertime(OvertimeRequest request, Long userId) {
        BigDecimal days = request.getOvertimeHours().divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP);
        PerformanceOvertime o = PerformanceOvertime.builder()
                .userId(userId)
                .workDate(request.getWorkDate())
                .overtimeHours(request.getOvertimeHours())
                .overtimeDays(days)
                .deleted(0)
                .build();
        overtimeRepository.insert(o);
        return OvertimeResponse.from(o);
    }

    @Override
    @Transactional
    public OvertimeResponse updateOvertime(Long id, OvertimeRequest request, Long userId) {
        PerformanceOvertime o = requireOwned(id, userId);
        BigDecimal days = request.getOvertimeHours().divide(HOURS_PER_DAY, 4, RoundingMode.HALF_UP);
        o.setWorkDate(request.getWorkDate());
        o.setOvertimeHours(request.getOvertimeHours());
        o.setOvertimeDays(days);
        overtimeRepository.updateById(o);
        return OvertimeResponse.from(o);
    }

    @Override
    public IPage<OvertimeResponse> listOvertime(Long userId, LocalDate startDate, LocalDate endDate, int page, int size) {
        LambdaQueryWrapper<PerformanceOvertime> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PerformanceOvertime::getUserId, userId)
                .ge(startDate != null, PerformanceOvertime::getWorkDate, startDate)
                .le(endDate != null, PerformanceOvertime::getWorkDate, endDate)
                .orderByDesc(PerformanceOvertime::getWorkDate)
                .orderByDesc(PerformanceOvertime::getId);

        IPage<PerformanceOvertime> raw = overtimeRepository.selectPage(newPage(page, size), wrapper);
        Page<OvertimeResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(OvertimeResponse::from).collect(Collectors.toList()));
        return result;
    }

    @Override
    @Transactional
    public void deleteOvertime(Long id, Long userId) {
        requireOwned(id, userId);
        overtimeRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDeleteOvertime(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的记录");
        }
        List<Long> distinct = ids.stream().distinct().collect(Collectors.toList());
        Long owned = overtimeRepository.selectCount(new LambdaQueryWrapper<PerformanceOvertime>()
                .eq(PerformanceOvertime::getUserId, userId)
                .in(PerformanceOvertime::getId, distinct));
        if (owned == null || owned.longValue() != distinct.size()) {
            throw new BusinessException(403, "包含无权操作的记录");
        }
        overtimeRepository.deleteBatchIds(distinct);
    }

    private PerformanceOvertime requireOwned(Long id, Long userId) {
        PerformanceOvertime o = overtimeRepository.selectById(id);
        if (o == null) {
            throw new BusinessException("加班记录不存在");
        }
        if (!o.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该加班记录");
        }
        return o;
    }

    private Page<PerformanceOvertime> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
