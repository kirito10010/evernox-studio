package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.SiteLinkRequest;
import com.evernox.dto.SiteLinkResponse;
import com.evernox.dto.SiteStatsResponse;
import com.evernox.dto.SiteTagResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.SiteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 网站分享控制器（用户侧，均需登录）
 */
@RestController
@RequestMapping("/site")
@RequiredArgsConstructor
public class SiteController {

    private final SiteService siteService;
    private final JwtTokenProvider jwtTokenProvider;

    /** 新建分享（默认私有） */
    @PostMapping
    public Result<SiteLinkResponse> create(
            @Valid @RequestBody SiteLinkRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("创建成功", siteService.create(request, getUserId(httpRequest)));
    }

    /** 编辑分享 */
    @PutMapping("/{id}")
    public Result<SiteLinkResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody SiteLinkRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("更新成功", siteService.update(id, request, getUserId(httpRequest)));
    }

    /** 删除分享 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        siteService.delete(id, getUserId(request));
        return Result.success("删除成功", null);
    }

    /** 申请公开 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest request) {
        siteService.submit(id, getUserId(request));
        return Result.success("已提交审批", null);
    }

    /** 撤回申请 / 自行撤下 */
    @PostMapping("/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id, HttpServletRequest request) {
        siteService.withdraw(id, getUserId(request));
        return Result.success("已转为私有", null);
    }

    /** 我的分享列表 */
    @GetMapping("/list")
    public Result<IPage<SiteLinkResponse>> listMine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {
        return Result.success(siteService.listMine(getUserId(request), status, page, size));
    }

    /** 公开导航列表 */
    @GetMapping("/public")
    public Result<IPage<SiteLinkResponse>> listPublic(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tagIds) {
        return Result.success(siteService.listPublic(keyword, parseTagIds(tagIds), page, size));
    }

    /** 标签库（只读） */
    @GetMapping("/tags")
    public Result<List<SiteTagResponse>> listTags() {
        return Result.success(siteService.listTags());
    }

    /** 我的分享统计 */
    @GetMapping("/stats")
    public Result<SiteStatsResponse> getStats(HttpServletRequest request) {
        return Result.success(siteService.getStats(getUserId(request)));
    }

    /** 分享详情 */
    @GetMapping("/{id}")
    public Result<SiteLinkResponse> getById(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(siteService.getById(id, getUserId(request)));
    }

    /** 逗号分隔的标签ID，非数字直接丢弃，不让脏参数进到 SQL 条件 */
    private List<Long> parseTagIds(String tagIds) {
        if (tagIds == null || tagIds.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tagIds.split(","))
                .map(s -> s.trim())
                .filter(s -> s.matches("\\d+"))
                .map(Long::valueOf)
                .toList();
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
