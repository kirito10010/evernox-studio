package com.evernox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * Code Arena 模型排行榜定时采集
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiArenaScheduler {

    private final AiArenaService aiArenaService;

    /** 榜单每周更新一次：周一 04:15 全量重抓 */
    @Scheduled(cron = "0 15 4 * * MON")
    public void weeklySync() {
        try {
            aiArenaService.syncAll();
        } catch (Exception e) {
            log.warn("Code Arena 每周同步失败: {}", e.getMessage());
        }
    }

    /** 启动时若库为空则异步先抓一次，保证首次访问就有数据 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        CompletableFuture.runAsync(() -> {
            try {
                if (aiArenaService.isEmpty()) {
                    aiArenaService.syncAll();
                }
            } catch (Exception e) {
                log.warn("Code Arena 启动抓取失败: {}", e.getMessage());
            }
        });
    }
}
