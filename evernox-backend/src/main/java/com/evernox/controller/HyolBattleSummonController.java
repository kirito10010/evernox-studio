package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolBattleImportResponse;
import com.evernox.dto.HyolBattleItemResponse;
import com.evernox.dto.HyolBattlePeriodResponse;
import com.evernox.service.HyolBattleSummonService;
import com.evernox.util.ImageTypeValidator;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 火影忍者OL 活动详情 · 战场的召唤控制器
 */
@RestController
@RequestMapping("/hyol/battle")
@RequiredArgsConstructor
public class HyolBattleSummonController {

    private final HyolBattleSummonService battleService;
    private final StorageConfig storageConfig;

    /** 期列表，公开只读 */
    @GetMapping("/periods")
    public Result<List<HyolBattlePeriodResponse>> periods() {
        return Result.success(battleService.periods());
    }

    /** 某期全部商品，公开只读 */
    @GetMapping("/items")
    public Result<List<HyolBattleItemResponse>> items(@RequestParam Long periodId) {
        return Result.success(battleService.items(periodId));
    }

    /** 商品/技能图标：<img> 直接引用，无鉴权头，需公开 */
    @GetMapping("/image/{filename}")
    public ResponseEntity<byte[]> image(
            @PathVariable String filename,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        if (filename == null || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        Path file = storageConfig.getBattleImagePath().resolve(filename);
        if (!Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        try {
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            long size = Files.size(file);
            String etag = "\"" + Long.toHexString(lastModified) + "-" + Long.toHexString(size) + "\"";
            CacheControl cache = CacheControl.maxAge(0, TimeUnit.SECONDS).mustRevalidate();
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                        .eTag(etag)
                        .cacheControl(cache)
                        .build();
            }
            byte[] bytes = Files.readAllBytes(file);
            String mime = ImageTypeValidator.detectMimeType(bytes);
            MediaType mediaType = mime != null ? MediaType.parseMediaType(mime) : mediaTypeOf(filename);
            return ResponseEntity.ok()
                    .contentType(Objects.requireNonNull(mediaType))
                    .eTag(etag)
                    .lastModified(lastModified)
                    .cacheControl(cache)
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    /** 整期导入（JSON + 图标），仅管理员 */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('admin')")
    public Result<HyolBattleImportResponse> importData(
            @RequestParam("manifest") MultipartFile manifest,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        return Result.success("导入完成", battleService.importFolder(manifest, images));
    }

    /** 删除整期（含阵营与商品），仅管理员 */
    @DeleteMapping("/period/{id}")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> deletePeriod(@PathVariable Long id) {
        battleService.deletePeriod(id);
        return Result.success("已删除该期", null);
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
