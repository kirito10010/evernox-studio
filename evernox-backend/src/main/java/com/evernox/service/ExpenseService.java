package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.ExpenseCategoryRequest;
import com.evernox.dto.ExpenseCategoryResponse;
import com.evernox.dto.ExpenseChartResponse;
import com.evernox.dto.ExpenseRecordRequest;
import com.evernox.dto.ExpenseRecordResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 记账服务（纯私有数据，所有方法都按 userId 隔离）
 */
public interface ExpenseService {

    // ---- 消费类型 ----

    ExpenseCategoryResponse createCategory(ExpenseCategoryRequest request, Long userId);

    List<ExpenseCategoryResponse> listCategories(Long userId);

    ExpenseCategoryResponse updateCategory(Long id, ExpenseCategoryRequest request, Long userId);

    void deleteCategory(Long id, Long userId);

    // ---- 消费记录 ----

    ExpenseRecordResponse createRecord(ExpenseRecordRequest request, Long userId);

    ExpenseRecordResponse updateRecord(Long id, ExpenseRecordRequest request, Long userId);

    void deleteRecord(Long id, Long userId);

    /**
     * @param categoryId 可选，按类型过滤
     * @param startDate  可选，消费日期起
     * @param endDate    可选，消费日期止
     * @param keyword    可选，备注模糊搜索
     */
    IPage<ExpenseRecordResponse> listRecords(Long userId, Long categoryId,
                                             LocalDate startDate, LocalDate endDate,
                                             String keyword, int page, int size);

    /** 有记录的月份列表（YYYY-MM，倒序，最新在前） */
    List<String> months(Long userId);

    /** 消费趋势（按日聚合，含类型明细） */
    ExpenseChartResponse chart(Long userId, LocalDate startDate, LocalDate endDate);
}
