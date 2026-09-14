package com.evernox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 编程资讯定时采集任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiNewsScheduler {

    private final AiNewsService aiNewsService;

    @Scheduled(fixedRate = 30 * 60 * 1000, initialDelay = 5000)
    public void crawl() {
        try {
            aiNewsService.crawlAll();
        } catch (Exception e) {
            log.warn("AI 资讯定时采集失败: {}", e.getMessage());
        }
    }

    /** 模型排行榜：约每 4~6 小时随机抓取一次（固定间隔 + 0~2 小时随机抖动），避免固定时间被抓包识别 */
    @Scheduled(fixedDelay = 4 * 60 * 60 * 1000, initialDelay = 60 * 1000)
    public void crawlLeaderboard() {
        try {
            long jitter = ThreadLocalRandom.current().nextLong(2 * 60 * 60 * 1000L);
            Thread.sleep(jitter);
            aiNewsService.crawlLeaderboard();
            aiNewsService.crawlZhizhiRank();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("AI 模型排行榜抓取失败: {}", e.getMessage());
        }
    }

    /** 启动时异步先抓一次排行榜，保证首次访问就有数据 */
    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        CompletableFuture.runAsync(() -> {
            try {
                aiNewsService.crawlLeaderboard();
                aiNewsService.crawlZhizhiRank();
            } catch (Exception e) {
                log.warn("AI 模型排行榜启动抓取失败: {}", e.getMessage());
            }
        });
    }
}


