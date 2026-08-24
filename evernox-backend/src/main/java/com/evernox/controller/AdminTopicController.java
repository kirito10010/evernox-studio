package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.TopicCircleRequest;
import com.evernox.dto.TopicCircleResponse;
import com.evernox.dto.TopicCommentResponse;
import com.evernox.dto.TopicPostResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.AdminTopicService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 话题集中营管理控制器（仅管理员）
 */
@RestController
@RequestMapping("/admin/topic")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminTopicController {

    private final AdminTopicService adminTopicService;
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== 帖子 ====================

    @GetMapping("/post/list")
    public Result<IPage<TopicPostResponse>> listPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminTopicService.listPosts(page, size, keyword));
    }

    @DeleteMapping("/post/{id}")
    public Result<Void> deletePost(@PathVariable Long id) {
        adminTopicService.deletePost(id);
        return Result.success("删除成功", null);
    }

    @DeleteMapping("/post/batch")
    public Result<Void> batchDeletePosts(@RequestParam List<Long> ids) {
        adminTopicService.batchDeletePosts(ids);
        return Result.success("删除成功", null);
    }

    // ==================== 评论 ====================

    @GetMapping("/comment/list")
    public Result<IPage<TopicCommentResponse>> listComments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminTopicService.listComments(page, size, keyword));
    }

    @DeleteMapping("/comment/{id}")
    public Result<Void> deleteComment(@PathVariable Long id) {
        adminTopicService.deleteComment(id);
        return Result.success("删除成功", null);
    }

    @DeleteMapping("/comment/batch")
    public Result<Void> batchDeleteComments(@RequestParam List<Long> ids) {
        adminTopicService.batchDeleteComments(ids);
        return Result.success("删除成功", null);
    }

    // ==================== 圈子 ====================

    @GetMapping("/circle/list")
    public Result<IPage<TopicCircleResponse>> listCircles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(adminTopicService.listCircles(page, size, keyword));
    }

    @PostMapping("/circle")
    public Result<TopicCircleResponse> createCircle(@Valid @RequestBody TopicCircleRequest request,
                                                    HttpServletRequest http) {
        return Result.success("创建成功", adminTopicService.createCircle(request, getUserId(http)));
    }

    @PutMapping("/circle/{id}")
    public Result<TopicCircleResponse> updateCircle(@PathVariable Long id,
                                                    @Valid @RequestBody TopicCircleRequest request) {
        return Result.success("更新成功", adminTopicService.updateCircle(id, request));
    }

    @DeleteMapping("/circle/{id}")
    public Result<Void> deleteCircle(@PathVariable Long id) {
        adminTopicService.deleteCircle(id);
        return Result.success("删除成功", null);
    }

    @DeleteMapping("/circle/batch")
    public Result<Void> batchDeleteCircles(@RequestParam List<Long> ids) {
        adminTopicService.batchDeleteCircles(ids);
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
