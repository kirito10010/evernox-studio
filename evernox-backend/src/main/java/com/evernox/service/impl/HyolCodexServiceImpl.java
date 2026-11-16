package com.evernox.service.impl;

import com.evernox.codex.CaptureReader;
import com.evernox.codex.CodexBuilder;
import com.evernox.codex.CodexDataset;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolCodexImportResponse;
import com.evernox.exception.BusinessException;
import com.evernox.service.HyolCodexService;
import com.evernox.util.ImageTypeValidator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

/**
 * 忍者图鉴 · 抓包导入实现。
 *
 * <p>导入全程串行（同一时刻只允许一个导入），数据集先写临时文件再原子替换，
 * 读者永远不会看到半份 JSON。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyolCodexServiceImpl implements HyolCodexService {

    private static final String DATASET_FILE = "ninjas.json";
    private static final String META_FILE = "meta.json";
    private static final int MIN_NINJAS = 100;
    private static final int MAX_WARNINGS = 50;

    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;

    private final ReentrantLock importLock = new ReentrantLock();
    private volatile CodexBuilder.Legacy legacyCache;

    @Override
    public byte[] dataset() {
        Path file = datasetPath();
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            log.error("读取忍者图鉴数据集失败", e);
            return null;
        }
    }

    @Override
    public long datasetLastModified() {
        Path file = datasetPath();
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    @Override
    public Map<String, Object> meta() {
        Path file = storageConfig.getCodexPath().resolve(META_FILE);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return objectMapper.readValue(file.toFile(), new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IOException e) {
            log.error("读取忍者图鉴元信息失败", e);
            return null;
        }
    }

    @Override
    public HyolCodexImportResponse importFolder(List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            throw new BusinessException("请选择抓包文件夹");
        }
        importLock.lock();
        Path temp = null;
        try {
            temp = Files.createTempDirectory(storageConfig.getCodexPath(), "import-");
            List<String> warnings = new ArrayList<>();
            int accepted = saveUploads(files, temp, warnings);
            if (accepted == 0) {
                throw new BusinessException("没有可导入的 http-*.jsonl 文件（netlog 与 json 不需要上传）");
            }

            CaptureReader.Bundle bundle = CaptureReader.read(temp, objectMapper);
            warnings.addAll(bundle.warnings());
            CodexBuilder.Result result = CodexBuilder.build(bundle, legacy());
            warnings.addAll(result.warnings());
            CodexDataset dataset = result.dataset();
            if (dataset.getNinjas().size() < MIN_NINJAS) {
                throw new BusinessException("抓包不完整：只解析出 " + dataset.getNinjas().size()
                        + " 位忍者，已保留原有数据");
            }
            dataset.setGeneratedAt(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            int imageCount = writeImages(bundle, warnings);
            writeDataset(dataset);
            writeMeta(dataset, bundle, accepted, imageCount, warnings);

            log.info("忍者图鉴导入完成：build={} ninjas={} skills={} forms={} images={}",
                    bundle.build(), dataset.getStats().getNinjas(), dataset.getStats().getSkills(),
                    dataset.getStats().getForms(), imageCount);
            return HyolCodexImportResponse.builder()
                    .build(bundle.build())
                    .ninjaCount(dataset.getStats().getNinjas())
                    .skillCount(dataset.getStats().getSkills())
                    .formCount(dataset.getStats().getForms())
                    .imageCount(imageCount)
                    .warnings(limit(warnings))
                    .build();
        } catch (IOException e) {
            throw new BusinessException("导入失败：" + e.getMessage());
        } finally {
            deleteQuietly(temp);
            importLock.unlock();
        }
    }

    @Override
    public void deleteDataset() {
        importLock.lock();
        try {
            Files.deleteIfExists(datasetPath());
            Files.deleteIfExists(storageConfig.getCodexPath().resolve(META_FILE));
            clearDirectory(storageConfig.getCodexPortraitPath());
            clearDirectory(storageConfig.getCodexHeadPath());
        } catch (IOException e) {
            throw new BusinessException("清空数据集失败：" + e.getMessage());
        } finally {
            importLock.unlock();
        }
    }

    @Override
    public Path resolveImage(String filename) {
        Path portrait = storageConfig.getCodexPortraitPath().resolve(filename);
        if (Files.isRegularFile(portrait)) {
            return portrait;
        }
        Path head = storageConfig.getCodexHeadPath().resolve(filename);
        return Files.isRegularFile(head) ? head : null;
    }

    private Path datasetPath() {
        return storageConfig.getCodexPath().resolve(DATASET_FILE);
    }

    private int saveUploads(List<MultipartFile> files, Path temp, List<String> warnings) throws IOException {
        int accepted = 0;
        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String original = file.getOriginalFilename();
            String name = original == null ? "" : original;
            name = name.replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1);
            String lower = name.toLowerCase();
            if (!lower.endsWith(".jsonl")) {
                warnings.add("已忽略 " + name);
                continue;
            }
            Path target = Objects.requireNonNull(temp.resolve(name));
            file.transferTo(target);
            accepted++;
        }
        return accepted;
    }

    private int writeImages(CaptureReader.Bundle bundle, List<String> warnings) throws IOException {
        int count = 0;
        count += writeImageGroup(bundle.portraits(), storageConfig.getCodexPortraitPath(), warnings);
        count += writeImageGroup(bundle.heads(), storageConfig.getCodexHeadPath(), warnings);
        return count;
    }

    private int writeImageGroup(Map<String, byte[]> images, Path dir, List<String> warnings) throws IOException {
        int count = 0;
        for (Map.Entry<String, byte[]> entry : images.entrySet()) {
            byte[] bytes = entry.getValue();
            if (!ImageTypeValidator.isSupportedImage(bytes)) {
                warnings.add("跳过非图片资源：" + entry.getKey());
                continue;
            }
            String name = entry.getKey().replaceAll("[^0-9A-Za-z._-]", "_");
            Files.write(dir.resolve(name), bytes);
            count++;
        }
        return count;
    }

    private void writeDataset(CodexDataset dataset) throws IOException {
        Path target = datasetPath();
        Path tmp = target.resolveSibling(DATASET_FILE + ".tmp");
        Files.write(tmp, objectMapper.writeValueAsBytes(dataset));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeMeta(CodexDataset dataset, CaptureReader.Bundle bundle, int fileCount,
                           int imageCount, List<String> warnings) throws IOException {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("build", bundle.build());
        meta.put("generatedAt", dataset.getGeneratedAt());
        meta.put("stats", dataset.getStats());
        meta.put("fileCount", fileCount);
        meta.put("imageCount", imageCount);
        meta.put("warnings", limit(warnings));
        Path target = storageConfig.getCodexPath().resolve(META_FILE);
        Path tmp = target.resolveSibling(META_FILE + ".tmp");
        Files.write(tmp, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(meta));
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private CodexBuilder.Legacy legacy() {
        CodexBuilder.Legacy cached = legacyCache;
        if (cached != null) {
            return cached;
        }
        try (InputStream in = getClass().getResourceAsStream("/codex/legacy.json")) {
            if (in == null) {
                legacyCache = CodexBuilder.Legacy.empty();
                return legacyCache;
            }
            JsonNode root = objectMapper.readTree(in);
            Map<String, Integer> rare = root.has("rare")
                    ? objectMapper.convertValue(root.get("rare"), new TypeReference<Map<String, Integer>>() {
            })
                    : Map.of();
            Map<String, String> skillNames = root.has("skillNames")
                    ? objectMapper.convertValue(root.get("skillNames"), new TypeReference<Map<String, String>>() {
            })
                    : Map.of();
            legacyCache = new CodexBuilder.Legacy(rare, skillNames);
            return legacyCache;
        } catch (IOException e) {
            log.warn("读取历史稀有度 / 技能名失败，将使用默认值：{}", e.getMessage());
            legacyCache = CodexBuilder.Legacy.empty();
            return legacyCache;
        }
    }

    private static List<String> limit(List<String> warnings) {
        if (warnings == null || warnings.isEmpty()) {
            return List.of();
        }
        return warnings.size() <= MAX_WARNINGS ? List.copyOf(warnings) : List.copyOf(warnings.subList(0, MAX_WARNINGS));
    }

    private static void clearDirectory(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path path : stream.toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(dir)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("清理导入临时目录失败：{}", e.getMessage());
        }
    }
}
