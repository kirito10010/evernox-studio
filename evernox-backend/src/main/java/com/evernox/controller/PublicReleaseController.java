package com.evernox.controller;

import com.evernox.entity.ReleaseAsset;
import com.evernox.service.ReleaseService;
import com.evernox.service.ReleaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 公开的版本发布更新源（启动器使用，不鉴权）。
 *
 * BASE = /api/release/public/{slug}
 * - GET {BASE}/latest.yml
 * - GET {BASE}/{fileName}
 */
@Slf4j
@RestController
@RequestMapping("/release/public")
@RequiredArgsConstructor
public class PublicReleaseController {

    private static final MediaType YAML = MediaType.parseMediaType("text/yaml;charset=UTF-8");

    private final ReleaseService releaseService;
    private final ReleaseStorageService storageService;

    @GetMapping("/{slug}/latest.yml")
    @SuppressWarnings("null")
    public ResponseEntity<String> latestYml(@PathVariable String slug) {
        String yml = releaseService.buildPublicLatestYml(slug);
        if (yml == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(YAML)
                .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(yml);
    }

    @GetMapping("/{slug}/{fileName:.+}")
    @SuppressWarnings("null")
    public ResponseEntity<Resource> download(@PathVariable String slug,
                                             @PathVariable String fileName,
                                             @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        ReleaseAsset asset = releaseService.findPublicAsset(slug, fileName);
        if (asset == null) {
            return ResponseEntity.notFound().build();
        }

        Path path;
        try {
            path = storageService.resolve(asset.getStoragePath());
        } catch (RuntimeException e) {
            log.warn("公开附件路径非法: id={}, path={}", asset.getId(), asset.getStoragePath());
            return ResponseEntity.notFound().build();
        }
        if (!Files.isReadable(path)) {
            log.error("公开附件文件缺失: id={}, path={}", asset.getId(), path);
            return ResponseEntity.notFound().build();
        }

        if (!StringUtils.hasText(range)) {
            releaseService.incrementDownload(asset.getId());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(asset.getFileName(), StandardCharsets.UTF_8)
                                .build().toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path));
    }
}
