package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.SalaryConfigRequest;
import com.evernox.dto.SalaryConfigResponse;
import com.evernox.dto.SalaryRecordRequest;
import com.evernox.dto.SalaryRecordResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.SalaryService;
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

import java.math.BigDecimal;
import java.util.List;

/**
 * 记录工资控制器（用户侧，均需登录）
 */
@RestController
@RequestMapping("/salary")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== 工资配置 ====================

    @GetMapping("/config")
    public Result<SalaryConfigResponse> getConfig(HttpServletRequest request) {
        return Result.success(salaryService.getConfig(getUserId(request)));
    }

    @PutMapping("/config")
    public Result<SalaryConfigResponse> updateConfig(
            @Valid @RequestBody SalaryConfigRequest configRequest,
            HttpServletRequest httpRequest) {
        return Result.success("保存成功", salaryService.updateConfig(configRequest, getUserId(httpRequest)));
    }

    // ==================== 工资记录 ====================

    @GetMapping("/preview")
    public Result<SalaryRecordResponse> preview(
            @RequestParam String month,
            @RequestParam(required = false) BigDecimal attendanceDays,
            HttpServletRequest request) {
        return Result.success(salaryService.preview(month, attendanceDays, getUserId(request)));
    }

    @PostMapping("/record")
    public Result<SalaryRecordResponse> createRecord(
            @Valid @RequestBody SalaryRecordRequest recordRequest,
            HttpServletRequest httpRequest) {
        return Result.success("创建成功", salaryService.createRecord(recordRequest, getUserId(httpRequest)));
    }

    @GetMapping("/record/list")
    public Result<List<SalaryRecordResponse>> listRecords(HttpServletRequest request) {
        return Result.success(salaryService.listRecords(getUserId(request)));
    }

    @DeleteMapping("/record/{id}")
    public Result<Void> deleteRecord(@PathVariable Long id, HttpServletRequest request) {
        salaryService.deleteRecord(id, getUserId(request));
        return Result.success("删除成功", null);
    }

    // ==================== 工具 ====================

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
