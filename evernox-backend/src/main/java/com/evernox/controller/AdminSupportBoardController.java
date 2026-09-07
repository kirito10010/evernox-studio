package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.SupportBoardRequest;
import com.evernox.dto.SupportBoardResponse;
import com.evernox.dto.SupportPixelRequest;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.SupportBoardService;
import jakarta.servlet.http.HttpServletRequest;
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
 * 应援板管理端接口（仅 admin）
 */
@RestController
@RequestMapping("/admin/support-board")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminSupportBoardController {

    private final SupportBoardService supportBoardService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping
    public Result<List<SupportBoardResponse>> list() {
        return Result.success(supportBoardService.list());
    }

    @PostMapping
    public Result<SupportBoardResponse> create(@RequestBody SupportBoardRequest request) {
        return Result.success("创建成功", supportBoardService.create(request.getName()));
    }

    @PutMapping("/{id}/active")
    public Result<Void> setActive(@PathVariable Long id) {
        supportBoardService.setActive(id);
        return Result.success("已设为当前画板", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supportBoardService.delete(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/pixel/lock")
    public Result<Void> lockPixel(@RequestParam int x, @RequestParam int y, HttpServletRequest http) {
        supportBoardService.lockPixel(getUserId(http), x, y);
        return Result.success("已锁定", null);
    }

    @PostMapping("/pixel/unlock")
    public Result<Void> unlockPixel(@RequestParam int x, @RequestParam int y) {
        supportBoardService.unlockPixel(x, y);
        return Result.success("已解锁", null);
    }

    @PostMapping("/pixel/lock-batch")
    public Result<Void> lockPixels(@RequestBody List<SupportPixelRequest> pixels, HttpServletRequest http) {
        supportBoardService.lockPixels(getUserId(http), pixels);
        return Result.success("已锁定", null);
    }

    @PostMapping("/pixel/unlock-batch")
    public Result<Void> unlockPixels(@RequestBody List<SupportPixelRequest> pixels) {
        supportBoardService.unlockPixels(pixels);
        return Result.success("已解锁", null);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
