package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.OllamaFilterOptions;
import com.evernox.dto.OllamaModelResponse;
import com.evernox.dto.OllamaSyncStatus;

/**
 * Ollama 模型库服务
 */
public interface AiOllamaService {

    /** 分页查询模型列表，支持关键词/厂商/参数量/abliterated/能力筛选，sort=popular|new|size */
    IPage<OllamaModelResponse> listModels(String keyword, String vendor, String size, Boolean abliterated,
                                          String capability, String sort, int page, int pageSize);

    /** 模型详情（含全部变体标签） */
    OllamaModelResponse getModelDetail(Long id);

    /** 筛选项：厂商（含数量）/ 参数量尺寸 / 能力 */
    OllamaFilterOptions getFilterOptions();

    /** 同步状态与统计（模型数、变体数、上次同步结果） */
    OllamaSyncStatus getSyncStatus();

    /** 模型库是否为空（用于首次启动自动补数据） */
    boolean isEmpty();

    /** 与官网全量比对同步：新增入库、变化更新、官网已删的删除（含安全闸） */
    OllamaSyncStatus syncAll();

    /** 异步触发同步（单飞，已在跑则忽略） */
    void triggerSyncAsync();

    /**
     * 按关键词从官网「按需补全」模型库（异步，只增不删）。
     * 用于「官网有、本地没有」的场景：官网默认列表页只返回官方库，社区模型无法枚举，
     * 只能按关键词抓回来。关键词为空/过长/已有同步在跑时同步抛业务异常。
     */
    void triggerBackfillAsync(String keyword);
}
