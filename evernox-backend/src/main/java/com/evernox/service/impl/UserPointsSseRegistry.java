package com.evernox.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
}
