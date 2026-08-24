package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.ExpenseCategoryRequest;
import com.evernox.dto.ExpenseCategoryResponse;
import com.evernox.dto.ExpenseChartResponse;
import com.evernox.dto.ExpenseRecordRequest;
import com.evernox.dto.ExpenseRecordResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.ExpenseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 记账控制器（用户侧，均需登录）
 */
@RestController
@RequestMapping("/expense")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== 消费类型 ====================

    @PostMapping("/category")
    public Result<ExpenseCategoryResponse> createCategory(
            @Valid @RequestBody ExpenseCategoryRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("创建成功", expenseService.createCategory(request, getUserId(httpRequest)));
    }

    @GetMapping("/category/list")
    public Result<List<ExpenseCategoryResponse>> listCategories(HttpServletRequest request) {
        return Result.success(expenseService.listCategories(getUserId(request)));
    }

    @PutMapping("/category/{id}")
    public Result<ExpenseCategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseCategoryRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("更新成功", expenseService.updateCategory(id, request, getUserId(httpRequest)));
    }

    @DeleteMapping("/category/{id}")
    public Result<Void> deleteCategory(@PathVariable Long id, HttpServletRequest request) {
        expenseService.deleteCategory(id, getUserId(request));
        return Result.success("删除成功", null);
    }

    // ==================== 消费记录 ====================

    @PostMapping("/record")
    public Result<ExpenseRecordResponse> createRecord(
            @Valid @RequestBody ExpenseRecordRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("创建成功", expenseService.createRecord(request, getUserId(httpRequest)));
    }

    @GetMapping("/record/list")
    public Result<IPage<ExpenseRecordResponse>> listRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        return Result.success(expenseService.listRecords(
                getUserId(request), categoryId,
                parseDate(startDate), parseDate(endDate), keyword, page, size));
    }

    @PutMapping("/record/{id}")
    public Result<ExpenseRecordResponse> updateRecord(
            @PathVariable Long id,
            @Valid @RequestBody ExpenseRecordRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("更新成功", expenseService.updateRecord(id, request, getUserId(httpRequest)));
    }

    @DeleteMapping("/record/{id}")
    public Result<Void> deleteRecord(@PathVariable Long id, HttpServletRequest request) {
        expenseService.deleteRecord(id, getUserId(request));
        return Result.success("删除成功", null);
    }

    @GetMapping("/record/months")
    public Result<List<String>> months(HttpServletRequest request) {
        return Result.success(expenseService.months(getUserId(request)));
    }

    @GetMapping("/record/chart")
    public Result<ExpenseChartResponse> chart(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {
        return Result.success(expenseService.chart(
                getUserId(request), parseDate(startDate), parseDate(endDate)));
    }

    // ==================== 工具 ====================

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }

    /** 空串/空白当作 null；非法日期格式由框架抛 400 */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }
}
