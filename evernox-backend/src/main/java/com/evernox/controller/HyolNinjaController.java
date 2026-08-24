package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolNinjaResponse;
import com.evernox.dto.HyolRefreshResponse;
import com.evernox.service.HyolNinjaService;
import com.evernox.util.ImageTypeValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * 火影忍者OL忍者图鉴控制器（用户侧）
 */
@RestController
@RequestMapping("/hyol/ninja")
@RequiredArgsConstructor
public class HyolNinjaController {

    private final HyolNinjaService ninjaService;
    private final StorageConfig storageConfig;

    @GetMapping("/list")
    public Result<IPage<HyolNinjaResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "24") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String attr,
            @RequestParam(required = false) String hurtType,
            @RequestParam(required = false) String chaseStatus,
            @RequestParam(required = false) String hurtStatus,
            @RequestParam(required = false) String rare) {
        return Result.success(ninjaService.list(page, size, keyword, attr, hurtType, chaseStatus, hurtStatus, rare));
    }

    @PostMapping("/refresh")
    @PreAuthorize("hasRole('admin')")
    public Result<HyolRefreshResponse> refresh() {
        return Result.success("刷新完成", ninjaService.refresh());
    }

    /** 忍者本地图片（头像/技能图标 <img> 直接引用，无鉴权头，需公开） */
    @GetMapping("/image/{filename}")
    public ResponseEntity<byte[]> image(@PathVariable String filename) {
        // 防路径穿越
        if (filename == null || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        Path file = storageConfig.getDataPath().resolve("hyol-ninja").resolve(filename);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            // 优先按实际字节识别类型：压缩后可能是 JPEG，扩展名未必对得上
            String mime = ImageTypeValidator.detectMimeType(bytes);
            MediaType mediaType = mime != null ? MediaType.parseMediaType(mime) : mediaTypeOf(filename);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400, must-revalidate")
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    @NonNull
    private MediaType mediaTypeOf(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) {
            return Objects.requireNonNull(MediaType.IMAGE_PNG);
        }
        if (lower.endsWith(".gif")) {
            return Objects.requireNonNull(MediaType.IMAGE_GIF);
        }
        if (lower.endsWith(".webp")) {
            return MediaType.parseMediaType("image/webp");
        }
        if (lower.endsWith(".bmp")) {
            return MediaType.parseMediaType("image/bmp");
        }
        return Objects.requireNonNull(MediaType.IMAGE_JPEG);
    }
}
