package com.evernox.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户积分实时推送注册表（按 userId 维护单连接）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserPointsSseRegistry {

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public SseEmitter register(Long userId) {
        SseEmitter old = emitters.remove(userId);
        if (old != null) {
            try {
                old.complete();
            } catch (Exception ignored) {
            }
        }
        SseEmitter emitter = new SseEmitter(0L);
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(e -> emitters.remove(userId, emitter));
        try {
            emitter.send(SseEmitter.event().name("ready").data("ready"));
        } catch (Exception e) {
            emitters.remove(userId, emitter);
            log.warn("积分 SSE 连接建立失败: {}", e.getMessage());
        }
        return emitter;
    }

    @SuppressWarnings("null")
    public void notifyPointsChanged(Long userId, Integer points) {
        if (userId == null) {
            return;
        }
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name("points-changed")
                    .data(objectMapper.writeValueAsString(Map.of("points", points == null ? 0 : points))));
        } catch (Exception e) {
            emitters.remove(userId, emitter);
        }
    }

    /**
     * 当前在线登录用户数（同一个 userId 多标签页只算一个）
     */
    public int onlineCount() {
        return emitters.size();
    }

    /**
     * SSE 心跳：每 30 秒向所有连接发送注释帧。
     * <p>
     * 作用一：让 Nginx 等代理不因空闲读超时断开长连接；
     * 作用二：客户端已消失时 send 会抛错，借此清理残留连接，保证在线人数准确。
     */
    @Scheduled(fixedDelay = 30_000)
    public void heartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        emitters.forEach((userId, emitter) -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception e) {
                emitters.remove(userId, emitter);
            }
        });
    }
}
