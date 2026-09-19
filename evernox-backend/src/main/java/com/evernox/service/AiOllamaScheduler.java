package com.evernox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Ollama 模型库定时同步任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiOllamaScheduler {

    private final AiOllamaService aiOllamaService;

    /** 每周一 03:30 与官网全量比对：新增入库、变化更新、官网已删的删除 */
    @Scheduled(cron = "0 30 3 * * MON")
    public void weeklySync() {
        try {
            aiOllamaService.syncAll();
        } catch (Exception e) {
            log.warn("Ollama 模型库每周同步失败: {}", e.getMessage());
        }
    }

    /** 首次部署（库里没有模型）时异步补一次，保证打开页面就有数据 */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrap() {
        CompletableFuture.runAsync(() -> {
            try {
                if (aiOllamaService.isEmpty()) {
                    aiOllamaService.syncAll();
                }
            } catch (Exception e) {
                log.warn("Ollama 模型库首次抓取失败: {}", e.getMessage());
            }
        });
    }
}
