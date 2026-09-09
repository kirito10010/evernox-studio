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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应援板实时推送注册表（广播给所有在线观众）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportBoardSseRegistry {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ObjectMapper objectMapper;
    /** 单线程异步广播：避免慢客户端阻塞请求线程，同时保持事件顺序 */
    private final ExecutorService sseExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "support-board-sse");
        t.setDaemon(true);
        return t;
    });

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

    /** 单个像素变更（用于批量推送） */
    public record PixelChange(int x, int y, String color, Integer locked) {}

    @SuppressWarnings("null")
    public void broadcastBatch(Long boardId, List<PixelChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("boardId", boardId);
        data.put("pixels", changes);
        String payload;
        try {
            payload = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("应援板像素变更序列化失败: {}", e.getMessage());
            return;
        }
        sseExecutor.submit(() -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name("pixel-changed").data(payload));
                } catch (Exception e) {
                    emitters.remove(emitter);
                }
            }
        });
    }
}