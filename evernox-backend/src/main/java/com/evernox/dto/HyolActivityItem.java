package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 活动时间轴单条记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolActivityItem {

    private Long id;

    /** 活动名 */
    private String name;

    /** 活动类型 */
    private String category;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDate announceDate;

    /** 来源公告标题 */
    private String sourceTitle;

    /** 来源公告链接 */
    private String sourceUrl;
}
