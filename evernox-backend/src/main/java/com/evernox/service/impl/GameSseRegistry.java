package com.evernox.service.impl;

import com.evernox.dto.GameMapChangeEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 沙盘争霸实时推送注册表（进程内实现，仿照公告 SSE）
 *
 * 每位在线玩家持有一条 SSE 连接；任何地图变更（占领/进攻/驻防/建设）后广播
 * map-change 事件，让其他在线玩家即时看到地图变化。单实例部署够用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameSseRegistry {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;

    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("ready"));
        } catch (Exception e) {
            emitters.remove(emitter);
            log.warn("游戏 SSE 连接建立失败: {}", e.getMessage());
        }
        return emitter;
    }

    @SuppressWarnings("null")
    public void broadcastMapChange(GameMapChangeEvent event) {
        String data;
        try {
            data = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            log.warn("游戏地图变更序列化失败: {}", e.getMessage());
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("map-change").data(data));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
