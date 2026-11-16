package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动时间轴响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolActivityTimelineResponse {

    private List<HyolActivityItem> items;

    /** 活动名及次数（不受筛选影响，用于渲染筛选器；按次数从多到少排序） */
    private List<HyolActivityNameCount> names;

    private long total;

    /** 最近同步时间 */
    private LocalDateTime syncedAt;
}
