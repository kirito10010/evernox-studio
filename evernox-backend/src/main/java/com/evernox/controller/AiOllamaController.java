package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.OllamaFilterOptions;
import com.evernox.dto.OllamaModelResponse;
import com.evernox.dto.OllamaSyncStatus;
import com.evernox.service.AiOllamaService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ollama 模型库接口（查询对登录用户开放，同步仅管理员）
 */
@RestController
@RequestMapping("/ai-ollama")
@RequiredArgsConstructor
public class AiOllamaController {

    private final AiOllamaService aiOllamaService;

    @GetMapping("/models")
    public Result<IPage<OllamaModelResponse>> models(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) Boolean abliterated,
            @RequestParam(required = false) String capability,
            @RequestParam(defaultValue = "popular") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(aiOllamaService.listModels(keyword, vendor, size, abliterated, capability, sort, page, pageSize));
    }

    @GetMapping("/models/{id}")
    public Result<OllamaModelResponse> detail(@PathVariable Long id) {
        return Result.success(aiOllamaService.getModelDetail(id));
    }

    @GetMapping("/filters")
    public Result<OllamaFilterOptions> filters() {
        return Result.success(aiOllamaService.getFilterOptions());
    }

    @GetMapping("/stats")
    public Result<OllamaSyncStatus> stats() {
        return Result.success(aiOllamaService.getSyncStatus());
    }

    @GetMapping("/sync/status")
    public Result<OllamaSyncStatus> syncStatus() {
        return Result.success(aiOllamaService.getSyncStatus());
    }

    @PostMapping("/sync")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> sync() {
        aiOllamaService.triggerSyncAsync();
        return Result.<Void>success("已开始同步", null);
    }

    /**
     * 按关键词从官网「按需补全」模型库（只增不删）。
     *
     * 官网默认列表页只返回官方库、社区模型无法枚举，所以「官网有、本地没有」的模型
     * 只能按关键词抓回来。搜索页无结果时由前端调用。
     */
    @PostMapping("/backfill")
    @PreAuthorize("hasRole('admin')")
    public Result<Void> backfill(@RequestParam String keyword) {
        aiOllamaService.triggerBackfillAsync(keyword);
        return Result.<Void>success("已开始从官网搜索并补全", null);
    }
}
