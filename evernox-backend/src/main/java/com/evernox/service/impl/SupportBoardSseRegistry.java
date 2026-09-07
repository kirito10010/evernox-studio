package com.evernox.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 应援板实时推送注册表（广播给所有在线观众）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportBoardSseRegistry {

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
            log.warn("应援板 SSE 连接建立失败: {}", e.getMessage());
        }
        return emitter;
    }

    @SuppressWarnings("null")
    public void broadcast(Long boardId, int x, int y, String color, Integer locked) {
        Map<String, Object> data = new HashMap<>();
        data.put("boardId", boardId);
        data.put("x", x);
        data.put("y", y);
        data.put("color", color);
        data.put("locked", locked);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("应援板像素变更序列化失败: {}", e.getMessage());
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("pixel-changed").data(payload));
            } catch (Exception e) {
                emitters.remove(emitter);
            }
        }
    }
}
