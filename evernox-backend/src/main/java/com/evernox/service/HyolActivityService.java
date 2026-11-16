package com.evernox.service;

import com.evernox.dto.HyolActivitySyncStatus;
import com.evernox.dto.HyolActivityTimelineResponse;

import java.util.List;

/**
 * 火影忍者OL 活动时间轴服务
 */
public interface HyolActivityService {

    /**
     * 时间轴数据。
     *
     * @param names   活动全名（多选，空表示全部）
     * @param keyword 活动名关键词（模糊匹配）
     */
    HyolActivityTimelineResponse timeline(List<String> names, String keyword);

    /** 触发同步（full=true 时忽略已入库公告，全量重扫） */
    HyolActivitySyncStatus startSync(boolean full);

    HyolActivitySyncStatus getSyncStatus();
}
