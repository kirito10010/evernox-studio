package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.config.StorageConfig;
import com.evernox.dto.AnnouncementRequest;
import com.evernox.dto.AnnouncementResponse;
import com.evernox.dto.UnreadCountResponse;
import com.evernox.entity.Announcement;
import com.evernox.entity.AnnouncementImage;
import com.evernox.entity.AnnouncementRead;
import com.evernox.entity.AnnouncementTag;
import com.evernox.entity.Image;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.AnnouncementImageRepository;
import com.evernox.repository.AnnouncementReadRepository;
import com.evernox.repository.AnnouncementRepository;
import com.evernox.repository.AnnouncementTagRepository;
import com.evernox.repository.ImageRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.AnnouncementService;
import com.evernox.service.ImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 公告服务实现
 *
 * 正文里的插图只以 image.id 出现，引用关系另存 announcement_image：
 * 删除/编辑公告时移除图片，都靠这张表判断哪些插图已无人引用、可以真正删掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AnnouncementServiceImpl implements AnnouncementService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 200;

    private final AnnouncementRepository announcementRepository;
    private final AnnouncementTagRepository tagRepository;
    private final AnnouncementReadRepository readRepository;
    private final AnnouncementImageRepository announcementImageRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final NoteHtmlSanitizer sanitizer;

    // ==================== 管理员 ====================

    @Override
    @Transactional
    public AnnouncementResponse create(AnnouncementRequest request, Long adminId) {
        validateTag(request.getTagId());
        String safeHtml = sanitizer.sanitize(request.getContent());
        Set<Long> imageIds = requireAnnouncementImages(sanitizer.extractImageIds(safeHtml));

        Announcement announcement = Announcement.builder()
                .title(request.getTitle().trim())
                .content(safeHtml)
                .tagId(request.getTagId())
                .createdBy(adminId)
                .deleted(0)
                .build();
        announcementRepository.insert(announcement);
        linkImages(announcement.getId(), imageIds);
        log.info("公告发布: id={}, admin={}", announcement.getId(), adminId);
        return applyTag(AnnouncementResponse.detail(announcement),
                tagRepository.selectById(announcement.getTagId()));
    }

    @Override
    @Transactional
    public AnnouncementResponse update(Long id, AnnouncementRequest request) {
        Announcement announcement = requireAnnouncement(id);
        validateTag(request.getTagId());
        String safeHtml = sanitizer.sanitize(request.getContent());
        Set<Long> newIds = requireAnnouncementImages(sanitizer.extractImageIds(safeHtml));

        announcement.setTitle(request.getTitle().trim());
        announcement.setContent(safeHtml);
        announcement.setTagId(request.getTagId());
        announcementRepository.updateById(announcement);

        syncImages(id, newIds);
        return applyTag(AnnouncementResponse.detail(announcement),
                tagRepository.selectById(announcement.getTagId()));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        requireAnnouncement(id);
        announcementRepository.deleteById(id);
        // 公告没了，插图不再有引用，连文件一起清掉
        syncImages(id, Set.of());
        log.info("公告删除: id={}", id);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的公告");
        }
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.size() > MAX_BATCH_SIZE) {
            throw new BusinessException("单次最多删除 " + MAX_BATCH_SIZE + " 条");
        }
        Long owned = announcementRepository.selectCount(new LambdaQueryWrapper<Announcement>()
                .in(Announcement::getId, distinct));
        if (owned == null || owned.longValue() != distinct.size()) {
            throw new BusinessException("包含不存在的公告");
        }
        for (Long id : distinct) {
            delete(id);
        }
        log.info("公告批量删除: ids={}", distinct);
    }

    @Override
    public IPage<AnnouncementResponse> listAdmin(int page, int size, String keyword) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Announcement::getTitle, keyword.trim());
        }
        wrapper.orderByDesc(Announcement::getCreatedAt).orderByDesc(Announcement::getId);

        IPage<Announcement> raw = announcementRepository.selectPage(newPage(page, size), wrapper);
        Map<Long, AnnouncementTag> tagMap = tagMap(raw.getRecords());
        Map<Long, String> nameCache = new HashMap<>();

        Page<AnnouncementResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(a -> {
            AnnouncementResponse dto = AnnouncementResponse.brief(a);
            applyTag(dto, tagMap.get(a.getTagId()));
            dto.setCreatedByName(nameCache.computeIfAbsent(a.getCreatedBy(), this::username));
            return dto;
        }).toList());
        return result;
    }

    // ==================== 用户 ====================

    @Override
    public List<AnnouncementResponse> listForUser(Long userId) {
        List<Announcement> announcements = announcementRepository.selectList(
                new LambdaQueryWrapper<Announcement>()
                        .orderByDesc(Announcement::getCreatedAt)
                        .orderByDesc(Announcement::getId));
        Set<Long> readIds = readAnnouncementIds(userId);
        Map<Long, AnnouncementTag> tagMap = tagMap(announcements);

        return announcements.stream().map(a -> {
            AnnouncementResponse dto = AnnouncementResponse.brief(a);
            applyTag(dto, tagMap.get(a.getTagId()));
            dto.setRead(readIds.contains(a.getId()));
            return dto;
        }).toList();
    }

    @Override
    public AnnouncementResponse getDetail(Long id) {
        Announcement a = requireAnnouncement(id);
        return applyTag(AnnouncementResponse.detail(a), tagRepository.selectById(a.getTagId()));
    }

    @Override
    public void markRead(Long id, Long userId) {
        requireAnnouncement(id);
        Long exists = readRepository.selectCount(new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getAnnouncementId, id)
                .eq(AnnouncementRead::getUserId, userId));
        if (exists != null && exists > 0) {
            return;
        }
        try {
            readRepository.insert(AnnouncementRead.builder()
                    .announcementId(id)
                    .userId(userId)
                    .readAt(LocalDateTime.now())
                    .build());
        } catch (DuplicateKeyException e) {
            // 并发重复点击，忽略即可（联合主键去重）
        }
    }

    @Override
    public UnreadCountResponse unreadCount(Long userId) {
        long total = announcementRepository.selectCount(new LambdaQueryWrapper<>());
        long read = readRepository.selectCount(new LambdaQueryWrapper<AnnouncementRead>()
                .eq(AnnouncementRead::getUserId, userId)
                .inSql(AnnouncementRead::getAnnouncementId,
                        "SELECT id FROM announcement WHERE deleted = 0"));
        int unread = (int) Math.max(0, total - read);
        return UnreadCountResponse.builder().unread(unread).build();
    }

    // ==================== 内部方法 ====================

    private Announcement requireAnnouncement(Long id) {
        Announcement a = announcementRepository.selectById(id);
        if (a == null) {
            throw new BusinessException("公告不存在");
        }
        return a;
    }

    private void validateTag(Long tagId) {
        if (tagId == null) {
            return;
        }
        if (tagRepository.selectById(tagId) == null) {
            throw new BusinessException("标签不存在");
        }
    }

    /** 正文引用的图片必须是公告插图（purpose=4），防止把普通私密图贴进公告 */
    private Set<Long> requireAnnouncementImages(Set<Long> ids) {
        if (ids.isEmpty()) {
            return ids;
        }
        Set<Long> valid = imageRepository.selectBatchIds(ids).stream()
                .filter(img -> img.getPurpose() != null
                        && img.getPurpose() == StorageConfig.PURPOSE_ANNOUNCEMENT)
                .map(Image::getId)
                .collect(Collectors.toSet());
        if (valid.size() != ids.size()) {
            throw new BusinessException(403, "正文引用了非公告图片");
        }
        return valid;
    }

    private void linkImages(Long announcementId, Set<Long> imageIds) {
        for (Long imageId : imageIds) {
            announcementImageRepository.insert(AnnouncementImage.builder()
                    .announcementId(announcementId)
                    .imageId(imageId)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    /** 同步引用关系，并把不再被引用的插图真正删掉 */
    private void syncImages(Long announcementId, Set<Long> newIds) {
        Set<Long> oldIds = currentImageIds(announcementId);

        Set<Long> removed = new HashSet<>(oldIds);
        removed.removeAll(newIds);
        for (Long imageId : removed) {
            cleanImage(announcementId, imageId);
        }

        Set<Long> added = new HashSet<>(newIds);
        added.removeAll(oldIds);
        linkImages(announcementId, added);
    }

    private void cleanImage(Long announcementId, Long imageId) {
        announcementImageRepository.delete(new LambdaQueryWrapper<AnnouncementImage>()
                .eq(AnnouncementImage::getAnnouncementId, announcementId)
                .eq(AnnouncementImage::getImageId, imageId));
        Image img = imageRepository.selectById(imageId);
        if (img == null) {
            return;
        }
        try {
            // 公告插图归上传的管理员所有，按实际所有者删除
            imageService.deleteImage(imageId, img.getUserId());
        } catch (RuntimeException e) {
            log.warn("清理公告插图失败: announcementId={}, imageId={}, err={}",
                    announcementId, imageId, e.getMessage());
        }
    }

    private Set<Long> currentImageIds(Long announcementId) {
        return announcementImageRepository.selectList(new LambdaQueryWrapper<AnnouncementImage>()
                        .eq(AnnouncementImage::getAnnouncementId, announcementId)).stream()
                .map(AnnouncementImage::getImageId)
                .collect(Collectors.toSet());
    }

    private Set<Long> readAnnouncementIds(Long userId) {
        return readRepository.selectList(new LambdaQueryWrapper<AnnouncementRead>()
                        .eq(AnnouncementRead::getUserId, userId)).stream()
                .map(AnnouncementRead::getAnnouncementId)
                .collect(Collectors.toSet());
    }

    /** 一次查库补齐标签信息，避免逐条查标签表 */
    private Map<Long, AnnouncementTag> tagMap(List<Announcement> announcements) {
        List<Long> tagIds = announcements.stream()
                .map(Announcement::getTagId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (tagIds.isEmpty()) {
            return Map.of();
        }
        return tagRepository.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(AnnouncementTag::getId, t -> t));
    }

    private AnnouncementResponse applyTag(AnnouncementResponse dto, AnnouncementTag tag) {
        if (tag != null) {
            dto.setTagName(tag.getName());
            dto.setTagColor(tag.getColor());
        }
        return dto;
    }

    private String username(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.selectById(userId);
        return user != null ? user.getUsername() : "未知用户";
    }

    private Page<Announcement> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
