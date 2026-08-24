package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.ExpenseCategoryRequest;
import com.evernox.dto.ExpenseCategoryResponse;
import com.evernox.dto.ExpenseChartResponse;
import com.evernox.dto.ExpenseRecordRequest;
import com.evernox.dto.ExpenseRecordResponse;
import com.evernox.entity.ExpenseCategory;
import com.evernox.entity.ExpenseRecord;
import com.evernox.exception.BusinessException;
import com.evernox.repository.ExpenseCategoryRepository;
import com.evernox.repository.ExpenseRecordRepository;
import com.evernox.service.ExpenseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 记账服务实现（纯私有数据，严格按 userId 隔离）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ExpenseServiceImpl implements ExpenseService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_CHART_DAYS = 366;

    private final ExpenseCategoryRepository categoryRepository;
    private final ExpenseRecordRepository recordRepository;

    // ==================== 消费类型 ====================

    @Override
    @Transactional
    public ExpenseCategoryResponse createCategory(ExpenseCategoryRequest request, Long userId) {
        String name = request.getName().trim();
        ensureNameUnique(name, userId, null);

        ExpenseCategory category = ExpenseCategory.builder()
                .userId(userId)
                .name(name)
                .deleted(0)
                .build();
        categoryRepository.insert(category);
        return ExpenseCategoryResponse.from(category);
    }

    @Override
    public List<ExpenseCategoryResponse> listCategories(Long userId) {
        LambdaQueryWrapper<ExpenseCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExpenseCategory::getUserId, userId)
                .orderByAsc(ExpenseCategory::getId);
        return categoryRepository.selectList(wrapper).stream()
                .map(ExpenseCategoryResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ExpenseCategoryResponse updateCategory(Long id, ExpenseCategoryRequest request, Long userId) {
        ExpenseCategory category = requireOwnedCategory(id, userId);
        String name = request.getName().trim();
        ensureNameUnique(name, userId, id);

        category.setName(name);
        categoryRepository.updateById(category);
        return ExpenseCategoryResponse.from(category);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id, Long userId) {
        requireOwnedCategory(id, userId);
        long referenced = recordRepository.selectCount(new LambdaQueryWrapper<ExpenseRecord>()
                .eq(ExpenseRecord::getUserId, userId)
                .eq(ExpenseRecord::getCategoryId, id));
        if (referenced > 0) {
            throw new BusinessException("该类型下还有消费记录，请先删除相关记录");
        }
        categoryRepository.deleteById(id);
    }

    // ==================== 消费记录 ====================

    @Override
    @Transactional
    public ExpenseRecordResponse createRecord(ExpenseRecordRequest request, Long userId) {
        ExpenseCategory category = requireOwnedCategoryForRecord(request.getCategoryId(), userId);

        ExpenseRecord record = ExpenseRecord.builder()
                .userId(userId)
                .categoryId(category.getId())
                .amount(request.getAmount())
                .remark(normalizeRemark(request.getRemark()))
                .expenseDate(request.getExpenseDate())
                .deleted(0)
                .build();
        recordRepository.insert(record);
        return ExpenseRecordResponse.from(record, category.getName());
    }

    @Override
    @Transactional
    public ExpenseRecordResponse updateRecord(Long id, ExpenseRecordRequest request, Long userId) {
        ExpenseRecord record = requireOwnedRecord(id, userId);
        ExpenseCategory category = requireOwnedCategoryForRecord(request.getCategoryId(), userId);

        record.setCategoryId(category.getId());
        record.setAmount(request.getAmount());
        record.setExpenseDate(request.getExpenseDate());
        record.setRemark(normalizeRemark(request.getRemark()));
        recordRepository.updateById(record);

        // updateById 忽略 null：备注被清空时需显式置空
        if (record.getRemark() == null) {
            recordRepository.update(null, new LambdaUpdateWrapper<ExpenseRecord>()
                    .eq(ExpenseRecord::getId, id)
                    .eq(ExpenseRecord::getUserId, userId)
                    .set(ExpenseRecord::getRemark, null));
        }
        return ExpenseRecordResponse.from(record, category.getName());
    }

    @Override
    @Transactional
    public void deleteRecord(Long id, Long userId) {
        requireOwnedRecord(id, userId);
        recordRepository.deleteById(id);
    }

    @Override
    public IPage<ExpenseRecordResponse> listRecords(Long userId, Long categoryId,
                                                    LocalDate startDate, LocalDate endDate,
                                                    String keyword, int page, int size) {
        String kw = (keyword == null) ? null : keyword.trim();
        LambdaQueryWrapper<ExpenseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExpenseRecord::getUserId, userId)
                .eq(categoryId != null, ExpenseRecord::getCategoryId, categoryId)
                .ge(startDate != null, ExpenseRecord::getExpenseDate, startDate)
                .le(endDate != null, ExpenseRecord::getExpenseDate, endDate)
                .like(kw != null && !kw.isEmpty(), ExpenseRecord::getRemark, kw)
                .orderByDesc(ExpenseRecord::getExpenseDate)
                .orderByDesc(ExpenseRecord::getId);

        IPage<ExpenseRecord> raw = recordRepository.selectPage(newPage(page, size), wrapper);

        Set<Long> categoryIds = raw.getRecords().stream()
                .map(ExpenseRecord::getCategoryId)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = categoryNameMap(categoryIds);

        Page<ExpenseRecordResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream()
                .map(r -> ExpenseRecordResponse.from(r, nameMap.get(r.getCategoryId())))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public List<String> months(Long userId) {
        return recordRepository.selectDistinctMonths(userId);
    }

    @Override
    public ExpenseChartResponse chart(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new BusinessException("请选择开始日期");
        }
        if (endDate == null) {
            endDate = startDate;
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_CHART_DAYS) {
            throw new BusinessException("查询范围过大，最多 " + MAX_CHART_DAYS + " 天");
        }

        // 每日总额
        Map<LocalDate, BigDecimal> dailyTotal = recordRepository
                .selectDailyTotals(userId, startDate, endDate).stream()
                .collect(Collectors.toMap(
                        ExpenseRecordRepository.DailyTotal::getExpenseDate,
                        ExpenseRecordRepository.DailyTotal::getTotal,
                        (a, b) -> a));

        // 范围内全部记录，用于按「日期 → 类型」聚合明细
        LambdaQueryWrapper<ExpenseRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExpenseRecord::getUserId, userId)
                .between(ExpenseRecord::getExpenseDate, startDate, endDate)
                .orderByAsc(ExpenseRecord::getExpenseDate);
        List<ExpenseRecord> records = recordRepository.selectList(wrapper);

        Map<Long, String> nameMap = categoryNameMap(
                records.stream().map(ExpenseRecord::getCategoryId).collect(Collectors.toSet()));

        Map<LocalDate, Map<Long, BigDecimal>> breakdownByDate = new LinkedHashMap<>();
        for (ExpenseRecord r : records) {
            Map<Long, BigDecimal> byCategory = breakdownByDate.computeIfAbsent(
                    r.getExpenseDate(), k -> new LinkedHashMap<>());
            byCategory.merge(r.getCategoryId(), r.getAmount(), BigDecimal::add);
        }

        List<ExpenseChartResponse.Point> points = new ArrayList<>();
        for (LocalDate date : dailyTotal.keySet().stream().sorted().collect(Collectors.toList())) {
            List<ExpenseChartResponse.Breakdown> breakdown = breakdownByDate
                    .getOrDefault(date, Collections.emptyMap()).entrySet().stream()
                    .map(e -> ExpenseChartResponse.Breakdown.builder()
                            .categoryId(e.getKey())
                            .categoryName(nameMap.get(e.getKey()))
                            .amount(e.getValue())
                            .build())
                    .sorted((a, b) -> b.getAmount().compareTo(a.getAmount()))
                    .collect(Collectors.toList());

            points.add(ExpenseChartResponse.Point.builder()
                    .date(date)
                    .total(dailyTotal.get(date))
                    .breakdown(breakdown)
                    .build());
        }

        BigDecimal totalAmount = dailyTotal.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        long count = records.size();

        return ExpenseChartResponse.builder()
                .points(points)
                .totalAmount(totalAmount)
                .count(count)
                .build();
    }

    // ==================== 内部工具 ====================

    /** 备注 trim 后为空白串则归一化为 null */
    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String trimmed = remark.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void ensureNameUnique(String name, Long userId, Long excludeId) {
        LambdaQueryWrapper<ExpenseCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExpenseCategory::getUserId, userId)
                .eq(ExpenseCategory::getName, name)
                .ne(excludeId != null, ExpenseCategory::getId, excludeId);
        if (categoryRepository.selectCount(wrapper) > 0) {
            throw new BusinessException("类型名称已存在");
        }
    }

    private Map<Long, String> categoryNameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<ExpenseCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ExpenseCategory::getId, ids);
        return categoryRepository.selectList(wrapper).stream()
                .collect(Collectors.toMap(ExpenseCategory::getId, ExpenseCategory::getName, (a, b) -> a));
    }

    /** 取出并校验类型归属；不存在或非本人一律按「不存在」处理，避免泄露他人数据 */
    private ExpenseCategory requireOwnedCategoryForRecord(Long categoryId, Long userId) {
        ExpenseCategory category = categoryRepository.selectById(categoryId);
        if (category == null || !category.getUserId().equals(userId)) {
            throw new BusinessException("消费类型不存在");
        }
        return category;
    }

    private ExpenseCategory requireOwnedCategory(Long id, Long userId) {
        ExpenseCategory category = categoryRepository.selectById(id);
        if (category == null) {
            throw new BusinessException("消费类型不存在");
        }
        if (!category.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该消费类型");
        }
        return category;
    }

    private ExpenseRecord requireOwnedRecord(Long id, Long userId) {
        ExpenseRecord record = recordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException("消费记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该消费记录");
        }
        return record;
    }

    private Page<ExpenseRecord> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
