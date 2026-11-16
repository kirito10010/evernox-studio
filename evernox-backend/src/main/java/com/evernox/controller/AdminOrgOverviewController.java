package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.AdminOrgOverviewResponse;
import com.evernox.service.AdminOrgOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理员组织总览（仅 admin；超级会员不可见）
 */
@RestController
@RequestMapping("/admin/org-overview")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminOrgOverviewController {

    private final AdminOrgOverviewService overviewService;

    @GetMapping
    public Result<List<AdminOrgOverviewResponse>> list() {
        return Result.success(overviewService.listAll());
    }
}
