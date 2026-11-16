package com.evernox.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 活动时间轴定时同步：官网通常周三/周四发布停机更新公告，周四晚增量扫一遍最新几页。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HyolActivityScheduler {

    private final HyolActivityService activityService;

    @Scheduled(cron = "0 30 20 ? * THU")
    public void weeklySync() {
        try {
            activityService.startSync(false);
        } catch (Exception e) {
            log.warn("活动时间轴定时同步失败: {}", e.getMessage());
        }
    }
}
