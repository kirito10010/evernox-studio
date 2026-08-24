package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.PerformanceChartResponse;
import com.evernox.dto.PerformanceProjectRequest;
import com.evernox.dto.PerformanceProjectResponse;
import com.evernox.dto.PerformanceRecordRequest;
import com.evernox.dto.PerformanceRecordResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.PerformanceService;
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
 * 记录绩效控制器（用户侧，均需登录）
 */
@RestController
@RequestMapping("/performance")
@RequiredArgsConstructor
public class PerformanceController {

    private final PerformanceService performanceService;
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== 项目配置 ====================

    @PostMapping("/project")
    public Result<PerformanceProjectResponse> createProject(
            @Valid @RequestBody PerformanceProjectRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("创建成功", performanceService.createProject(request, getUserId(httpRequest)));
    }

    @GetMapping("/project/list")
    public Result<List<PerformanceProjectResponse>> listProjects(HttpServletRequest request) {
        return Result.success(performanceService.listProjects(getUserId(request)));
    }

    @PutMapping("/project/{id}")
    public Result<PerformanceProjectResponse> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody PerformanceProjectRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("更新成功", performanceService.updateProject(id, request, getUserId(httpRequest)));
    }

    @DeleteMapping("/project/{id}")
    public Result<Void> deleteProject(@PathVariable Long id, HttpServletRequest request) {
        performanceService.deleteProject(id, getUserId(request));
        return Result.success("删除成功", null);
    }

    // ==================== 绩效记录 ====================

    @PostMapping("/record")
    public Result<PerformanceRecordResponse> createRecord(
            @Valid @RequestBody PerformanceRecordRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("创建成功", performanceService.createRecord(request, getUserId(httpRequest)));
    }

    @GetMapping("/record/list")
    public Result<IPage<PerformanceRecordResponse>> listRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer processType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {
        return Result.success(performanceService.listRecords(
                getUserId(request), projectId, processType,
                parseDate(startDate), parseDate(endDate), page, size));
    }

    @PutMapping("/record/{id}")
    public Result<PerformanceRecordResponse> updateRecord(
            @PathVariable Long id,
            @Valid @RequestBody PerformanceRecordRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("更新成功", performanceService.updateRecord(id, request, getUserId(httpRequest)));
    }

    @DeleteMapping("/record/{id}")
    public Result<Void> deleteRecord(@PathVariable Long id, HttpServletRequest request) {
        performanceService.deleteRecord(id, getUserId(request));
        return Result.success("删除成功", null);
    }

    @GetMapping("/record/months")
    public Result<List<String>> months(HttpServletRequest request) {
        return Result.success(performanceService.months(getUserId(request)));
    }

    @GetMapping("/record/chart")
    public Result<PerformanceChartResponse> chart(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {
        return Result.success(performanceService.chart(
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
