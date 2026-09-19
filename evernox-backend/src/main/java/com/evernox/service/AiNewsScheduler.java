package com.evernox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
}
