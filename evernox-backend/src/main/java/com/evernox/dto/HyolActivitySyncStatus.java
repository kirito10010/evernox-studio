package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 活动时间轴同步状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolActivitySyncStatus {

    private boolean running;

    /** 是否全量重扫（忽略已入库公告） */
    private boolean full;

    /** 当前阶段文案 */
    private String phase;

    private long done;

    private long total;

    /** 新增活动条数 */
    private long added;

    /** 更新活动条数 */
    private long updated;

    /** 抓取/解析失败公告数 */
    private long failed;

    /** 跳过（已入库或没有热点活动）的公告数 */
    private long skipped;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String message;
}
