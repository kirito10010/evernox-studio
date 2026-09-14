package com.evernox.service.impl;

import com.evernox.dto.SupportPixelItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 应援板像素读缓存（Redis Hash：field = "x:y"，value = "locked|color"）
 *
 * 只做「读缓存」：MySQL 仍是唯一持久化源。Redis 不可用时全部回退 MySQL，不影响功能。
 * 缓存要么「完整」要么「缺失」，绝不出现不完整缓存（写操作仅当缓存已存在时才增量更新）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupportBoardPixelCache {

    private static final String EMPTY_MARK = "__empty__";

    private final StringRedisTemplate redis;

    @NonNull
    private String key(Long boardId) {
        return "support_board:" + boardId + ":pixels";
    }

    /** 读取缓存；缓存不存在返回 null（表示需要从 MySQL 加载） */
    public List<SupportPixelItem> get(Long boardId) {
        try {
            String k = key(boardId);
            if (Boolean.FALSE.equals(redis.hasKey(k))) {
                return null;
            }
            Map<Object, Object> entries = redis.opsForHash().entries(k);
            List<SupportPixelItem> pixels = new ArrayList<>(Math.max(0, entries.size() - 1));
            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                String field = (String) e.getKey();
                if (EMPTY_MARK.equals(field)) {
                    continue;
                }
                String[] xy = field.split(":", 2);
                String val = (String) e.getValue();
                String color = val;
                int locked = 0;
                if (val != null && val.contains("|")) {
                    String[] parts = val.split("\\|", 2);
                    locked = "1".equals(parts[0]) ? 1 : 0;
                    color = parts[1];
                }
                pixels.add(SupportPixelItem.builder()
                        .x(Integer.parseInt(xy[0]))
                        .y(Integer.parseInt(xy[1]))
                        .color(color)
                        .locked(locked)
                        .build());
            }
            return pixels;
        } catch (Exception e) {
            log.warn("读取画板像素缓存失败，回退 MySQL: {}", e.getMessage());
            return null;
        }
    }

    /** 全量写入缓存（预热）；空画板写入哨兵标记 */
    public void putAll(Long boardId, List<SupportPixelItem> pixels) {
        try {
            String k = key(boardId);
            redis.delete(k);
            if (pixels == null || pixels.isEmpty()) {
                redis.opsForHash().put(k, EMPTY_MARK, "1");
                return;
            }
            // 先拼成 Map，再一次 putAll（单条 HMSET），避免逐像素多次 Redis 往返
            Map<String, String> map = new HashMap<>(pixels.size());
            for (SupportPixelItem p : pixels) {
                String color = p.getColor();
                if (color == null) {
                    continue;
                }
                int locked = p.getLocked() != null && p.getLocked() == 1 ? 1 : 0;
                map.put(p.getX() + ":" + p.getY(), locked + "|" + color);
            }
            redis.opsForHash().putAll(k, map);
        } catch (Exception e) {
            log.warn("写入画板像素缓存失败: {}", e.getMessage());
        }
    }

    /** 绘制一个像素（仅当缓存已存在时才增量更新，避免产生不完整缓存） */
    public void setPixel(Long boardId, int x, int y, @NonNull String color) {
        try {
            String k = key(boardId);
            if (Boolean.FALSE.equals(redis.hasKey(k))) {
                return;
            }
            redis.opsForHash().delete(k, EMPTY_MARK);
            redis.opsForHash().put(k, x + ":" + y, "0|" + color);
        } catch (Exception e) {
            log.warn("更新画板像素缓存失败: {}", e.getMessage());
        }
    }

    /** 擦除一个像素（仅当缓存已存在时） */
    public void erasePixel(Long boardId, int x, int y) {
        try {
            String k = key(boardId);
            if (Boolean.FALSE.equals(redis.hasKey(k))) {
                return;
            }
            redis.opsForHash().delete(k, x + ":" + y);
        } catch (Exception e) {
            log.warn("更新画板像素缓存失败: {}", e.getMessage());
        }
    }

    /** 锁定一个像素（仅当缓存已存在时） */
    public void lockPixel(Long boardId, int x, int y, @NonNull String color) {
        try {
            String k = key(boardId);
            if (Boolean.FALSE.equals(redis.hasKey(k))) {
                return;
            }
            redis.opsForHash().delete(k, EMPTY_MARK);
            redis.opsForHash().put(k, x + ":" + y, "1|" + color);
        } catch (Exception e) {
            log.warn("更新画板像素缓存失败: {}", e.getMessage());
        }
    }

    /** 解锁一个像素（仅当缓存已存在时） */
    public void unlockPixel(Long boardId, int x, int y, @NonNull String color) {
        try {
            String k = key(boardId);
            if (Boolean.FALSE.equals(redis.hasKey(k))) {
                return;
            }
            redis.opsForHash().put(k, x + ":" + y, "0|" + color);
        } catch (Exception e) {
            log.warn("更新画板像素缓存失败: {}", e.getMessage());
        }
    }

    /** 使缓存失效（切画板/删画板时） */
    public void invalidate(Long boardId) {
        try {
            redis.delete(key(boardId));
        } catch (Exception e) {
            log.warn("失效画板像素缓存失败: {}", e.getMessage());
        }
    }
}
