package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.BatchDeleteRequest;
import com.evernox.dto.OvertimeRequest;
import com.evernox.dto.OvertimeResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.OvertimeService;
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

/**
 * 加班记录控制器（用户侧，均需登录）
 */
@RestController
@RequestMapping("/performance/overtime")
@RequiredArgsConstructor
public class OvertimeController {

    private final OvertimeService overtimeService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping
    public Result<OvertimeResponse> create(
            @Valid @RequestBody OvertimeRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("创建成功", overtimeService.createOvertime(request, getUserId(httpRequest)));
    }

    @GetMapping("/list")
    public Result<IPage<OvertimeResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            HttpServletRequest request) {
        return Result.success(overtimeService.listOvertime(
                getUserId(request), parseDate(startDate), parseDate(endDate), page, size));
    }

    @PutMapping("/{id}")
    public Result<OvertimeResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody OvertimeRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("更新成功", overtimeService.updateOvertime(id, request, getUserId(httpRequest)));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        overtimeService.deleteOvertime(id, getUserId(request));
        return Result.success("删除成功", null);
    }

    @PostMapping("/batch-delete")
    public Result<Void> batchDelete(
            @Valid @RequestBody BatchDeleteRequest request,
            HttpServletRequest httpRequest) {
        overtimeService.batchDeleteOvertime(request.getIds(), getUserId(httpRequest));
        return Result.success("删除成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value);
    }
}
