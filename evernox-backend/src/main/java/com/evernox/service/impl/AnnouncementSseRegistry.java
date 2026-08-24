package com.evernox.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 公告实时推送注册表（进程内实现）
 *
 * 每位在线用户持有一条 SSE 连接；管理员发布/编辑/删除公告后广播 refresh 事件，
 * 通知所有在线用户刷新未读数，实现「管理员发布，其他用户即时收到」。
 *
 * 单实例部署够用；进程重启后连接全部失效，前端 EventSource 会自动重连。
 */
@Slf4j
@Component
public class AnnouncementSseRegistry {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /** 建立一条新的 SSE 连接 */
    public SseEmitter register() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));

        try {
            // 先发一条 ready，把代理/浏览器缓冲冲开，并确认连接可用
            emitter.send(SseEmitter.event().name("ready").data("ready"));
        } catch (Exception e) {
            emitters.remove(emitter);
            log.warn("SSE 连接建立失败: {}", e.getMessage());
        }
        return emitter;
    }

    /** 广播刷新事件：管理员发布/编辑/删除公告后调用 */
    public void broadcast() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("refresh").data("refresh"));
            } catch (Exception e) {
                // 客户端已断开，移除该连接
                emitters.remove(emitter);
            }
        }
    }
}
