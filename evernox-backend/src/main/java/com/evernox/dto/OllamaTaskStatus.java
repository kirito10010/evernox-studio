package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ollama 后台作业状态（获取新模型 / 更新模型共用一个槽位）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaTaskStatus {

    /** 任务类型：DISCOVER / UPDATE；null 表示还没跑过 */
    private String type;

    private boolean running;

    /** 总条数（更新=待更新模型数；获取=抓变体阶段的新模型数，未知为 0） */
    private long total;

    /** 已完成条数 */
    private long done;

    /** 当前阶段说明，如「抓取官网列表」「抓取变体」「更新 3/100」 */
    private String phase;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private String message;

    /** 获取新模型结果（type=DISCOVER 时） */
    private OllamaDiscoverResult discover;

    /** 更新模型结果（type=UPDATE 时） */
    private OllamaUpdateResult update;
}
