package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.entity.NoteImage;
import com.evernox.repository.NoteImageRepository;
import com.evernox.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 笔记插图的公共操作
 *
 * 用户侧（撤回）与管理侧（通过/驳回/撤下）都要改插图可见性，抽出来避免两处各写一遍。
 */
@Component
@RequiredArgsConstructor
@SuppressWarnings("null")
public class NoteImageSupport {

    private final NoteImageRepository noteImageRepository;
    private final ImageService imageService;

    public List<Long> imageIds(Long noteId) {
        return noteImageRepository.selectList(new LambdaQueryWrapper<NoteImage>()
                        .eq(NoteImage::getNoteId, noteId)).stream()
                .map(NoteImage::getImageId)
                .toList();
    }

    /**
     * 批量调整某篇笔记插图的可见性
     *
     * 公开笔记的插图必须可见，否则读者只能看到一堆加载失败的图；
     * 反过来撤下/驳回时必须收回，否则公开期间拿到 id 的人还能继续读。
     */
    public void setVisibility(Long noteId, int visibility) {
        for (Long imageId : imageIds(noteId)) {
            imageService.setVisibilityBySystem(imageId, visibility);
        }
    }
}
