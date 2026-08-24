package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.TodoRequest;
import com.evernox.dto.TodoResponse;
import com.evernox.dto.TodoStatsResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.TodoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 待办事项控制器（用户侧，均需登录）
 */
@RestController
@RequestMapping("/todo")
@RequiredArgsConstructor
public class TodoController {

    private final TodoService todoService;
    private final JwtTokenProvider jwtTokenProvider;

    /** 新建待办 */
    @PostMapping
    public Result<TodoResponse> create(
            @Valid @RequestBody TodoRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("创建成功", todoService.create(request, getUserId(httpRequest)));
    }

    /** 编辑待办 */
    @PutMapping("/{id}")
    public Result<TodoResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody TodoRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("更新成功", todoService.update(id, request, getUserId(httpRequest)));
    }

    /** 切换完成态 */
    @PutMapping("/{id}/done")
    public Result<TodoResponse> setDone(
            @PathVariable Long id,
            @RequestParam boolean done,
            HttpServletRequest request) {
        return Result.success(todoService.setDone(id, done, getUserId(request)));
    }

    /** 删除待办 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        todoService.delete(id, getUserId(request));
        return Result.success("删除成功", null);
    }

    /** 我的待办分页 */
    @GetMapping("/list")
    public Result<IPage<TodoResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Integer done,
            @RequestParam(required = false) Integer priority,
            @RequestParam(required = false) String dueScope,
            HttpServletRequest request) {
        return Result.success(todoService.list(
                getUserId(request), done, priority, dueScope, page, size));
    }

    /** 我的待办统计 */
    @GetMapping("/stats")
    public Result<TodoStatsResponse> getStats(HttpServletRequest request) {
        return Result.success(todoService.getStats(getUserId(request)));
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
