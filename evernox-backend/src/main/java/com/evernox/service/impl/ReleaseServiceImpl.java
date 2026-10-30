package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.ReleaseStatus;
import com.evernox.dto.ReleaseAssetResponse;
import com.evernox.dto.ReleaseDetailResponse;
import com.evernox.dto.ReleaseRequest;
import com.evernox.dto.ReleaseSummaryResponse;
import com.evernox.entity.ProjectRelease;
import com.evernox.entity.ReleaseAsset;
import com.evernox.entity.ReleaseProject;
import com.evernox.exception.BusinessException;
import com.evernox.repository.ProjectReleaseRepository;
import com.evernox.repository.ReleaseAssetRepository;
import com.evernox.repository.ReleaseProjectRepository;
import com.evernox.service.ReleaseService;
import com.evernox.service.ReleaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 版本发布服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseServiceImpl implements ReleaseService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern TAG_PATTERN =
            Pattern.compile("^[vV]?\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?$");
    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final ReleaseProjectRepository projectRepository;
    private final ProjectReleaseRepository releaseRepository;

    private final ReleaseAssetRepository assetRepository;
    private final ReleaseStorageService storageService;


    @Override
    @SuppressWarnings("null")
    public IPage<ReleaseSummaryResponse> list(Long projectId, int page, int size,
                                              String keyword, Integer status, Boolean latest, String sort,
                                              Long ownerId) {

        requireProject(projectId, ownerId);
        int current = Math.max(page, 1);

        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<ProjectRelease> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectRelease::getProjectId, projectId);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ProjectRelease::getTag, kw)
                    .or().like(ProjectRelease::getTitle, kw));
        }
        if (status != null) {
            wrapper.eq(ProjectRelease::getStatus, status);
        }
        if (latest != null) {
            wrapper.eq(ProjectRelease::getIsLatest, latest ? 1 : 0);
        }
        String order = StringUtils.hasText(sort) ? sort : "latest";
        switch (order) {
            case "created" -> wrapper.orderByDesc(ProjectRelease::getCreatedAt);
            case "published" -> wrapper.orderByDesc(ProjectRelease::getPublishedAt);
            case "tag" -> wrapper.orderByAsc(ProjectRelease::getTag);
            default -> wrapper.orderByDesc(ProjectRelease::getIsLatest)
                    .orderByDesc(ProjectRelease::getPublishedAt);
        }
        wrapper.orderByDesc(ProjectRelease::getId);


        IPage<ProjectRelease> raw = releaseRepository.selectPage(new Page<>(current, pageSize), wrapper);
        List<ProjectRelease> records = raw.getRecords();
        if (records.isEmpty()) {
            Page<ReleaseSummaryResponse> empty = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
            empty.setRecords(List.of());
            return empty;
        }

        List<Long> releaseIds = records.stream().map(ProjectRelease::getId).collect(Collectors.toList());
        List<ReleaseAsset> assets = assetRepository.selectList(new LambdaQueryWrapper<ReleaseAsset>()
                .in(ReleaseAsset::getReleaseId, releaseIds));
        Map<Long, List<ReleaseAsset>> byRelease = assets.stream()
                .collect(Collectors.groupingBy(ReleaseAsset::getReleaseId));

        List<ReleaseSummaryResponse> result = records.stream().map(release -> {
            List<ReleaseAsset> list = byRelease.getOrDefault(release.getId(), List.of());
            return ReleaseSummaryResponse.builder()
                    .id(release.getId())
                    .projectId(release.getProjectId())
                    .tag(release.getTag())
                    .title(release.getTitle())
                    .summary(summarize(release.getNotes()))
                    .status(release.getStatus())
                    .latest(Integer.valueOf(1).equals(release.getIsLatest()))
                    .publishedAt(release.getPublishedAt())
                    .createdAt(release.getCreatedAt())
                    .updatedAt(release.getUpdatedAt())
                    .downloadCount(release.getDownloadCount() == null ? 0L : release.getDownloadCount())
                    .assetCount(list.size())

                    .totalSize(list.stream().mapToLong(ReleaseAsset::getSize).sum())
                    .build();
        }).collect(Collectors.toList());

        Page<ReleaseSummaryResponse> pageResult = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        pageResult.setRecords(result);
        return pageResult;
    }

    @Override
    public ReleaseDetailResponse get(Long id, Long ownerId) {
        return toDetail(requireRelease(id, ownerId));
    }

    @Override
    @Transactional
    public ReleaseDetailResponse create(ReleaseRequest request, Long ownerId) {
        if (request.getProjectId() == null) {
            throw new BusinessException("projectId 不能为空");
        }
        ReleaseProject project = requireProject(request.getProjectId(), ownerId);
        String tag = normalizeTag(request.getTag());
        ensureTagUnique(project.getId(), tag, null);

        ProjectRelease release = ProjectRelease.builder()
                .projectId(project.getId())
                .tag(tag)
                .title(request.getTitle().trim())
                .notes(request.getNotes())
                .status(ReleaseStatus.DRAFT.getCode())
                .isLatest(0)
                .downloadCount(0L)
                .createdBy(ownerId)
                .deleted(0)
                .build();
        releaseRepository.insert(release);
        syncAssets(project.getId(), release.getId(), request.getAssetIds());
        log.info("版本发布创建: id={}, project={}, tag={}, owner={}",
                release.getId(), project.getSlug(), tag, ownerId);
        return toDetail(release);
    }

    @Override
    @Transactional
    public ReleaseDetailResponse update(Long id, ReleaseRequest request, Long ownerId) {
        ProjectRelease release = requireRelease(id, ownerId);

        String tag = normalizeTag(request.getTag());

        if (Integer.valueOf(ReleaseStatus.PUBLISHED.getCode()).equals(release.getStatus())
                && !tag.equals(release.getTag())) {
            throw new BusinessException("已发布版本不可修改标签");
        }
        if (!tag.equals(release.getTag())) {
            ensureTagUnique(release.getProjectId(), tag, release.getId());
        }

        release.setTag(tag);
        release.setTitle(request.getTitle().trim());
        release.setNotes(request.getNotes());
        releaseRepository.updateById(release);
        syncAssets(release.getProjectId(), release.getId(), request.getAssetIds());
        return toDetail(release);
    }

    @Override
    @Transactional
    public void delete(Long id, Long ownerId) {
        ProjectRelease release = requireRelease(id, ownerId);

        List<ReleaseAsset> assets = assetsOfRelease(id);
        for (ReleaseAsset asset : assets) {
            assetRepository.deleteById(asset.getId());
            storageService.deleteQuietly(asset.getStoragePath());
        }
        releaseRepository.deleteById(id);
        log.warn("版本已删除: id={}, tag={}, 附件数={}", id, release.getTag(), assets.size());
    }

    @Override
    @Transactional
    public ReleaseDetailResponse publish(Long id, boolean latest, Long ownerId) {
        ProjectRelease release = requireRelease(id, ownerId);

        List<ReleaseAsset> assets = assetsOfRelease(id);
        if (assets.stream().noneMatch(this::isDistributable)) {
            throw new BusinessException("至少上传一个安装包或分发文件后才能发布");
        }

        release.setStatus(ReleaseStatus.PUBLISHED.getCode());
        if (release.getPublishedAt() == null) {
            release.setPublishedAt(LocalDateTime.now());
        }

        ProjectRelease currentLatest = releaseRepository.findLatestByProject(release.getProjectId());
        if (latest || currentLatest == null) {
            releaseRepository.clearLatest(release.getProjectId());
            release.setIsLatest(1);
        }
        releaseRepository.updateById(release);
        log.info("版本已发布: id={}, tag={}, latest={}", id, release.getTag(), release.getIsLatest());
        return toDetail(release);
    }

    @Override
    @Transactional
    public ReleaseDetailResponse setLatest(Long id, boolean latest, Long ownerId) {
        ProjectRelease release = requireRelease(id, ownerId);

        if (!Integer.valueOf(ReleaseStatus.PUBLISHED.getCode()).equals(release.getStatus())) {
            throw new BusinessException("只有已发布版本才能设为最新");
        }
        if (latest) {
            releaseRepository.clearLatest(release.getProjectId());
            release.setIsLatest(1);
        } else {
            release.setIsLatest(0);
        }
        releaseRepository.updateById(release);
        log.info("版本最新标记变更: id={}, tag={}, latest={}", id, release.getTag(), latest);
        return toDetail(release);
    }

    @Override
    public String previewLatestYml(Long id, Long ownerId) {
        ProjectRelease release = requireRelease(id, ownerId);

        if (!Integer.valueOf(ReleaseStatus.PUBLISHED.getCode()).equals(release.getStatus())) {
            throw new BusinessException("草稿不能生成更新清单，请先发布");
        }
        return buildYml(release, assetsOfRelease(id));
    }

    @Override
    @SuppressWarnings("null")
    public String buildPublicLatestYml(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        ReleaseProject project = projectRepository.selectOne(new LambdaQueryWrapper<ReleaseProject>()
                .eq(ReleaseProject::getSlug, slug.trim()));
        if (project == null) {
            return null;
        }
        ProjectRelease latest = releaseRepository.findLatestByProject(project.getId());
        if (latest == null) {
            return null;
        }
        List<ReleaseAsset> assets = assetsOfRelease(latest.getId());
        if (assets.stream().noneMatch(this::isDistributable)) {
            log.error("最新版本缺少可分发附件: releaseId={}", latest.getId());
            return null;
        }
        return buildYml(latest, assets);
    }

    @Override
    public ReleaseAsset findPublicAsset(String slug, String fileName) {
        if (!StringUtils.hasText(slug) || !StringUtils.hasText(fileName)) {
            return null;
        }
        return assetRepository.findPublicAsset(slug.trim(), fileName);
    }

    @Override
    public void incrementDownload(Long assetId) {
        if (assetId == null) {
            return;
        }
        ReleaseAsset asset = assetRepository.selectById(assetId);
        if (asset == null || asset.getReleaseId() == null) {
            return;
        }
        assetRepository.incrementDownload(assetId);
        // 只把安装包/分发文件计入版本下载次数，blockmap 等辅助文件不计
        if (isDistributable(asset)) {
            releaseRepository.incrementDownload(asset.getReleaseId());
        }
    }


    /**
     * 同步版本附件：绑定新增、删除移除项、同版本同名覆盖。
     */
    private void syncAssets(Long projectId, Long releaseId, List<Long> assetIds) {
        LinkedHashSet<Long> desired = assetIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(assetIds);
        List<ReleaseAsset> current = assetsOfRelease(releaseId);

        // 先完整校验，避免校验失败时已经删掉了旧附件文件
        List<ReleaseAsset> targets = new ArrayList<>();
        for (Long assetId : desired) {
            ReleaseAsset asset = assetRepository.selectById(assetId);
            if (asset == null || Integer.valueOf(1).equals(asset.getDeleted())) {
                throw new BusinessException("附件不存在: " + assetId);
            }
            if (!projectId.equals(asset.getProjectId())) {
                throw new BusinessException("附件不属于当前项目");
            }
            if (asset.getReleaseId() != null && !releaseId.equals(asset.getReleaseId())) {
                throw new BusinessException("附件已属于其它版本");
            }
            targets.add(asset);
        }

        Set<String> fileNames = new HashSet<>();
        for (ReleaseAsset asset : targets) {
            if (!fileNames.add(asset.getFileName())) {
                throw new BusinessException("同一版本不能包含两个同名附件: " + asset.getFileName());
            }
        }

        for (ReleaseAsset asset : current) {
            if (!desired.contains(asset.getId())) {
                assetRepository.deleteById(asset.getId());
                storageService.deleteQuietly(asset.getStoragePath());
            }
        }

        for (ReleaseAsset asset : targets) {
            asset.setReleaseId(releaseId);
            assetRepository.updateById(asset);
        }
    }


    @SuppressWarnings("null")
    private ReleaseDetailResponse toDetail(ProjectRelease release) {
        List<ReleaseAsset> assets = assetsOfRelease(release.getId());
        List<ReleaseAssetResponse> responses = assets.stream()
                .map(asset -> ReleaseAssetResponse.builder()
                        .id(asset.getId())
                        .fileName(asset.getFileName())
                        .size(asset.getSize())
                        .sha512(asset.getSha512())
                        .downloadCount(asset.getDownloadCount())
                        .createdAt(asset.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
        return ReleaseDetailResponse.builder()
                .id(release.getId())
                .projectId(release.getProjectId())
                .tag(release.getTag())
                .title(release.getTitle())
                .notes(release.getNotes())
                .status(release.getStatus())
                .latest(Integer.valueOf(1).equals(release.getIsLatest()))
                .publishedAt(release.getPublishedAt())
                .createdAt(release.getCreatedAt())
                .updatedAt(release.getUpdatedAt())
                .downloadCount(release.getDownloadCount() == null ? 0L : release.getDownloadCount())
                .assetCount(responses.size())

                .totalSize(responses.stream().mapToLong(ReleaseAssetResponse::getSize).sum())
                .assets(responses)
                .build();
    }

    @SuppressWarnings("null")
    private List<ReleaseAsset> assetsOfRelease(Long releaseId) {
        return assetRepository.selectList(new LambdaQueryWrapper<ReleaseAsset>()
                .eq(ReleaseAsset::getReleaseId, releaseId)
                .orderByAsc(ReleaseAsset::getId));
    }

    private boolean isDistributable(ReleaseAsset asset) {
        String name = asset.getFileName().toLowerCase(Locale.ROOT);
        return !name.endsWith(".blockmap") && !name.endsWith(".yml") && !name.endsWith(".yaml");
    }

    private String buildYml(ProjectRelease release, List<ReleaseAsset> assets) {
        List<ReleaseAsset> files = assets.stream()
                .filter(this::isDistributable)
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
        if (files.isEmpty()) {
            throw new BusinessException("至少需要一个可分发文件（如 .exe）");
        }

        ReleaseAsset primary = files.get(0);
        String version = release.getTag().replaceFirst("(?i)^v", "");
        LocalDateTime publishedAt = release.getPublishedAt() == null
                ? LocalDateTime.now() : release.getPublishedAt();

        StringBuilder yml = new StringBuilder(512);
        yml.append("version: ").append(version).append('\n');
        yml.append("files:\n");
        for (ReleaseAsset file : files) {
            yml.append("  - url: ").append(yamlQuote(file.getFileName())).append('\n')
                    .append("    sha512: ").append(file.getSha512()).append('\n')
                    .append("    size: ").append(file.getSize()).append('\n');
        }
        yml.append("path: ").append(yamlQuote(primary.getFileName())).append('\n');
        yml.append("sha512: ").append(primary.getSha512()).append('\n');
        yml.append("releaseDate: '")
                .append(ISO_UTC.format(publishedAt.atZone(ZoneId.systemDefault()).toInstant()))
                .append("'\n");
        return yml.toString();
    }

    private String yamlQuote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private String summarize(String notes) {
        if (!StringUtils.hasText(notes)) {
            return null;
        }
        String text = notes.replaceAll("\\s+", " ").trim();
        return text.length() <= 140 ? text : text.substring(0, 140) + "…";
    }

    private String normalizeTag(String tag) {
        if (!StringUtils.hasText(tag)) {
            throw new BusinessException("版本标签不能为空");
        }
        String value = tag.trim();
        if (!TAG_PATTERN.matcher(value).matches()) {
            throw new BusinessException("版本标签需符合 v1.2.3 或 1.2.3-beta.1");
        }
        return value;
    }

    @SuppressWarnings("null")
    private void ensureTagUnique(Long projectId, String tag, Long excludeId) {
        LambdaQueryWrapper<ProjectRelease> wrapper = new LambdaQueryWrapper<ProjectRelease>()
                .eq(ProjectRelease::getProjectId, projectId)
                .eq(ProjectRelease::getTag, tag);
        if (excludeId != null) {
            wrapper.ne(ProjectRelease::getId, excludeId);
        }
        if (releaseRepository.selectCount(wrapper) > 0) {
            throw new BusinessException("该版本标签已存在");
        }
    }

    private ReleaseProject requireProject(Long id, Long ownerId) {
        ReleaseProject project = id == null ? null : projectRepository.selectById(id);
        if (project == null || ownerId == null || !ownerId.equals(project.getOwnerId())) {
            throw new BusinessException("项目不存在");
        }
        return project;
    }

    private ProjectRelease requireRelease(Long id, Long ownerId) {
        ProjectRelease release = id == null ? null : releaseRepository.selectById(id);
        if (release == null) {
            throw new BusinessException("版本不存在");
        }
        ReleaseProject project = projectRepository.selectById(release.getProjectId());
        if (project == null || ownerId == null || !ownerId.equals(project.getOwnerId())) {
            throw new BusinessException("版本不存在");
        }
        return release;
    }
}

