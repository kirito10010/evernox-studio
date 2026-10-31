package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.ReleaseDetailResponse;
import com.evernox.dto.ReleaseLatestRequest;
import com.evernox.dto.ReleaseRequest;
import com.evernox.dto.ReleaseSummaryResponse;
import com.evernox.security.SecurityUtils;
import com.evernox.service.ReleaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员 - 版本发布管理
 */
@RestController
@RequestMapping("/admin/release")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('admin', 'super_member')")
public class AdminReleaseController {

    private final ReleaseService releaseService;

    @GetMapping("/list")
    public Result<IPage<ReleaseSummaryResponse>> list(
            @RequestParam Long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Boolean latest,
            @RequestParam(required = false) String sort) {
        return Result.success(releaseService.list(projectId, page, size, keyword, status, latest, sort,
                SecurityUtils.currentUserId()));
    }


    @GetMapping("/{id}")
    public Result<ReleaseDetailResponse> get(@PathVariable Long id) {
        return Result.success(releaseService.get(id, SecurityUtils.currentUserId()));
    }

    @PostMapping
    public Result<ReleaseDetailResponse> create(@Valid @RequestBody ReleaseRequest request) {
        return Result.success("版本已保存", releaseService.create(request, SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}")
    public Result<ReleaseDetailResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody ReleaseRequest request) {
        return Result.success("版本已更新",
                releaseService.update(id, request, SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        releaseService.delete(id, SecurityUtils.currentUserId());
        return Result.success("版本已删除", null);
    }

    @PostMapping("/{id}/publish")
    public Result<ReleaseDetailResponse> publish(@PathVariable Long id,
                                                 @RequestBody(required = false) ReleaseLatestRequest request) {
        boolean latest = request == null || request.getLatest() == null || request.getLatest();
        return Result.success("发布成功",
                releaseService.publish(id, latest, SecurityUtils.currentUserId()));
    }

    @PostMapping("/{id}/latest")
    public Result<ReleaseDetailResponse> setLatest(@PathVariable Long id,
                                                   @RequestBody(required = false) ReleaseLatestRequest request) {
        boolean latest = request == null || request.getLatest() == null || request.getLatest();
        return Result.success(latest ? "已设为最新" : "已取消最新",
                releaseService.setLatest(id, latest, SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}/latest-yml")
    public Result<String> previewLatestYml(@PathVariable Long id) {
        return Result.success(releaseService.previewLatestYml(id, SecurityUtils.currentUserId()));
    }
}

