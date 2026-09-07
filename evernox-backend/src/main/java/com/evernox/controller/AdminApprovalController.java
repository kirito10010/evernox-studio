package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.ApprovalSummaryResponse;
import com.evernox.service.AdminApprovalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员待审批汇总控制器
 */
@RestController
@RequestMapping("/admin/approval")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminApprovalController {

    private final AdminApprovalService adminApprovalService;

    /** 待审批条数汇总 */
    @GetMapping("/summary")
    public Result<ApprovalSummaryResponse> summary() {
        return Result.success(adminApprovalService.getSummary());
    }
}
