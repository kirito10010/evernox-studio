package com.evernox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.entity.GameRound;
import com.evernox.repository.GameRoundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 沙盘争霸轮次调度
 *
 * 周一 10:00 开局、周日 18:00 结算；启动时兜底保证部署后立即可玩。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameRoundScheduler implements ApplicationRunner {

    private final GameService gameService;
    private final GameRoundRepository roundRepository;

    @Scheduled(cron = "0 0 10 * * MON")
    public void scheduledStart() {
        try {
            if (runningRound() == null) {
                gameService.startRound();
            }
        } catch (Exception e) {
            log.error("沙盘开局定时任务失败", e);
        }
    }

    @Scheduled(cron = "0 0 18 * * SUN")
    public void scheduledSettle() {
        try {
            gameService.settle();
        } catch (Exception e) {
            log.error("沙盘结算定时任务失败", e);
        }
    }

    /** 每 10 分钟兜底一次：结算时间已过但仍 RUNNING 的轮次（应对 18:00 cron 未命中/宕机） */
    @Scheduled(cron = "0 */10 * * * *")
    public void scheduledSettleIfExpired() {
        try {
            GameRound r = runningRound();
            if (r != null && r.getSettleAt() != null
                    && r.getSettleAt().isBefore(LocalDateTime.now())) {
                gameService.settle();
            }
        } catch (Exception e) {
            log.error("沙盘过期结算兜底失败", e);
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            GameRound r = runningRound();
            if (r != null && r.getSettleAt() != null
                    && r.getSettleAt().isBefore(LocalDateTime.now())) {
                gameService.settle();
            }
            if (runningRound() == null && shouldBootstrap()) {
                gameService.startRound();
            }
        } catch (Exception e) {
            log.error("沙盘启动兜底失败", e);
        }
    }

    @SuppressWarnings("null")
    private GameRound runningRound() {
        return roundRepository.selectOne(new LambdaQueryWrapper<GameRound>()
                .eq(GameRound::getStatus, GameRound.STATUS_RUNNING)
                .orderByDesc(GameRound::getId)
                .last("LIMIT 1"));
    }

    /** 结算窗口（周日18:00 ~ 周一10:00）不自动开新轮 */
    private boolean shouldBootstrap() {
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dow = now.getDayOfWeek();
        if (dow == DayOfWeek.SUNDAY) {
            return now.toLocalTime().isBefore(LocalTime.of(18, 0));
        }
        if (dow == DayOfWeek.MONDAY) {
            return !now.toLocalTime().isBefore(LocalTime.of(10, 0));
        }
        return true;
    }
}
