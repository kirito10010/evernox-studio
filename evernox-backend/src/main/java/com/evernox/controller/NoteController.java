package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.NoteRequest;
import com.evernox.dto.NoteResponse;
import com.evernox.dto.NoteStatsResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.NoteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 记事本控制器（用户侧，均需登录）
 */
@RestController
@RequestMapping("/note")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final JwtTokenProvider jwtTokenProvider;

    /** 新建笔记（默认私有） */
    @PostMapping
    public Result<NoteResponse> create(
            @Valid @RequestBody NoteRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("创建成功", noteService.create(request, getUserId(httpRequest)));
    }

    /** 编辑笔记 */
    @PutMapping("/{id}")
    public Result<NoteResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody NoteRequest request,
            HttpServletRequest httpRequest) {
        return Result.success("已保存", noteService.update(id, request, getUserId(httpRequest)));
    }

    /** 删除笔记 */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        noteService.delete(id, getUserId(request));
        return Result.success("删除成功", null);
    }

    /** 置顶开关 */
    @PutMapping("/{id}/pin")
    public Result<NoteResponse> setPinned(
            @PathVariable Long id,
            @RequestParam boolean pinned,
            HttpServletRequest request) {
        return Result.success(noteService.setPinned(id, pinned, getUserId(request)));
    }

    /** 申请公开 */
    @PostMapping("/{id}/submit")
    public Result<Void> submit(@PathVariable Long id, HttpServletRequest request) {
        noteService.submit(id, getUserId(request));
        return Result.success("已提交审批", null);
    }

    /** 撤回申请 / 撤下已公开 */
    @PostMapping("/{id}/withdraw")
    public Result<Void> withdraw(@PathVariable Long id, HttpServletRequest request) {
        noteService.withdraw(id, getUserId(request));
        return Result.success("已转为私有", null);
    }

    /** 我的笔记分页 */
    @GetMapping("/list")
    public Result<IPage<NoteResponse>> listMine(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            HttpServletRequest request) {
        return Result.success(noteService.listMine(
                getUserId(request), keyword, status, page, size));
    }

    /** 公开笔记分页 */
    @GetMapping("/public")
    public Result<IPage<NoteResponse>> listPublic(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return Result.success(noteService.listPublic(keyword, page, size));
    }

    /** 我的笔记统计 */
    @GetMapping("/stats")
    public Result<NoteStatsResponse> getStats(HttpServletRequest request) {
        return Result.success(noteService.getStats(getUserId(request)));
    }

    /** 详情（含正文） */
    @GetMapping("/{id}")
    public Result<NoteResponse> getById(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(noteService.getById(id, getUserId(request)));
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
