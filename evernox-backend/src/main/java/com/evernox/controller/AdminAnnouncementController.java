package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.AnnouncementRequest;
import com.evernox.dto.AnnouncementResponse;
import com.evernox.dto.AnnouncementTagRequest;
import com.evernox.dto.AnnouncementTagResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.AnnouncementService;
import com.evernox.service.AnnouncementTagService;
import com.evernox.service.impl.AnnouncementSseRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员公告管理控制器
 *
 * 类级 @PreAuthorize 与 SecurityConfig 的 /admin/** 形成纵深防御。
 */
@RestController
@RequestMapping("/admin/announcement")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;
    private final AnnouncementTagService tagService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AnnouncementSseRegistry sseRegistry;

    // ==================== 标签 ====================

    @GetMapping("/tag/list")
    public Result<List<AnnouncementTagResponse>> tagList() {
        return Result.success(tagService.list());
    }

    @PostMapping("/tag")
    public Result<AnnouncementTagResponse> createTag(@Valid @RequestBody AnnouncementTagRequest request) {
        return Result.success("创建成功", tagService.create(request));
    }

    @PutMapping("/tag/{id}")
    public Result<AnnouncementTagResponse> updateTag(@PathVariable Long id,
                                                     @Valid @RequestBody AnnouncementTagRequest request) {
        return Result.success("更新成功", tagService.update(id, request));
    }

    @DeleteMapping("/tag/{id}")
    public Result<Void> deleteTag(@PathVariable Long id) {
        tagService.delete(id);
        return Result.success("删除成功", null);
    }

    // ==================== 公告 ====================

    @GetMapping("/list")
    public Result<IPage<AnnouncementResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(announcementService.listAdmin(page, size, keyword));
    }

    @PostMapping
    public Result<AnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest request,
                                               HttpServletRequest http) {
        AnnouncementResponse resp = announcementService.create(request, getUserId(http));
        sseRegistry.broadcast();
        return Result.success("发布成功", resp);
    }

    @PutMapping("/{id}")
    public Result<AnnouncementResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody AnnouncementRequest request) {
        AnnouncementResponse resp = announcementService.update(id, request);
        sseRegistry.broadcast();
        return Result.success("更新成功", resp);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        announcementService.delete(id);
        sseRegistry.broadcast();
        return Result.success("删除成功", null);
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestParam List<Long> ids) {
        announcementService.batchDelete(ids);
        sseRegistry.broadcast();
        return Result.success("删除成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
