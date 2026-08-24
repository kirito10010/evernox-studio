package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.AnnouncementTagRequest;
import com.evernox.dto.AnnouncementTagResponse;
import com.evernox.entity.Announcement;
import com.evernox.entity.AnnouncementTag;
import com.evernox.exception.BusinessException;
import com.evernox.repository.AnnouncementRepository;
import com.evernox.repository.AnnouncementTagRepository;
import com.evernox.service.AnnouncementTagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 公告标签服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AnnouncementTagServiceImpl implements AnnouncementTagService {

    private final AnnouncementTagRepository tagRepository;
    private final AnnouncementRepository announcementRepository;

    @Override
    public List<AnnouncementTagResponse> list() {
        return tagRepository.selectList(new LambdaQueryWrapper<AnnouncementTag>()
                        .orderByAsc(AnnouncementTag::getSort)
                        .orderByAsc(AnnouncementTag::getId)).stream()
                .map(AnnouncementTagResponse::from)
                .toList();
    }

    @Override
    public AnnouncementTagResponse create(AnnouncementTagRequest request) {
        String name = request.getName().trim();
        ensureNameUnique(name, null);

        AnnouncementTag tag = AnnouncementTag.builder()
                .name(name)
                .color(request.getColor().trim())
                .sort(0)
                .build();
        tagRepository.insert(tag);
        log.info("公告标签创建: id={}, name={}", tag.getId(), name);
        return AnnouncementTagResponse.from(tag);
    }

    @Override
    public AnnouncementTagResponse update(Long id, AnnouncementTagRequest request) {
        AnnouncementTag tag = requireTag(id);
        String name = request.getName().trim();
        ensureNameUnique(name, id);

        tag.setName(name);
        tag.setColor(request.getColor().trim());
        tagRepository.updateById(tag);
        return AnnouncementTagResponse.from(tag);
    }

    @Override
    public void delete(Long id) {
        requireTag(id);
        Long used = announcementRepository.selectCount(new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getTagId, id));
        if (used != null && used > 0) {
            throw new BusinessException("该标签已被公告使用，无法删除");
        }
        tagRepository.deleteById(id);
        log.info("公告标签删除: id={}", id);
    }

    private AnnouncementTag requireTag(Long id) {
        AnnouncementTag tag = tagRepository.selectById(id);
        if (tag == null) {
            throw new BusinessException("标签不存在");
        }
        return tag;
    }

    /** 名称唯一性校验：excludeId 非空时排除自身（更新场景） */
    private void ensureNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<AnnouncementTag> wrapper = new LambdaQueryWrapper<AnnouncementTag>()
                .eq(AnnouncementTag::getName, name)
                .ne(excludeId != null, AnnouncementTag::getId, excludeId);
        Long count = tagRepository.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("标签已存在");
        }
    }
}
