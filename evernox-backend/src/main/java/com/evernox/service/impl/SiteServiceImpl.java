package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.SiteStatus;
import com.evernox.common.UserRole;
import com.evernox.dto.SiteLinkRequest;
import com.evernox.dto.SiteLinkResponse;
import com.evernox.dto.SiteStatsResponse;
import com.evernox.dto.SiteTagResponse;
import com.evernox.entity.Image;
import com.evernox.entity.SiteLink;
import com.evernox.entity.SiteLinkTag;
import com.evernox.entity.SiteTag;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.ImageRepository;
import com.evernox.repository.SiteLinkRepository;
import com.evernox.repository.SiteLinkTagRepository;
import com.evernox.repository.SiteTagRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.ImageService;
import com.evernox.service.SiteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 网站分享服务实现（用户侧）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SiteServiceImpl implements SiteService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SiteLinkRepository siteLinkRepository;
    private final SiteTagRepository siteTagRepository;
    private final SiteLinkTagRepository siteLinkTagRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final SiteAssembler siteAssembler;

    @Override
    @Transactional
    public SiteLinkResponse create(SiteLinkRequest request, Long userId) {
        requireOwnedCover(request.getCoverImageId(), userId);

        SiteLink site = SiteLink.builder()
                .userId(userId)
                .title(request.getTitle().trim())
                .url(request.getUrl().trim())
                .description(request.getDescription())
                .coverImageId(request.getCoverImageId())
                .status(SiteStatus.PRIVATE)
                .deleted(0)
                .build();
        siteLinkRepository.insert(site);
        log.info("网站分享创建: id={}, user={}", site.getId(), userId);
        return siteAssembler.convert(site);
    }

    @Override
    @Transactional
    public SiteLinkResponse update(Long id, SiteLinkRequest request, Long userId) {
        SiteLink site = requireOwned(id, userId);
        if (site.getStatus() == SiteStatus.PENDING) {
            throw new BusinessException("审批中不可编辑，请先撤回申请");
        }
        if (site.getStatus() == SiteStatus.PUBLIC) {
            throw new BusinessException("已公开的分享不可直接编辑，请先撤下");
        }
        requireOwnedCover(request.getCoverImageId(), userId);

        site.setTitle(request.getTitle().trim());
        site.setUrl(request.getUrl().trim());
        site.setDescription(request.getDescription());
        site.setCoverImageId(request.getCoverImageId());
        siteLinkRepository.updateById(site);
        return siteAssembler.convert(site);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        SiteLink site = requireOwned(id, userId);
        siteLinkRepository.deleteById(id);
        // 关联是纯附属数据，站点删了就没有意义，物理清掉避免脏关联被后续标签查询捞到
        LambdaQueryWrapper<SiteLinkTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SiteLinkTag::getSiteId, id);
        siteLinkTagRepository.delete(wrapper);
        // 封面本身仍归图床管理，这里只把可见性收回私密
        imageService.setVisibilityBySystem(site.getCoverImageId(), 0);
        log.info("网站分享删除: id={}, user={}", id, userId);
    }

    @Override
    @Transactional
    public void submit(Long id, Long userId) {
        SiteLink site = requireOwned(id, userId);
        if (site.getStatus() != SiteStatus.PRIVATE && site.getStatus() != SiteStatus.REJECTED) {
            throw new BusinessException("当前状态不可申请公开");
        }
        // 用条件更新而非 updateById：一是 updateById 会忽略 null，清不掉驳回原因；
        // 二是 status 作为更新条件可挡住并发重复提交
        int rows = siteLinkRepository.update(null, new LambdaUpdateWrapper<SiteLink>()
                .eq(SiteLink::getId, id)
                .in(SiteLink::getStatus, List.of(SiteStatus.PRIVATE, SiteStatus.REJECTED))
                .set(SiteLink::getStatus, SiteStatus.PENDING)
                .set(SiteLink::getSubmittedAt, LocalDateTime.now())
                .set(SiteLink::getUpdatedAt, LocalDateTime.now())
                .set(SiteLink::getRejectReason, null)
                .set(SiteLink::getReviewedBy, null)
                .set(SiteLink::getReviewedAt, null));
        if (rows == 0) {
            throw new BusinessException("该分享状态已变化，请刷新后重试");
        }
        log.info("网站分享申请公开: id={}, user={}", id, userId);
    }

    @Override
    @Transactional
    public void withdraw(Long id, Long userId) {
        SiteLink site = requireOwned(id, userId);
        if (site.getStatus() != SiteStatus.PENDING && site.getStatus() != SiteStatus.PUBLIC) {
            throw new BusinessException("当前状态无需撤回");
        }
        site.setStatus(SiteStatus.PRIVATE);
        siteLinkRepository.updateById(site);
        imageService.setVisibilityBySystem(site.getCoverImageId(), 0);
        log.info("网站分享撤回: id={}, user={}", id, userId);
    }

    @Override
    public IPage<SiteLinkResponse> listMine(Long userId, Integer status, int page, int size) {
        LambdaQueryWrapper<SiteLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SiteLink::getUserId, userId)
                .eq(status != null, SiteLink::getStatus, status)
                .orderByDesc(SiteLink::getUpdatedAt);
        return siteAssembler.convert(siteLinkRepository.selectPage(newPage(page, size), wrapper));
    }

    @Override
    public IPage<SiteLinkResponse> listPublic(String keyword, List<Long> tagIds, int page, int size) {
        LambdaQueryWrapper<SiteLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SiteLink::getStatus, SiteStatus.PUBLIC);

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(SiteLink::getTitle, kw)
                    .or().like(SiteLink::getDescription, kw)
                    .or().like(SiteLink::getUrl, kw));
        }
        if (tagIds != null && !tagIds.isEmpty()) {
            // 命中任一所选标签即可，先取交集站点ID再过滤，避免 join 后去重
            LambdaQueryWrapper<SiteLinkTag> relWrapper = new LambdaQueryWrapper<>();
            relWrapper.in(SiteLinkTag::getTagId, tagIds);
            Set<Long> siteIds = siteLinkTagRepository.selectList(relWrapper).stream()
                    .map(SiteLinkTag::getSiteId)
                    .collect(Collectors.toSet());
            if (siteIds.isEmpty()) {
                return new Page<>(page, size);
            }
            wrapper.in(SiteLink::getId, siteIds);
        }
        wrapper.orderByDesc(SiteLink::getReviewedAt).orderByDesc(SiteLink::getId);
        return siteAssembler.convert(siteLinkRepository.selectPage(newPage(page, size), wrapper));
    }

    @Override
    public SiteLinkResponse getById(Long id, Long userId) {
        SiteLink site = siteLinkRepository.selectById(id);
        if (site == null) {
            throw new BusinessException("分享不存在");
        }
        if (site.getStatus() != SiteStatus.PUBLIC
                && !site.getUserId().equals(userId)
                && !isAdmin(userId)) {
            throw new BusinessException(403, "无权查看该分享");
        }
        return siteAssembler.convert(site);
    }

    @Override
    public List<SiteTagResponse> listTags() {
        LambdaQueryWrapper<SiteTag> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SiteTag::getSort).orderByAsc(SiteTag::getId);
        return siteTagRepository.selectList(wrapper).stream()
                .map(SiteTagResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    public SiteStatsResponse getStats(Long userId) {
        return SiteStatsResponse.builder()
                .mine(countMine(userId, null))
                .pending(countMine(userId, SiteStatus.PENDING))
                .published(countMine(userId, SiteStatus.PUBLIC))
                .rejected(countMine(userId, SiteStatus.REJECTED))
                .build();
    }

    private long countMine(Long userId, Integer status) {
        LambdaQueryWrapper<SiteLink> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SiteLink::getUserId, userId).eq(status != null, SiteLink::getStatus, status);
        return siteLinkRepository.selectCount(wrapper);
    }

    /** 取出并校验归属，越权直接 403 */
    private SiteLink requireOwned(Long id, Long userId) {
        SiteLink site = siteLinkRepository.selectById(id);
        if (site == null) {
            throw new BusinessException("分享不存在");
        }
        if (!site.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该分享");
        }
        return site;
    }

    /** 封面必须是自己上传的图片，否则可用他人的私密图 ID 蹭公开曝光 */
    private void requireOwnedCover(Long coverImageId, Long userId) {
        if (coverImageId == null) {
            return;
        }
        Image image = imageRepository.selectById(coverImageId);
        if (image == null) {
            throw new BusinessException("封面图片不存在");
        }
        if (!image.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权使用该封面图片");
        }
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userRepository.selectById(userId);
        return user != null && UserRole.ADMIN.equals(user.getRole());
    }

    private Page<SiteLink> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
