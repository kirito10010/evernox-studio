package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.NoteResponse;
import com.evernox.dto.NoteStatsResponse;
import com.evernox.dto.SiteRejectRequest;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.AdminNoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 记事本审批控制器（管理员）
 *
 * 类级 @PreAuthorize 是唯一可信的权限防线；SecurityConfig 对 /admin/** 的 URL 级拦截是纵深防御。
 * 驳回请求体复用 SiteRejectRequest：字段与校验完全一致（一个必填的 reason），
 * 再造一个同形 DTO 只会多一处需要同步维护的地方。
 */
@RestController
@RequestMapping("/admin/note")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminNoteController {

    private final AdminNoteService adminNoteService;
    private final JwtTokenProvider jwtTokenProvider;

    /** 笔记列表：支持状态与关键词 */
    @GetMapping("/list")
    public Result<IPage<NoteResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminNoteService.listNotes(page, size, status, keyword));
    }

    /** 详情（含正文），审批前阅读用 */
    @GetMapping("/{id}")
    public Result<NoteResponse> getById(@PathVariable Long id) {
        return Result.success(adminNoteService.getById(id));
    }

    /** 审批通过 */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id, HttpServletRequest request) {
        adminNoteService.approve(id, getUserId(request));
        return Result.success("已通过审批", null);
    }

    /** 审批驳回（必须填原因） */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(
            @PathVariable Long id,
            @Valid @RequestBody SiteRejectRequest request,
            HttpServletRequest httpRequest) {
        adminNoteService.reject(id, request.getReason(), getUserId(httpRequest));
        return Result.success("已驳回", null);
    }

    /** 撤下已公开笔记 */
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id, HttpServletRequest request) {
        adminNoteService.offline(id, getUserId(request));
        return Result.success("已撤下", null);
    }

    /** 审批统计 */
    @GetMapping("/stats")
    public Result<NoteStatsResponse> getStats() {
        return Result.success(adminNoteService.getStats());
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
