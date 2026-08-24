package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.AnnouncementRequest;
import com.evernox.dto.AnnouncementResponse;
import com.evernox.dto.UnreadCountResponse;

import java.util.List;

/**
 * 公告服务接口
 */
public interface AnnouncementService {

    // ==================== 管理员 ====================

    AnnouncementResponse create(AnnouncementRequest request, Long adminId);

    AnnouncementResponse update(Long id, AnnouncementRequest request);

    void delete(Long id);

    void batchDelete(List<Long> ids);

    IPage<AnnouncementResponse> listAdmin(int page, int size, String keyword);

    // ==================== 用户 ====================

    List<AnnouncementResponse> listForUser(Long userId);

    AnnouncementResponse getDetail(Long id);

    void markRead(Long id, Long userId);

    UnreadCountResponse unreadCount(Long userId);
}
