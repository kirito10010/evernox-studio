package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.Result;
import com.evernox.dto.AlbumRequest;
import com.evernox.dto.AlbumResponse;
import com.evernox.dto.ImageResponse;
import com.evernox.entity.Album;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.AlbumService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 相册控制器
 */
@RestController
@RequestMapping("/album")
@RequiredArgsConstructor
public class AlbumController {

    private final AlbumService albumService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 创建相册
     */
    @PostMapping
    public Result<AlbumResponse> createAlbum(
            @Valid @RequestBody AlbumRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        AlbumResponse response = albumService.createAlbum(request, userId);
        return Result.success("创建成功", response);
    }

    /**
     * 更新相册
     */
    @PutMapping("/{id}")
    public Result<AlbumResponse> updateAlbum(
            @PathVariable Long id,
            @Valid @RequestBody AlbumRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        AlbumResponse response = albumService.updateAlbum(id, request, userId);
        return Result.success("更新成功", response);
    }

    /**
     * 删除相册
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteAlbum(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        albumService.deleteAlbum(id, userId);
        return Result.success("删除成功", null);
    }

    /**
     * 获取当前用户相册列表
     */
    @GetMapping("/list")
    public Result<IPage<AlbumResponse>> getUserAlbums(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        Page<Album> pageParam = new Page<>(page, size);
        IPage<AlbumResponse> result = albumService.getUserAlbums(userId, pageParam);
        return Result.success(result);
    }

    /**
     * 获取公开相册列表 — 无需认证
     */
    @GetMapping("/public")
    public Result<IPage<AlbumResponse>> getPublicAlbums(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Album> pageParam = new Page<>(page, size);
        IPage<AlbumResponse> result = albumService.getPublicAlbums(pageParam);
        return Result.success(result);
    }

    /**
     * 获取相册详情
     */
    @GetMapping("/{id}")
    public Result<AlbumResponse> getAlbumById(
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        AlbumResponse response = albumService.getAlbumById(id, userId);
        return Result.success(response);
    }

    /**
     * 获取相册内的图片列表
     */
    @GetMapping("/{id}/images")
    public Result<IPage<ImageResponse>> getAlbumImages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        Page<?> pageParam = new Page<>(page, size);
        IPage<ImageResponse> result = albumService.getAlbumImages(id, userId, pageParam);
        return Result.success(result);
    }

    /**
     * 向相册添加图片
     */
    @PostMapping("/{id}/images")
    public Result<Void> addImageToAlbum(
            @PathVariable Long id,
            @RequestParam Long imageId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        albumService.addImageToAlbum(id, imageId, userId);
        return Result.success("添加成功", null);
    }

    /**
     * 从相册移除图片
     */
    @DeleteMapping("/{id}/images/{imageId}")
    public Result<Void> removeImageFromAlbum(
            @PathVariable Long id,
            @PathVariable Long imageId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        albumService.removeImageFromAlbum(id, imageId, userId);
        return Result.success("移除成功", null);
    }

    /**
     * 获取相册统计
     */
    @GetMapping("/stats")
    public Result<Map<String, Long>> getStats(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        Map<String, Long> stats = new HashMap<>();
        stats.put("myAlbums", albumService.countUserAlbums(userId));
        return Result.success(stats);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
