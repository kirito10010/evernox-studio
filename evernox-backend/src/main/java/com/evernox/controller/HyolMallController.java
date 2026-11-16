package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolMallImportResponse;
import com.evernox.dto.HyolMallItemResponse;
import com.evernox.dto.HyolMallPeriodResponse;
import com.evernox.service.HyolMallService;
import com.evernox.util.ImageTypeValidator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 火影忍者OL 活动详情 · 商城特惠控制器
 */
@RestController
@RequestMapping("/hyol/mall")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class HyolMallController {

    private final HyolMallService mallService;
    private final StorageConfig storageConfig;

    /** 期列表（含 7 天逐日概览），公开只读 */
    @GetMapping("/periods")
    public Result<List<HyolMallPeriodResponse>> periods() {
        return Result.success(mallService.periods());
    }

    /** 某期商品；day 为空时返回整期 */
    @GetMapping("/items")
    public Result<List<HyolMallItemResponse>> items(
            @RequestParam Long periodId,
            @RequestParam(required = false) String day) {
        return Result.success(mallService.items(periodId, day));
    }

    /** 商品图标：<img> 直接引用，无鉴权头，需公开 */
    @GetMapping("/image/{filename}")
    public ResponseEntity<byte[]> image(@PathVariable String filename) {
        // 防路径穿越
        if (filename == null || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        Path file = storageConfig.getMallImagePath().resolve(filename);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
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

    /** 导入当日数据（JSON + 商品图标），仅管理员 */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('admin')")
    public Result<HyolMallImportResponse> importData(
            @RequestParam("manifest") MultipartFile manifest,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        return Result.success("导入完成", mallService.importFolder(manifest, images));
    }

    /** 删除整期（含全部商品），仅管理员 */
    @DeleteMapping("/period/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> deletePeriod(@PathVariable Long id) {
        mallService.deletePeriod(id);
        return Result.success("已删除该期", null);
    }

    /** 删除某一天的导入数据，仅管理员；删完为空则整期一并删除 */
    @DeleteMapping("/day")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> deleteDay(@RequestParam Long periodId,
                                  @RequestParam String day) {
        mallService.deleteDay(periodId, day);
        return Result.success("已删除当天数据", null);
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
