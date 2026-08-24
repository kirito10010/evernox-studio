package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.Result;
import com.evernox.dto.ImageDimensionsRequest;
import com.evernox.dto.ImageResponse;
import com.evernox.dto.ImageUploadRequest;
import com.evernox.dto.StorageStatsResponse;
import com.evernox.entity.Image;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.ImageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 图片控制器
 */
@RestController
@RequestMapping("/image")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 上传图片
     * 前端上传原始文件，服务端负责编码落盘
     */
    @PostMapping("/upload")
    public Result<ImageResponse> uploadImage(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") ImageUploadRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        ImageResponse response = imageService.uploadImage(file, request, userId);
        return Result.success("上传成功", response);
    }

    /**
     * 获取当前用户图片列表（分页）
     */
    @GetMapping("/list")
    public Result<IPage<ImageResponse>> getUserImages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        Page<Image> pageParam = new Page<>(page, size);
        IPage<ImageResponse> result = imageService.getUserImages(userId, pageParam);
        return Result.success(result);
    }

    /**
     * 获取公开图片列表（分页）— 无需认证
     */
    @GetMapping("/public")
    public Result<IPage<ImageResponse>> getPublicImages(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Image> pageParam = new Page<>(page, size);
        IPage<ImageResponse> result = imageService.getPublicImages(pageParam);
        return Result.success(result);
    }

    /**
     * 获取图片详情
     */
    @GetMapping("/{id}")
    public Result<ImageResponse> getImageById(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        ImageResponse response = imageService.getImageById(id, userId);
        return Result.success(response);
    }

    /**
     * 获取图片文件
     * 服务端解码后返回原始图片字节，可直接作为 img src 使用
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getImageFile(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        ImageResponse imageInfo = imageService.getImageById(id, userId);
        String etag = "\"" + id + "-" + imageInfo.getIv() + "\"";
        return buildImageFileResponse(imageInfo, imageService.getImageFile(id, userId),
                imageInfo.getMimeType(), etag, request);
    }

    /**
     * 获取缩略图文件
     * 服务端解码后返回缩略图字节；无缩略图时回退原图
     */
    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Resource> getThumbnailFile(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        ImageResponse imageInfo = imageService.getImageById(id, userId);
        String etag = "\"" + id + "-" + imageInfo.getIv() + "\"";
        String mimeType = Boolean.TRUE.equals(imageInfo.getHasThumbnail())
                ? "image/jpeg" : imageInfo.getMimeType();
        return buildImageFileResponse(imageInfo, imageService.getThumbnailFile(id, userId),
                mimeType, etag, request);
    }

    /**
     * 图片字节响应 + 缓存策略：
     * 公开图 public + 长 max-age（1 天内直接用缓存，过期后 304）；
     * 私密图 private + 每次 304 重校验（不重复传字节）。
     */
    private ResponseEntity<Resource> buildImageFileResponse(
            ImageResponse imageInfo,
            Resource resource,
            String mimeType,
            String etag,
            HttpServletRequest request) {
        MediaType mediaType = MediaType.parseMediaType(
                mimeType != null ? mimeType : "application/octet-stream");
        String cacheControl = cacheControl(imageInfo.getVisibility());

        if (etag.equals(request.getHeader(HttpHeaders.IF_NONE_MATCH))) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                    .header("X-Content-Type-Options", "nosniff")
                    .build();
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .eTag(etag)
                .header(HttpHeaders.CACHE_CONTROL, cacheControl)
                // 禁止浏览器嗅探内容类型：上传侧已按魔数校验，这里是纵深防御
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    /** 公开图 1 天缓存；私密图仅私有缓存 + 304 重校验 */
    private String cacheControl(Integer visibility) {
        if (Integer.valueOf(1).equals(visibility)) {
            return "public, max-age=86400, must-revalidate";
        }
        return "private, max-age=0, must-revalidate";
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteImage(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        imageService.deleteImage(id, userId);
        return Result.success("删除成功", null);
    }

    /**
     * 更新图片可见性
     */
    @PutMapping("/{id}/visibility")
    public Result<ImageResponse> updateVisibility(
            @PathVariable Long id,
            @RequestParam Integer visibility,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        ImageResponse response = imageService.updateVisibility(id, userId, visibility);
        return Result.success("更新成功", response);
    }

    /**
     * 校正图片真实像素尺寸
     * 前端解码后发现库中尺寸与实际不符时调用，仅所有者可改
     * 用 PUT 而非 PATCH：CORS 白名单未放行 PATCH，且语义上是整体替换该子资源
     */
    @PutMapping("/{id}/dimensions")
    public Result<Void> updateDimensions(
            @PathVariable Long id,
            @Valid @RequestBody ImageDimensionsRequest body,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        imageService.updateDimensions(id, userId, body.getWidth(), body.getHeight());
        return Result.success("更新成功", null);
    }

    /**
     * 获取图片所属相册ID列表
     */
    @GetMapping("/{id}/albums")
    public Result<java.util.List<Long>> getImageAlbums(
            @PathVariable Long id,
            HttpServletRequest request) {
        java.util.List<Long> albumIds = imageService.getImageAlbumIds(id);
        return Result.success(albumIds);
    }

    /**
     * 获取图片统计信息
     */
    @GetMapping("/stats")
    public Result<Map<String, Long>> getStats(HttpServletRequest request) {
        Long userId = getUserId(request);
        Map<String, Long> stats = new HashMap<>();
        stats.put("myImages", imageService.countUserImages(userId));
        stats.put("publicImages", imageService.countPublicImages());
        return Result.success(stats);
    }

    /**
     * 获取存储空间统计：数据盘容量 + 当前用户照片占用
     */
    @GetMapping("/storage")
    public Result<StorageStatsResponse> getStorageStats(HttpServletRequest request) {
        Long userId = getUserId(request);
        return Result.success(imageService.getStorageStats(userId));
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
