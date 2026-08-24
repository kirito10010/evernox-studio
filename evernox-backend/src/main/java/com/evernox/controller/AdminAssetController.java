package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.AdminAlbumImagesRequest;
import com.evernox.dto.AdminAssetStatsResponse;
import com.evernox.dto.AdminBatchVisibilityRequest;
import com.evernox.dto.AlbumRequest;
import com.evernox.dto.AlbumResponse;
import com.evernox.dto.ImageResponse;
import com.evernox.service.AdminAssetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员资产（图片 / 相册）管理控制器
 *
 * 类级 @PreAuthorize 是唯一可信的权限防线：本模块的接口可以读取任意用户的私密图片，
 * 一旦这里失守就是全平台隐私泄漏。service 层不再做归属校验，切勿把这些方法挪到其他控制器。
 */
@RestController
@RequestMapping("/admin/asset")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminAssetController {

    private final AdminAssetService adminAssetService;

    // ==================== 图片 ====================

    @GetMapping("/image/list")
    public Result<IPage<ImageResponse>> listImages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer visibility,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long albumId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(adminAssetService.listImages(
                page, size, userId, visibility, keyword, albumId, startDate, endDate, sortField, sortOrder));
    }

    @GetMapping("/image/{id}")
    public Result<ImageResponse> imageDetail(@PathVariable Long id) {
        return Result.success(adminAssetService.getImage(id));
    }

    /**
     * 取图片原始字节（含他人私密图）
     */
    @GetMapping("/image/{id}/file")
    public ResponseEntity<Resource> imageFile(@PathVariable Long id) {
        AdminAssetService.ImageFile file = adminAssetService.getImageFile(id);
        String rawMimeType = file.mimeType();
        String mimeType = rawMimeType != null ? rawMimeType : "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                // 管理员看到的可能是他人私密图，一律禁止任何层级的缓存
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0")
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }

    @GetMapping("/image/{id}/albums")
    public Result<List<Long>> imageAlbums(@PathVariable Long id) {
        return Result.success(adminAssetService.getImageAlbumIds(id));
    }

    @PutMapping("/image/{id}/visibility")
    public Result<ImageResponse> updateImageVisibility(@PathVariable Long id, @RequestParam Integer visibility) {
        return Result.success(adminAssetService.updateImageVisibility(id, visibility));
    }

    @PutMapping("/image/visibility/batch")
    public Result<Void> updateImageVisibilityBatch(@Valid @RequestBody AdminBatchVisibilityRequest request) {
        adminAssetService.updateImageVisibilityBatch(request.getIds(), request.getVisibility());
        return Result.success();
    }

    @DeleteMapping("/image/{id}")
    public Result<Void> deleteImage(@PathVariable Long id) {
        adminAssetService.deleteImage(id);
        return Result.success();
    }

    @DeleteMapping("/image/batch")
    public Result<Void> deleteImages(@RequestParam List<Long> ids) {
        adminAssetService.deleteImages(ids);
        return Result.success();
    }

    // ==================== 相册 ====================

    @GetMapping("/album/list")
    public Result<IPage<AlbumResponse>> listAlbums(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer visibility,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(adminAssetService.listAlbums(
                page, size, userId, visibility, keyword, startDate, endDate, sortField, sortOrder));
    }

    @GetMapping("/album/{id}")
    public Result<AlbumResponse> albumDetail(@PathVariable Long id) {
        return Result.success(adminAssetService.getAlbum(id));
    }

    @GetMapping("/album/{id}/images")
    public Result<IPage<ImageResponse>> albumImages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "24") int size) {
        return Result.success(adminAssetService.listAlbumImages(id, page, size));
    }

    @PutMapping("/album/{id}")
    public Result<AlbumResponse> updateAlbum(@PathVariable Long id, @Valid @RequestBody AlbumRequest request) {
        return Result.success(adminAssetService.updateAlbum(id, request));
    }

    @PostMapping("/album/{id}/images")
    public Result<Void> addImagesToAlbum(@PathVariable Long id, @Valid @RequestBody AdminAlbumImagesRequest request) {
        adminAssetService.addImagesToAlbum(id, request.getImageIds());
        return Result.success();
    }

    @DeleteMapping("/album/{id}/images/{imageId}")
    public Result<Void> removeImageFromAlbum(@PathVariable Long id, @PathVariable Long imageId) {
        adminAssetService.removeImageFromAlbum(id, imageId);
        return Result.success();
    }

    @DeleteMapping("/album/{id}")
    public Result<Void> deleteAlbum(@PathVariable Long id) {
        adminAssetService.deleteAlbum(id);
        return Result.success();
    }

    @DeleteMapping("/album/batch")
    public Result<Void> deleteAlbums(@RequestParam List<Long> ids) {
        adminAssetService.deleteAlbums(ids);
        return Result.success();
    }

    // ==================== 统计 ====================

    @GetMapping("/stats")
    public Result<AdminAssetStatsResponse> stats() {
        return Result.success(adminAssetService.getStats());
    }
}
