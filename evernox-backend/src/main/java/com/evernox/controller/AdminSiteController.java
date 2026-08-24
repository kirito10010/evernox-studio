package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.SiteLinkResponse;
import com.evernox.dto.SiteRejectRequest;
import com.evernox.dto.SiteReviewRequest;
import com.evernox.dto.SiteStatsResponse;
import com.evernox.dto.SiteTagRequest;
import com.evernox.dto.SiteTagResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.AdminSiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 网站分享审批控制器（管理员）
 *
 * 类级 @PreAuthorize 是唯一可信的权限防线；SecurityConfig 对 /admin/** 的 URL 级拦截是纵深防御。
 */
@RestController
@RequestMapping("/admin/site")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminSiteController {

    private final AdminSiteService adminSiteService;
    private final JwtTokenProvider jwtTokenProvider;

    /** 站点列表：支持状态、用户、关键词、排序 */
    @GetMapping("/list")
    public Result<IPage<SiteLinkResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(adminSiteService.listSites(page, size, status, userId, keyword, sortField, sortOrder));
    }

    /** 审批通过（必须带标签） */
    @PostMapping("/{id}/approve")
    public Result<Void> approve(
            @PathVariable Long id,
            @Valid @RequestBody SiteReviewRequest request,
            HttpServletRequest httpRequest) {
        adminSiteService.approve(id, request.getTagIds(), getUserId(httpRequest));
        return Result.success("已通过审批", null);
    }

    /** 审批驳回（必须填原因） */
    @PostMapping("/{id}/reject")
    public Result<Void> reject(
            @PathVariable Long id,
            @Valid @RequestBody SiteRejectRequest request,
            HttpServletRequest httpRequest) {
        adminSiteService.reject(id, request.getReason(), getUserId(httpRequest));
        return Result.success("已驳回", null);
    }

    /** 撤下已公开站点 */
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id, HttpServletRequest request) {
        adminSiteService.offline(id, getUserId(request));
        return Result.success("已撤下", null);
    }

    /** 调整已公开站点的标签 */
    @PutMapping("/{id}/tags")
    public Result<Void> updateTags(
            @PathVariable Long id,
            @Valid @RequestBody SiteReviewRequest request) {
        adminSiteService.updateTags(id, request.getTagIds());
        return Result.success("标签已更新", null);
    }

    /** 标签库列表（含关联站点数） */
    @GetMapping("/tag")
    public Result<List<SiteTagResponse>> listTags() {
        return Result.success(adminSiteService.listTags());
    }

    /** 新建标签 */
    @PostMapping("/tag")
    public Result<SiteTagResponse> createTag(@Valid @RequestBody SiteTagRequest request) {
        return Result.success("创建成功", adminSiteService.createTag(request));
    }

    /** 更新标签 */
    @PutMapping("/tag/{id}")
    public Result<SiteTagResponse> updateTag(
            @PathVariable Long id,
            @Valid @RequestBody SiteTagRequest request) {
        return Result.success("更新成功", adminSiteService.updateTag(id, request));
    }

    /** 删除标签 */
    @DeleteMapping("/tag/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        adminSiteService.deleteTag(id);
        return Result.success("删除成功", null);
    }

    /** 审批统计 */
    @GetMapping("/stats")
    public Result<SiteStatsResponse> getStats() {
        return Result.success(adminSiteService.getStats());
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
