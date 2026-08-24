package com.evernox.service;

import com.evernox.dto.AnnouncementTagRequest;
import com.evernox.dto.AnnouncementTagResponse;

import java.util.List;

/**
 * 公告标签服务接口
 */
public interface AnnouncementTagService {

    List<AnnouncementTagResponse> list();

    AnnouncementTagResponse create(AnnouncementTagRequest request);

    AnnouncementTagResponse update(Long id, AnnouncementTagRequest request);

    void delete(Long id);
}
