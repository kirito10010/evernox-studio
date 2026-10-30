package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.ReleaseProjectRequest;
import com.evernox.dto.ReleaseProjectResponse;
import com.evernox.security.SecurityUtils;
import com.evernox.service.ReleaseProjectService;

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

import java.util.List;

/**
 * 管理员 - 发布项目管理
 */
@RestController
@RequestMapping("/admin/release/project")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminReleaseProjectController {

    private final ReleaseProjectService projectService;

    @GetMapping("/list")
    public Result<List<ReleaseProjectResponse>> list(@RequestParam(required = false) String keyword) {
        return Result.success(projectService.list(keyword, SecurityUtils.currentUserId()));
    }

    @GetMapping("/{id}")
    public Result<ReleaseProjectResponse> get(@PathVariable Long id) {
        return Result.success(projectService.get(id, SecurityUtils.currentUserId()));
    }

    @PostMapping
    public Result<ReleaseProjectResponse> create(@Valid @RequestBody ReleaseProjectRequest request) {
        return Result.success("项目创建成功",
                projectService.create(request, SecurityUtils.currentUserId()));
    }

    @PutMapping("/{id}")
    public Result<ReleaseProjectResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody ReleaseProjectRequest request) {
        return Result.success("项目更新成功",
                projectService.update(id, request, SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, @RequestParam String confirmSlug) {
        projectService.delete(id, confirmSlug, SecurityUtils.currentUserId());
        return Result.success("项目已删除", null);
    }

}
