package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Ollama 模型库同步状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaSyncStatus {

    /** 是否正在同步 */
    private boolean running;

    /** 最近一次同步开始时间 */
    private LocalDateTime lastRunAt;

    /** 最近一次是否成功 */
    private boolean lastSuccess;

    /** 最近一次耗时(毫秒) */
    private long durationMs;

    /** 模型总数 */
    private long modelTotal;

    /** 变体总数 */
    private long tagTotal;

    /** 新增模型数 */
    private int modelAdded;

    /** 更新模型数 */
    private int modelUpdated;

    /** 删除模型数 */
    private int modelRemoved;

    /** 新增变体数 */
    private int tagAdded;

    /** 删除变体数 */
    private int tagRemoved;

    /** 结果说明 / 错误信息 */
    private String message;
}
