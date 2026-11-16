package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.HyolCodexImportResponse;
import com.evernox.service.HyolCodexService;
import com.evernox.util.ImageTypeValidator;
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
import java.util.Map;
import java.util.Objects;

/**
 * 忍者图鉴 · 抓包导入控制器
 */
@RestController
@RequestMapping("/hyol/codex")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class HyolCodexController {

    private final HyolCodexService codexService;

    /** 数据集原文（公开，ETag + 5 分钟缓存） */
    @GetMapping("/dataset")
    public ResponseEntity<byte[]> dataset() {
        byte[] data = codexService.dataset();
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        long lastModified = codexService.datasetLastModified();
        String etag = "\"" + lastModified + "-" + data.length + "\"";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ETAG, etag)
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=300")
                .body(data);
    }

    /** 导入元信息（公开） */
    @GetMapping("/meta")
    public Result<Map<String, Object>> meta() {
        Map<String, Object> meta = codexService.meta();
        if (meta == null) {
            return Result.success(null);
        }
        return Result.success(meta);
    }

    /** 导入抓包文件夹（仅管理员） */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('admin')")
    public Result<HyolCodexImportResponse> importFolder(
            @RequestParam("files") List<MultipartFile> files) {
        return Result.success("导入完成", codexService.importFolder(files));
    }

    /** 数据集立绘 / 头像（公开） */
    @GetMapping("/image/{filename}")
    public ResponseEntity<byte[]> image(@PathVariable String filename) {
        if (filename == null || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            return ResponseEntity.badRequest().build();
        }
        Path file = codexService.resolveImage(filename);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            byte[] bytes = Files.readAllBytes(file);
            String mime = ImageTypeValidator.detectMimeType(bytes);
            MediaType mediaType = mime != null ? MediaType.parseMediaType(mime) : mediaTypeOf(filename);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                    .body(bytes);
        } catch (IOException e) {
            return ResponseEntity.status(500).build();
        }
    }

    /** 清空数据集（仅管理员），前端回退静态数据 */
    @DeleteMapping("/dataset")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> deleteDataset() {
        codexService.deleteDataset();
        return Result.success("已清空图鉴数据", null);
    }

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
