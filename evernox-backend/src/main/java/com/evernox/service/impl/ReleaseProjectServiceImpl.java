package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.ReleaseProjectRequest;
import com.evernox.dto.ReleaseProjectResponse;
import com.evernox.entity.ProjectRelease;
import com.evernox.entity.ReleaseAsset;
import com.evernox.entity.ReleaseProject;
import com.evernox.exception.BusinessException;
import com.evernox.repository.ProjectReleaseRepository;
import com.evernox.repository.ReleaseAssetRepository;
import com.evernox.repository.ReleaseProjectRepository;
import com.evernox.service.ReleaseProjectService;
import com.evernox.service.ReleaseStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 发布项目服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReleaseProjectServiceImpl implements ReleaseProjectService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{1,39}$");

    private static final Set<String> RESERVED_SLUGS = Set.of(
            "latest", "latest-yml", "latest.yml", "public", "admin", "api", "assets", "release"
    );

    private final ReleaseProjectRepository projectRepository;
    private final ProjectReleaseRepository releaseRepository;
    private final ReleaseAssetRepository assetRepository;
    private final ReleaseStorageService storageService;

    @Override
    @SuppressWarnings("null")
    public List<ReleaseProjectResponse> list(String keyword, Long ownerId) {
        LambdaQueryWrapper<ReleaseProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReleaseProject::getOwnerId, ownerId);

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(ReleaseProject::getName, kw)
                    .or().like(ReleaseProject::getSlug, kw)
                    .or().like(ReleaseProject::getDescription, kw));
        }
        wrapper.orderByDesc(ReleaseProject::getSort).orderByDesc(ReleaseProject::getUpdatedAt);
        return projectRepository.selectList(wrapper).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ReleaseProjectResponse get(Long id, Long ownerId) {
        return toResponse(requireProject(id, ownerId));
    }


    @Override
    @SuppressWarnings("null")
    public ReleaseProjectResponse create(ReleaseProjectRequest request, Long ownerId) {
        String name = requireName(request.getName());
        String slug = normalizeSlug(request.getSlug());
        if (projectRepository.selectCount(new LambdaQueryWrapper<ReleaseProject>()
                .eq(ReleaseProject::getSlug, slug)) > 0) {
            throw new BusinessException("该 slug 已被使用");
        }

        ReleaseProject project = ReleaseProject.builder()
                .ownerId(ownerId)
                .name(name)
                .slug(slug)
                .description(trimToNull(request.getDescription()))
                .sort(request.getSort() == null ? 0 : request.getSort())
                .deleted(0)
                .build();
        projectRepository.insert(project);
        log.info("发布项目创建: id={}, slug={}, owner={}", project.getId(), slug, ownerId);
        return toResponse(project);
    }


    @Override
    public ReleaseProjectResponse update(Long id, ReleaseProjectRequest request, Long ownerId) {
        ReleaseProject project = requireProject(id, ownerId);

        if (StringUtils.hasText(request.getSlug()) && !request.getSlug().trim().equals(project.getSlug())) {
            throw new BusinessException("slug 创建后不可修改，否则启动器更新地址会失效");
        }

        project.setName(requireName(request.getName()));
        project.setDescription(trimToNull(request.getDescription()));
        if (request.getSort() != null) {
            project.setSort(request.getSort());
        }
        projectRepository.updateById(project);
        return toResponse(project);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public void delete(Long id, String confirmSlug, Long ownerId) {
        ReleaseProject project = requireProject(id, ownerId);

        if (!project.getSlug().equals(confirmSlug)) {
            throw new BusinessException("slug 不匹配，无法删除项目");
        }

        List<ReleaseAsset> assets = assetRepository.selectList(
                new LambdaQueryWrapper<ReleaseAsset>().eq(ReleaseAsset::getProjectId, id));
        for (ReleaseAsset asset : assets) {
            assetRepository.deleteById(asset.getId());
            storageService.deleteQuietly(asset.getStoragePath());
        }

        releaseRepository.delete(new LambdaQueryWrapper<ProjectRelease>()
                .eq(ProjectRelease::getProjectId, id));
        projectRepository.deleteById(id);
        log.warn("发布项目已删除: id={}, slug={}, 附件数={}", id, project.getSlug(), assets.size());
    }

    private ReleaseProjectResponse toResponse(ReleaseProject project) {
        ProjectRelease latest = releaseRepository.findLatestByProject(project.getId());
        return ReleaseProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .slug(project.getSlug())
                .description(project.getDescription())
                .sort(project.getSort())
                .releaseCount(releaseRepository.countByProject(project.getId()))
                .publishedCount(releaseRepository.countPublishedByProject(project.getId()))
                .latestTag(latest == null ? null : latest.getTag())
                .latestPublishedAt(latest == null ? null : latest.getPublishedAt())
                .assetCount(assetRepository.countByProject(project.getId()))
                .storageBytes(assetRepository.sumSizeByProject(project.getId()))
                .downloadTotal(releaseRepository.sumDownloadsByProject(project.getId()))

                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }

    private ReleaseProject requireProject(Long id, Long ownerId) {
        ReleaseProject project = id == null ? null : projectRepository.selectById(id);
        if (project == null || ownerId == null || !ownerId.equals(project.getOwnerId())) {
            throw new BusinessException("项目不存在");
        }
        return project;
    }


    private String requireName(String name) {
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("项目名称不能为空");
        }
        return name.trim();
    }

    private String normalizeSlug(String slug) {
        if (!StringUtils.hasText(slug)) {
            throw new BusinessException("slug 不能为空");
        }
        String value = slug.trim();
        if (!SLUG_PATTERN.matcher(value).matches()) {
            throw new BusinessException("slug 只能使用小写字母、数字和短横线，2-40 个字符");
        }
        if (RESERVED_SLUGS.contains(value)) {
            throw new BusinessException("slug 不能使用保留字: " + value);
        }
        return value;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
