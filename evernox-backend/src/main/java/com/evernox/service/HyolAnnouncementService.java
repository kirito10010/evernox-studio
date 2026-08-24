package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.HyolAnnouncementResponse;
import com.evernox.dto.HyolRefreshResponse;

/**
 * 火影忍者OL官方公告服务接口
 */
public interface HyolAnnouncementService {

    /** 从官网抓取并更新公告缓存 */
    HyolRefreshResponse refresh();

    IPage<HyolAnnouncementResponse> list(int page, int size);

    HyolAnnouncementResponse getDetail(Long id);
}
