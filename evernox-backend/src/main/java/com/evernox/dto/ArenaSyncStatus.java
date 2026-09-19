package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Code Arena 同步状态（管理员进度展示）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaSyncStatus {

    /** 是否正在同步 */
    private boolean running;

    /** 当前阶段："抓取榜单" / "写入数据库" / "空闲" */
    private String phase;

    /** 当前正在处理的分类中文名 */
    private String currentCategory;

    /** 分类总数 */
    private int categoryTotal;

    /** 已完成分类数 */
    private int categoryDone;

    /** 库中总行数 */
    private long rowTotal;

    /** 本次新增行数 */
    private int added;

    /** 本次更新行数 */
    private int updated;

    /** 本次删除行数 */
    private int removed;

    /** 最近一次同步开始时间 */
    private LocalDateTime lastRunAt;

    /** 最近一次是否成功 */
    private boolean lastSuccess;

    /** 最近一次耗时(毫秒) */
    private long durationMs;

    /** 结果说明 / 错误信息 */
    private String message;
}
