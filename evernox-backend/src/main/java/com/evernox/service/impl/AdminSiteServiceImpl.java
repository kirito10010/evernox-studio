package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.SiteStatus;
import com.evernox.dto.SiteLinkResponse;
import com.evernox.dto.SiteStatsResponse;
import com.evernox.dto.SiteTagRequest;
import com.evernox.dto.SiteTagResponse;
import com.evernox.entity.SiteLink;
import com.evernox.entity.SiteLinkTag;
import com.evernox.entity.SiteTag;
import com.evernox.exception.BusinessException;
import com.evernox.repository.SiteLinkRepository;
import com.evernox.repository.SiteLinkTagRepository;
import com.evernox.repository.SiteTagRepository;
import com.evernox.service.AdminSiteService;
import com.evernox.service.ImageService;
import com.evernox.util.SortColumnResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网站分享审批服务实现（管理员侧）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminSiteServiceImpl implements AdminSiteService {

    private static final int MAX_PAGE_SIZE = 100;

    /** 排序字段白名单：前端传入的字符串绝不能直接拼进 SQL */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "created_at",
            "submittedAt", "submitted_at",
            "reviewedAt", "reviewed_at",
            "title", "title"
    );

    private final SiteLinkRepository siteLinkRepository;
    private final SiteTagRepository siteTagRepository;
    private final SiteLinkTagRepository siteLinkTagRepository;
    private final ImageService imageService;
    private final SiteAssembler siteAssembler;

    @Override
    public IPage<SiteLinkResponse> listSites(int page, int size, Integer status, Long userId,
                                            String keyword, String sortField, String sortOrder) {
        QueryWrapper<SiteLink> wrapper = new QueryWrapper<>();
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            // 嵌套 and(...)：平铺的 or 会把上面的 status/user_id 条件短路掉
            wrapper.and(w -> w.like("title", kw).or().like("url", kw).or().like("description", kw));
        }
        String column = SortColumnResolver.resolve(SORT_COLUMNS, sortField, "submitted_at");
        wrapper.orderBy(true, "asc".equalsIgnoreCase(sortOrder), column);
        wrapper.orderByDesc("id");

        Page<SiteLink> pageParam = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return siteAssembler.convert(siteLinkRepository.selectPage(pageParam, wrapper));
    }

    @Override
    @Transactional
    public void approve(Long id, List<Long> tagIds, Long adminId) {
        SiteLink site = requireSite(id);
        if (site.getStatus() != SiteStatus.PENDING) {
            throw new BusinessException("该分享不在待审批状态");
        }
        Set<Long> validTagIds = requireExistingTags(tagIds);

        // status 作为更新条件：并发双击时第二次影响行数为 0，不会重复审批
        int rows = siteLinkRepository.update(null, new LambdaUpdateWrapper<SiteLink>()
                .eq(SiteLink::getId, id)
                .eq(SiteLink::getStatus, SiteStatus.PENDING)
                .set(SiteLink::getStatus, SiteStatus.PUBLIC)
                .set(SiteLink::getReviewedBy, adminId)
                .set(SiteLink::getReviewedAt, LocalDateTime.now())
                .set(SiteLink::getUpdatedAt, LocalDateTime.now())
                .set(SiteLink::getRejectReason, null));
        if (rows == 0) {
            throw new BusinessException("该申请已被处理，请刷新后重试");
        }

        replaceTags(id, validTagIds);
        // 转公开后封面必须能被其他用户读取
        imageService.setVisibilityBySystem(site.getCoverImageId(), 1);
        log.info("网站分享审批通过: id={}, admin={}, tags={}", id, adminId, validTagIds);
    }

    @Override
    @Transactional
    public void reject(Long id, String reason, Long adminId) {
        SiteLink site = requireSite(id);
        if (site.getStatus() != SiteStatus.PENDING) {
            throw new BusinessException("该分享不在待审批状态");
        }
        int rows = siteLinkRepository.update(null, new LambdaUpdateWrapper<SiteLink>()
                .eq(SiteLink::getId, id)
                .eq(SiteLink::getStatus, SiteStatus.PENDING)
                .set(SiteLink::getStatus, SiteStatus.REJECTED)
                .set(SiteLink::getRejectReason, reason.trim())
                .set(SiteLink::getReviewedBy, adminId)
                .set(SiteLink::getReviewedAt, LocalDateTime.now())
                .set(SiteLink::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException("该申请已被处理，请刷新后重试");
        }
        imageService.setVisibilityBySystem(site.getCoverImageId(), 0);
        log.info("网站分享审批驳回: id={}, admin={}", id, adminId);
    }

    @Override
    @Transactional
    public void offline(Long id, Long adminId) {
        SiteLink site = requireSite(id);
        if (site.getStatus() != SiteStatus.PUBLIC) {
            throw new BusinessException("该分享未处于公开状态");
        }
        siteLinkRepository.update(null, new LambdaUpdateWrapper<SiteLink>()
                .eq(SiteLink::getId, id)
                .eq(SiteLink::getStatus, SiteStatus.PUBLIC)
                .set(SiteLink::getStatus, SiteStatus.PRIVATE)
                .set(SiteLink::getReviewedBy, adminId)
                .set(SiteLink::getReviewedAt, LocalDateTime.now())
                .set(SiteLink::getUpdatedAt, LocalDateTime.now()));
        imageService.setVisibilityBySystem(site.getCoverImageId(), 0);
        log.info("网站分享被撤下: id={}, admin={}", id, adminId);
    }

    @Override
    @Transactional
    public void updateTags(Long id, List<Long> tagIds) {
        requireSite(id);
        replaceTags(id, requireExistingTags(tagIds));
    }

    @Override
    public List<SiteTagResponse> listTags() {
        LambdaQueryWrapper<SiteTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SiteTag::getSort).orderByAsc(SiteTag::getId);
        List<SiteTag> tags = siteTagRepository.selectList(wrapper);
        if (tags.isEmpty()) {
            return List.of();
        }

        // 一次查全部关联再在内存计数，避免每个标签一条 count
        Map<Long, Long> counts = siteLinkTagRepository.selectList(new LambdaQueryWrapper<>())
                .stream()
                .collect(Collectors.groupingBy(SiteLinkTag::getTagId, Collectors.counting()));

        return tags.stream().map(tag -> {
            SiteTagResponse resp = SiteTagResponse.from(tag);
            resp.setSiteCount(counts.getOrDefault(tag.getId(), 0L));
            return resp;
        }).collect(Collectors.toList());
    }

    @Override
    public SiteTagResponse createTag(SiteTagRequest request) {
        String name = request.getName().trim();
        requireTagNameFree(name, null);
        SiteTag tag = SiteTag.builder()
                .name(name)
                .sort(request.getSort() == null ? 0 : request.getSort())
                .build();
        siteTagRepository.insert(tag);
        return SiteTagResponse.from(tag);
    }

    @Override
    public SiteTagResponse updateTag(Long id, SiteTagRequest request) {
        SiteTag tag = siteTagRepository.selectById(id);
        if (tag == null) {
            throw new BusinessException("标签不存在");
        }
        String name = request.getName().trim();
        requireTagNameFree(name, id);
        tag.setName(name);
        tag.setSort(request.getSort() == null ? 0 : request.getSort());
        siteTagRepository.updateById(tag);
        return SiteTagResponse.from(tag);
    }

    @Override
    @Transactional
    public void deleteTag(Long id) {
        if (siteTagRepository.selectById(id) == null) {
            throw new BusinessException("标签不存在");
        }
        // 先清关联再删标签：站点保持公开，不因字典维护牵连审批状态
        LambdaQueryWrapper<SiteLinkTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SiteLinkTag::getTagId, id);
        siteLinkTagRepository.delete(wrapper);
        siteTagRepository.deleteById(id);
        log.info("站点标签删除: id={}", id);
    }

    @Override
    public SiteStatsResponse getStats() {
        return SiteStatsResponse.builder()
                .pending(countByStatus(SiteStatus.PENDING))
                .published(countByStatus(SiteStatus.PUBLIC))
                .rejected(countByStatus(SiteStatus.REJECTED))
                .build();
    }

    private long countByStatus(int status) {
        LambdaQueryWrapper<SiteLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SiteLink::getStatus, status);
        return siteLinkRepository.selectCount(wrapper);
    }

    private SiteLink requireSite(Long id) {
        SiteLink site = siteLinkRepository.selectById(id);
        if (site == null) {
            throw new BusinessException("分享不存在");
        }
        return site;
    }

    /** 标签必须非空且全部存在，公开前打标是硬要求 */
    private Set<Long> requireExistingTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            throw new BusinessException("请至少选择一个标签");
        }
        Set<Long> unique = new LinkedHashSet<>(tagIds);
        long existing = siteTagRepository.selectCount(
                new LambdaQueryWrapper<SiteTag>().in(SiteTag::getId, unique));
        if (existing != unique.size()) {
            throw new BusinessException("存在无效标签，请刷新标签库后重试");
        }
        return unique;
    }

    private void replaceTags(Long siteId, Set<Long> tagIds) {
        LambdaQueryWrapper<SiteLinkTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SiteLinkTag::getSiteId, siteId);
        siteLinkTagRepository.delete(wrapper);
        for (Long tagId : tagIds) {
            siteLinkTagRepository.insert(SiteLinkTag.builder()
                    .siteId(siteId)
                    .tagId(tagId)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    private void requireTagNameFree(String name, Long excludeId) {
        LambdaQueryWrapper<SiteTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SiteTag::getName, name).ne(excludeId != null, SiteTag::getId, excludeId);
        if (siteTagRepository.selectCount(wrapper) > 0) {
            throw new BusinessException("标签名已存在");
        }
    }
}
