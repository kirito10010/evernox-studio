package com.evernox.service.impl;

import com.evernox.config.ReleaseProperties;
import com.evernox.config.StorageConfig;
import com.evernox.dto.UploadInitResponse;
import com.evernox.entity.ReleaseAsset;
import com.evernox.entity.ReleaseProject;
import com.evernox.exception.BusinessException;
import com.evernox.repository.ReleaseAssetRepository;
import com.evernox.repository.ReleaseProjectRepository;
import com.evernox.service.ReleaseStorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 版本附件磁盘存储实现。
 *
 * 约定：
 * - 分片临时目录：data/releases/.uploads/{uploadId}/
 * - 正式文件：data/releases/{projectId}/{uuid}/{fileName}
 * - meta.json 让服务重启后仍可完成上传
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseStorageServiceImpl implements ReleaseStorageService {

    private static final long MIN_FREE_BYTES = 200L * 1024 * 1024;
    private static final int MAX_CHUNKS = 10000;
    private static final int MAX_FILE_NAME_LENGTH = 180;
    private static final Pattern INVALID_NAME_CHARS =
            Pattern.compile("[<>:\"/\\\\|?*\\x00-\\x1F]");
    private static final Pattern WINDOWS_DEVICE =
            Pattern.compile("^(COM[1-9]|LPT[1-9])$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL", "CLOCK$"
    );

    private final StorageConfig storageConfig;
    private final ReleaseProperties releaseProperties;
    private final ReleaseAssetRepository assetRepository;
    private final ReleaseProjectRepository projectRepository;
    private final ObjectMapper objectMapper;

    @Override
    public String sanitizeFileName(String rawFileName) {
        if (!StringUtils.hasText(rawFileName)) {
            throw new BusinessException("文件名不能为空");
        }
        if (rawFileName.contains("/") || rawFileName.contains("\\")) {
            throw new BusinessException("文件名不能包含路径分隔符");
        }
        String name = rawFileName.trim();
        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            throw new BusinessException("文件名不合法");
        }
        if (name.length() > MAX_FILE_NAME_LENGTH) {
            throw new BusinessException("文件名不能超过 " + MAX_FILE_NAME_LENGTH + " 个字符");
        }
        if (INVALID_NAME_CHARS.matcher(name).find()) {
            throw new BusinessException("文件名包含 Windows 不允许的字符");
        }
        if (name.endsWith(".") || name.endsWith(" ")) {
            throw new BusinessException("文件名不能以空格或点结尾");
        }
        if ("latest.yml".equalsIgnoreCase(name) || "latest.yaml".equalsIgnoreCase(name)) {
            throw new BusinessException("latest.yml 由系统自动生成，不能作为附件上传");
        }

        int dot = name.indexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name;
        String upper = stem.toUpperCase(Locale.ROOT);
        if (RESERVED_NAMES.contains(upper) || WINDOWS_DEVICE.matcher(upper).matches()) {
            throw new BusinessException("文件名使用了 Windows 保留名");
        }
        return name;
    }

    @Override
    public Path resolve(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            throw new BusinessException("附件路径为空");
        }
        Path base = storageConfig.getReleaseRoot().toAbsolutePath().normalize();
        Path file = storageConfig.getDataPath().resolve(storagePath).toAbsolutePath().normalize();
        if (!file.startsWith(base)) {
            throw new BusinessException("非法的附件路径");
        }
        return file;
    }

    @Override
    public UploadInitResponse initUpload(Long projectId, String fileName, long fileSize, Long ownerId) {
        ReleaseProject project = projectRepository.selectById(projectId);
        if (project == null || ownerId == null || !ownerId.equals(project.getOwnerId())) {
            throw new BusinessException("项目不存在");
        }

        String safeName = sanitizeFileName(fileName);
        if (fileSize <= 0) {
            throw new BusinessException("文件大小必须大于 0");
        }
        if (fileSize > releaseProperties.getMaxFileSize()) {
            throw new BusinessException("单个附件不能超过 "
                    + (releaseProperties.getMaxFileSize() / 1024 / 1024) + "MB");
        }

        long chunkSize = releaseProperties.getChunkSize();
        if (chunkSize <= 0) {
            throw new BusinessException("服务端分片配置错误");
        }
        long chunks = (fileSize + chunkSize - 1) / chunkSize;
        if (chunks > MAX_CHUNKS) {
            throw new BusinessException("分片数量过多，请增大分片大小");
        }

        Path uploadRoot = storageConfig.getReleaseUploadRoot();
        try {
            FileStore store = Files.getFileStore(uploadRoot);
            if (store.getUsableSpace() < fileSize + MIN_FREE_BYTES) {
                throw new BusinessException("服务器磁盘空间不足，无法接收该文件");
            }
        } catch (IOException e) {
            log.warn("读取磁盘剩余空间失败: {}", e.getMessage());
        }

        String uploadId = UUID.randomUUID().toString();
        Path dir = storageConfig.getReleaseUploadDir(uploadId);
        try {
            Files.createDirectories(dir);
            UploadMeta meta = new UploadMeta(uploadId, projectId, ownerId, safeName,
                    fileSize, chunkSize, (int) chunks, System.currentTimeMillis());

            objectMapper.writeValue(dir.resolve("meta.json").toFile(), meta);
        } catch (IOException e) {
            deleteRecursively(dir);
            throw new BusinessException("初始化上传失败，请重试");
        }
        return new UploadInitResponse(uploadId, chunkSize, (int) chunks);
    }

    @Override
    public int saveChunk(String uploadId, int index, MultipartFile chunk, Long ownerId) {
        UploadMeta meta = readMeta(uploadId);
        if (meta.getOwnerId() == null || !meta.getOwnerId().equals(ownerId)) {
            throw new BusinessException("上传会话不存在");
        }
        if (index < 0 || index >= meta.getTotalChunks()) {

            throw new BusinessException("分片序号越界");
        }
        if (chunk == null || chunk.isEmpty()) {
            throw new BusinessException("分片内容为空");
        }
        if (chunk.getSize() > meta.getChunkSize()
                || chunk.getSize() > releaseProperties.getMaxChunkSize()) {
            throw new BusinessException("分片大小超过限制");
        }

        Path dir = storageConfig.getReleaseUploadDir(uploadId);
        Path target = dir.resolve(index + ".part");
        try (InputStream in = chunk.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("分片保存失败，请重试");
        }
        return countParts(dir);
    }

    @Override
    public ReleaseAsset completeUpload(String uploadId, Long ownerId) {
        requireValidUploadId(uploadId);
        ReleaseAsset existing = assetRepository.findByUploadId(uploadId);
        if (existing != null) {
            ReleaseProject project = projectRepository.selectById(existing.getProjectId());
            if (project == null || ownerId == null || !ownerId.equals(project.getOwnerId())) {
                throw new BusinessException("上传会话不存在");
            }
            return existing;
        }

        UploadMeta meta = readMeta(uploadId);
        Long projectId = meta.getProjectId();
        ReleaseProject project = projectId == null ? null : projectRepository.selectById(projectId);
        if (project == null || meta.getOwnerId() == null || !meta.getOwnerId().equals(ownerId)) {
            throw new BusinessException("上传会话不存在");
        }


        Path dir = storageConfig.getReleaseUploadDir(uploadId);
        long totalBytes = 0;
        for (int i = 0; i < meta.getTotalChunks(); i++) {
            Path part = dir.resolve(i + ".part");
            if (!Files.exists(part)) {
                throw new BusinessException("分片缺失: " + i);
            }
            try {
                totalBytes += Files.size(part);
            } catch (IOException e) {
                throw new BusinessException("读取分片失败，请重试");
            }
        }
        if (totalBytes != meta.getFileSize()) {
            throw new BusinessException("分片总大小不一致，请重新上传");
        }

        Path merged = dir.resolve("merged.tmp");
        String sha512 = mergeAndHash(dir, meta, merged);

        Path assetDir = storageConfig.getReleaseAssetDir(projectId, UUID.randomUUID().toString())
                .toAbsolutePath().normalize();
        Path target = assetDir.resolve(meta.getFileName()).normalize();
        if (!target.startsWith(assetDir)) {
            throw new BusinessException("非法的附件路径");
        }

        try {
            Files.createDirectories(assetDir);
            Files.move(merged, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietlyPath(merged);
            throw new BusinessException("附件落盘失败，请重试");
        }

        String storagePath = storageConfig.getDataPath().toAbsolutePath().normalize()
                .relativize(target).toString().replace('\\', '/');
        ReleaseAsset asset = ReleaseAsset.builder()
                .projectId(projectId)
                .releaseId(null)
                .uploadId(uploadId)
                .fileName(meta.getFileName())
                .size(meta.getFileSize())
                .sha512(sha512)
                .storagePath(storagePath)
                .downloadCount(0L)
                .deleted(0)
                .build();
        try {
            assetRepository.insert(asset);
        } catch (DuplicateKeyException e) {
            deleteQuietlyPath(target);
            ReleaseAsset duplicated = assetRepository.findByUploadId(uploadId);
            if (duplicated != null) {
                return duplicated;
            }
            throw new BusinessException("上传会话已使用");
        } catch (RuntimeException e) {
            deleteQuietlyPath(target);
            throw e;
        }

        deleteRecursively(dir);
        log.info("附件上传完成: id={}, project={}, name={}, size={}",
                asset.getId(), projectId, meta.getFileName(), meta.getFileSize());
        return asset;
    }

    @Override
    public void cancelUpload(String uploadId, Long ownerId) {
        requireValidUploadId(uploadId);
        UploadMeta meta = readMeta(uploadId);
        if (meta.getOwnerId() == null || !meta.getOwnerId().equals(ownerId)) {
            throw new BusinessException("上传会话不存在");
        }
        deleteRecursively(storageConfig.getReleaseUploadDir(uploadId));
    }

    @Override
    public void deleteStagedAsset(Long assetId, Long ownerId) {
        ReleaseAsset asset = assetRepository.selectById(assetId);
        if (asset == null) {
            throw new BusinessException("附件不存在");
        }
        ReleaseProject project = projectRepository.selectById(asset.getProjectId());
        if (project == null || ownerId == null || !ownerId.equals(project.getOwnerId())) {
            throw new BusinessException("附件不存在");
        }
        if (asset.getReleaseId() != null) {
            throw new BusinessException("附件已加入版本，请通过保存版本移除");
        }
        assetRepository.deleteById(assetId);
        deleteQuietly(asset.getStoragePath());
    }


    @Override
    public void deleteQuietly(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }
        try {
            Path file = resolve(storagePath);
            deleteQuietlyPath(file);
            pruneEmptyParents(file.getParent());
        } catch (Exception e) {
            log.warn("删除附件文件失败: path={}, error={}", storagePath, e.getMessage());
        }
    }


    @Override
    public void cleanupUploadDirs(LocalDateTime before) {
        Path root = storageConfig.getReleaseUploadRoot();
        if (!Files.isDirectory(root)) {
            return;
        }
        Instant threshold = before.atZone(ZoneId.systemDefault()).toInstant();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    long createdAt = readCreatedAt(dir);
                    if (Instant.ofEpochMilli(createdAt).isBefore(threshold)) {
                        deleteRecursively(dir);
                        log.info("清理过期上传目录: {}", dir.getFileName());
                    }
                } catch (Exception e) {
                    log.warn("清理上传目录失败: {}, {}", dir, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("遍历上传目录失败: {}", e.getMessage());
        }
    }

    private String mergeAndHash(Path dir, UploadMeta meta, Path merged) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 缺少 SHA-512 实现", e);
        }

        long written = 0;
        byte[] buffer = new byte[1024 * 1024];
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(merged))) {
            for (int i = 0; i < meta.getTotalChunks(); i++) {
                Path part = dir.resolve(i + ".part");
                try (InputStream in = Files.newInputStream(part)) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        written += read;
                    }
                }
            }
        } catch (IOException e) {
            deleteQuietlyPath(merged);
            throw new BusinessException("合并分片失败，请重试");
        }
        if (written != meta.getFileSize()) {
            deleteQuietlyPath(merged);
            throw new BusinessException("分片总大小不一致，请重新上传");
        }
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    private UploadMeta readMeta(String uploadId) {
        requireValidUploadId(uploadId);
        Path dir = storageConfig.getReleaseUploadDir(uploadId);
        Path metaPath = dir.resolve("meta.json");
        if (!Files.isRegularFile(metaPath)) {
            throw new BusinessException("上传会话不存在或已过期");
        }
        try {
            UploadMeta meta = objectMapper.readValue(metaPath.toFile(), UploadMeta.class);
            if (meta.getTotalChunks() <= 0 || meta.getFileSize() <= 0) {
                throw new BusinessException("上传会话数据损坏");
            }
            return meta;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("读取上传会话失败");
        }
    }

    private long readCreatedAt(Path dir) {
        Path metaPath = dir.resolve("meta.json");
        try {
            if (Files.isRegularFile(metaPath)) {
                UploadMeta meta = objectMapper.readValue(metaPath.toFile(), UploadMeta.class);
                return meta.getCreatedAt();
            }
        } catch (IOException e) {
            log.debug("读取上传 meta 失败，回退目录时间: {}", e.getMessage());
        }
        try {
            return Files.getLastModifiedTime(dir).toMillis();
        } catch (IOException e) {
            return System.currentTimeMillis();
        }
    }

    private int countParts(Path dir) {
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return (int) stream.filter(p -> p.getFileName().toString().endsWith(".part")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private void requireValidUploadId(String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            throw new BusinessException("uploadId 不能为空");
        }
        try {
            UUID.fromString(uploadId);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("非法的上传会话");
        }
    }

    private void deleteQuietlyPath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("删除文件失败: {}, {}", path, e.getMessage());
        }
    }

    /** 删除附件后顺带清掉空的 uuid 目录与项目目录，避免长期堆积空目录 */
    private void pruneEmptyParents(Path dir) {
        Path root = storageConfig.getReleaseRoot().toAbsolutePath().normalize();
        Path current = dir == null ? null : dir.toAbsolutePath().normalize();
        while (current != null && current.startsWith(root) && !current.equals(root)) {
            try (Stream<Path> stream = Files.list(current)) {
                if (stream.findAny().isPresent()) {
                    return;
                }
            } catch (IOException e) {
                return;
            }
            try {
                Files.deleteIfExists(current);
            } catch (IOException e) {
                return;
            }
            current = current.getParent();
        }
    }


    private void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException e) {
                    log.warn("删除失败: {}, {}", p, e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("遍历删除失败: {}, {}", path, e.getMessage());
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UploadMeta {
        private String uploadId;
        private Long projectId;
        private Long ownerId;
        private String fileName;

        private long fileSize;
        private long chunkSize;
        private int totalChunks;
        private long createdAt;
    }
}
