package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.VisitLogItemResponse;
import com.evernox.dto.VisitOverviewResponse;
import com.evernox.dto.VisitTrendItem;
import com.evernox.dto.VisitUserRankItem;
import com.evernox.service.VisitLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员访问统计（仅 admin）
 */
@RestController
@RequestMapping("/admin/visit")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminVisitController {

    private final VisitLogService visitLogService;

    @GetMapping("/overview")
    public Result<VisitOverviewResponse> overview() {
        return Result.success(visitLogService.overview());
    }

    @GetMapping("/trend")
    public Result<List<VisitTrendItem>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(visitLogService.trend(days));
    }

    @GetMapping("/rank")
    public Result<List<VisitUserRankItem>> rank(@RequestParam(defaultValue = "10") int top) {
        return Result.success(visitLogService.rank(top));
    }

    @GetMapping("/recent")
    public Result<IPage<VisitLogItemResponse>> recent(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String username) {
        return Result.success(visitLogService.recent(page, size, type, username));
    }
}
