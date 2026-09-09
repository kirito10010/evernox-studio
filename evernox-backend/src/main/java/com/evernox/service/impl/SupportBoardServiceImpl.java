package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.SupportBoardResponse;
import com.evernox.dto.SupportBoardViewResponse;
import com.evernox.dto.SupportPixelItem;
import com.evernox.dto.SupportPixelRequest;
import com.evernox.entity.SupportBoard;
import com.evernox.entity.SupportPixel;
import com.evernox.exception.BusinessException;
import com.evernox.repository.SupportBoardRepository;
import com.evernox.repository.SupportPixelRepository;
import com.evernox.service.SupportBoardService;
import com.evernox.service.impl.SupportBoardSseRegistry.PixelChange;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 应援板服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupportBoardServiceImpl implements SupportBoardService {

    private final SupportBoardRepository boardRepository;
    private final SupportPixelRepository pixelRepository;
    private final SupportBoardSseRegistry sseRegistry;
    private final SupportBoardPixelCache pixelCache;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public SupportBoardViewResponse getActive() {
        SupportBoard board = activeBoard();
        if (board == null) {
            return null;
        }
        List<SupportPixelItem> pixels = pixelCache.get(board.getId());
        if (pixels == null) {
            pixels = loadPixels(board.getId());
            pixelCache.putAll(board.getId(), pixels);
        }
        return SupportBoardViewResponse.builder()
                .id(board.getId())
                .name(board.getName())
                .width(board.getWidth())
                .height(board.getHeight())
                .active(board.getActive())
                .pixels(toFlatPixels(pixels))
                .build();
    }

    /** 像素对象列表 → 扁平数组 [x, y, colorInt, locked, ...]，缩小首次加载体积 */
    private List<Integer> toFlatPixels(List<SupportPixelItem> pixels) {
        List<Integer> flat = new ArrayList<>(pixels.size() * 4);
        for (SupportPixelItem p : pixels) {
            flat.add(p.getX());
            flat.add(p.getY());
            flat.add(colorToInt(p.getColor()));
            flat.add(p.getLocked());
        }
        return flat;
    }

    private int colorToInt(String color) {
        if (color == null || color.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(color.substring(1), 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @SuppressWarnings("null")
    private List<SupportPixelItem> loadPixels(Long boardId) {
        return pixelRepository.selectList(
                        new LambdaQueryWrapper<SupportPixel>().eq(SupportPixel::getBoardId, boardId))
                .stream()
                .map(p -> SupportPixelItem.builder()
                        .x(p.getX()).y(p.getY()).color(p.getColor()).locked(p.getLocked()).build())
                .toList();
    }

    @Override
    @Transactional
    public void setPixel(Long userId, SupportPixelRequest req) {
        SupportBoard board = activeBoard();
        if (board == null) {
            throw new BusinessException("当前无可用画板");
        }
        Integer x = req.getX();
        Integer y = req.getY();
        if (x == null || y == null || x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
            throw new BusinessException("坐标超出画板范围");
        }

        Long lockedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM support_pixel WHERE board_id=? AND x=? AND y=? AND locked=1",
                Long.class, board.getId(), x, y);
        if (lockedCount != null && lockedCount > 0) {
            throw new BusinessException("该像素已锁定，无法修改");
        }

        String color = req.getColor() == null ? null : req.getColor().trim();
        if (color == null || color.isBlank()) {
            // 橡皮擦：只删自己的点
            int deleted = jdbcTemplate.update(
                    "DELETE FROM support_pixel WHERE board_id=? AND x=? AND y=? AND user_id=?",
                    board.getId(), x, y, userId);
            if (deleted == 0) {
                Long count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM support_pixel WHERE board_id=? AND x=? AND y=?",
                        Long.class, board.getId(), x, y);
                if (count != null && count > 0) {
                    throw new BusinessException("只能擦除自己画的点");
                }
            }
            sseRegistry.broadcastBatch(board.getId(), List.of(new PixelChange(x, y, null, null)));
            pixelCache.erasePixel(board.getId(), x, y);
        } else {
            // 画笔：覆盖任意人，归属变为当前用户
            jdbcTemplate.update(
                    "INSERT INTO support_pixel(board_id, x, y, color, user_id, updated_at) VALUES (?,?,?,?,?,NOW()) " +
                            "ON DUPLICATE KEY UPDATE color=VALUES(color), user_id=VALUES(user_id), updated_at=NOW()",
                    board.getId(), x, y, color, userId);
            sseRegistry.broadcastBatch(board.getId(), List.of(new PixelChange(x, y, color, 0)));
            pixelCache.setPixel(board.getId(), x, y, color);
        }
    }

    @Override
    @Transactional
    public List<SupportPixelRequest> setPixels(Long userId, List<SupportPixelRequest> pixels) {
        SupportBoard board = activeBoard();
        if (board == null) {
            throw new BusinessException("当前无可用画板");
        }
        List<SupportPixelRequest> skipped = new ArrayList<>();
        if (pixels == null || pixels.isEmpty()) {
            return skipped;
        }

        // 1. 校验坐标，收集有效像素并计算包围盒
        List<SupportPixelRequest> valid = new ArrayList<>(pixels.size());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (SupportPixelRequest req : pixels) {
            Integer x = req.getX();
            Integer y = req.getY();
            if (x == null || y == null || x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
                skipped.add(req);
                continue;
            }
            valid.add(req);
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        if (valid.isEmpty()) {
            return skipped;
        }

        // 2. 一次性查出包围盒内所有锁定像素，避免逐点查询数据库
        Set<String> locked = new HashSet<>();
        List<Map<String, Object>> lockedRows = jdbcTemplate.queryForList(
                "SELECT x, y FROM support_pixel WHERE board_id=? AND locked=1 AND x BETWEEN ? AND ? AND y BETWEEN ? AND ?",
                board.getId(), minX, maxX, minY, maxY);
        for (Map<String, Object> row : lockedRows) {
            locked.add(row.get("x") + "," + row.get("y"));
        }

        // 3. 拆分：彩绘走批量 INSERT，橡皮逐个删除
        List<SupportPixelRequest> draw = new ArrayList<>();
        List<SupportPixelRequest> erase = new ArrayList<>();
        for (SupportPixelRequest req : valid) {
            int x = req.getX();
            int y = req.getY();
            if (locked.contains(x + "," + y)) {
                skipped.add(req);
                continue;
            }
            String color = req.getColor() == null ? null : req.getColor().trim();
            if (color == null || color.isBlank()) {
                erase.add(req);
            } else {
                draw.add(req);
            }
        }

        List<PixelChange> changes = new ArrayList<>(draw.size() + erase.size());
        batchInsertDraw(board.getId(), userId, draw, changes);

        for (SupportPixelRequest req : erase) {
            int x = req.getX();
            int y = req.getY();
            int deleted = jdbcTemplate.update(
                    "DELETE FROM support_pixel WHERE board_id=? AND x=? AND y=? AND user_id=?",
                    board.getId(), x, y, userId);
            if (deleted == 0) {
                skipped.add(req);
                continue;
            }
            changes.add(new PixelChange(x, y, null, null));
            pixelCache.erasePixel(board.getId(), x, y);
        }

        sseRegistry.broadcastBatch(board.getId(), changes);
        return skipped;
    }

    /** 批量写入彩绘像素：按 1000 行一组拼接多行 INSERT，避免逐条数据库往返 */
    private void batchInsertDraw(Long boardId, Long userId, List<SupportPixelRequest> draw, List<PixelChange> changes) {
        if (draw.isEmpty()) {
            return;
        }
        final int BATCH = 1000;
        for (int start = 0; start < draw.size(); start += BATCH) {
            int end = Math.min(draw.size(), start + BATCH);
            List<SupportPixelRequest> slice = draw.subList(start, end);
            StringBuilder sql = new StringBuilder(
                    "INSERT INTO support_pixel(board_id, x, y, color, user_id, updated_at) VALUES ");
            List<Object> args = new ArrayList<>(slice.size() * 5);
            for (int i = 0; i < slice.size(); i++) {
                SupportPixelRequest req = slice.get(i);
                if (i > 0) {
                    sql.append(',');
                }
                sql.append("(?,?,?,?,?,NOW())");
                args.add(boardId);
                args.add(req.getX());
                args.add(req.getY());
                args.add(req.getColor().trim());
                args.add(userId);
            }
            sql.append(" ON DUPLICATE KEY UPDATE color=VALUES(color), user_id=VALUES(user_id), updated_at=NOW()");
            jdbcTemplate.update(Objects.requireNonNull(sql.toString()), args.toArray());
            for (SupportPixelRequest req : slice) {
                String color = Objects.requireNonNull(req.getColor().trim());
                changes.add(new PixelChange(req.getX(), req.getY(), color, 0));
                pixelCache.setPixel(boardId, req.getX(), req.getY(), color);
            }
        }
    }

    @Override
    @Transactional
    public void lockPixel(Long adminId, int x, int y) {
        SupportBoard board = activeBoard();
        if (board == null) {
            throw new BusinessException("当前无可用画板");
        }
        if (x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
            throw new BusinessException("坐标超出画板范围");
        }
        jdbcTemplate.update(
                "INSERT INTO support_pixel(board_id, x, y, color, user_id, locked, updated_at) VALUES (?,?,?,?,?,1,NOW()) " +
                        "ON DUPLICATE KEY UPDATE locked=1, updated_at=NOW()",
                board.getId(), x, y, "#000000", adminId);
        String color = currentColor(board.getId(), x, y);
        sseRegistry.broadcastBatch(board.getId(), List.of(new PixelChange(x, y, color, 1)));
        pixelCache.lockPixel(board.getId(), x, y, color);
        log.info("锁定像素: board={}, x={}, y={}", board.getId(), x, y);
    }

    @Override
    @Transactional
    public void unlockPixel(int x, int y) {
        SupportBoard board = activeBoard();
        if (board == null) {
            throw new BusinessException("当前无可用画板");
        }
        if (x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
            throw new BusinessException("坐标超出画板范围");
        }
        jdbcTemplate.update("UPDATE support_pixel SET locked=0 WHERE board_id=? AND x=? AND y=?", board.getId(), x, y);
        String color = currentColor(board.getId(), x, y);
        sseRegistry.broadcastBatch(board.getId(), List.of(new PixelChange(x, y, color, 0)));
        pixelCache.unlockPixel(board.getId(), x, y, color);
        log.info("解锁像素: board={}, x={}, y={}", board.getId(), x, y);
    }

    @Override
    @Transactional
    public void lockPixels(Long adminId, List<SupportPixelRequest> pixels) {
        SupportBoard board = activeBoard();
        if (board == null) {
            throw new BusinessException("当前无可用画板");
        }
        if (pixels == null || pixels.isEmpty()) {
            return;
        }
        // 校验坐标并计算包围盒
        List<SupportPixelRequest> valid = new ArrayList<>(pixels.size());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (SupportPixelRequest p : pixels) {
            Integer x = p.getX();
            Integer y = p.getY();
            if (x == null || y == null || x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
                continue;
            }
            valid.add(p);
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        if (valid.isEmpty()) {
            return;
        }
        // 一次性查出包围盒内已有像素颜色（未画过的显示为黑色）
        Map<String, String> colors = loadColorsInBox(board.getId(), minX, maxX, minY, maxY);
        // 批量写入锁定
        batchInsertLock(board.getId(), adminId, valid);
        List<PixelChange> changes = new ArrayList<>(valid.size());
        for (SupportPixelRequest p : valid) {
            String color = Objects.requireNonNull(colors.getOrDefault(p.getX() + "," + p.getY(), "#000000"));
            changes.add(new PixelChange(p.getX(), p.getY(), color, 1));
            pixelCache.lockPixel(board.getId(), p.getX(), p.getY(), color);
        }
        sseRegistry.broadcastBatch(board.getId(), changes);
        log.info("批量锁定像素: board={}, count={}", board.getId(), valid.size());
    }

    @Override
    @Transactional
    public void unlockPixels(List<SupportPixelRequest> pixels) {
        SupportBoard board = activeBoard();
        if (board == null) {
            throw new BusinessException("当前无可用画板");
        }
        if (pixels == null || pixels.isEmpty()) {
            return;
        }
        List<SupportPixelRequest> valid = new ArrayList<>(pixels.size());
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (SupportPixelRequest p : pixels) {
            Integer x = p.getX();
            Integer y = p.getY();
            if (x == null || y == null || x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
                continue;
            }
            valid.add(p);
            if (x < minX) minX = x;
            if (x > maxX) maxX = x;
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        if (valid.isEmpty()) {
            return;
        }
        // 解锁前先读颜色（用于广播解锁后的真实颜色）
        Map<String, String> colors = loadColorsInBox(board.getId(), minX, maxX, minY, maxY);
        jdbcTemplate.batchUpdate(
                "UPDATE support_pixel SET locked=0 WHERE board_id=? AND x=? AND y=?",
                valid,
                1000,
                (ps, p) -> {
                    ps.setLong(1, board.getId());
                    ps.setInt(2, p.getX());
                    ps.setInt(3, p.getY());
                });
        List<PixelChange> changes = new ArrayList<>(valid.size());
        for (SupportPixelRequest p : valid) {
            String color = Objects.requireNonNull(colors.getOrDefault(p.getX() + "," + p.getY(), "#000000"));
            changes.add(new PixelChange(p.getX(), p.getY(), color, 0));
            pixelCache.unlockPixel(board.getId(), p.getX(), p.getY(), color);
        }
        sseRegistry.broadcastBatch(board.getId(), changes);
        log.info("批量解锁像素: board={}, count={}", board.getId(), valid.size());
    }

    /** 批量写入锁定像素：按 1000 行一组拼接多行 INSERT，避免逐条数据库往返 */
    private void batchInsertLock(Long boardId, Long adminId, List<SupportPixelRequest> pixels) {
        final int BATCH = 1000;
        for (int start = 0; start < pixels.size(); start += BATCH) {
            int end = Math.min(pixels.size(), start + BATCH);
            List<SupportPixelRequest> slice = pixels.subList(start, end);
            StringBuilder sql = new StringBuilder(
                    "INSERT INTO support_pixel(board_id, x, y, color, user_id, locked, updated_at) VALUES ");
            List<Object> args = new ArrayList<>(slice.size() * 6);
            for (int i = 0; i < slice.size(); i++) {
                SupportPixelRequest p = slice.get(i);
                if (i > 0) {
                    sql.append(',');
                }
                sql.append("(?,?,?,?,?,1,NOW())");
                args.add(boardId);
                args.add(p.getX());
                args.add(p.getY());
                args.add("#000000");
                args.add(adminId);
            }
            sql.append(" ON DUPLICATE KEY UPDATE locked=1, updated_at=NOW()");
            jdbcTemplate.update(Objects.requireNonNull(sql.toString()), args.toArray());
        }
    }

    /** 读取包围盒内已有像素颜色，key = "x,y" */
    private Map<String, String> loadColorsInBox(Long boardId, int minX, int maxX, int minY, int maxY) {
        Map<String, String> colors = new HashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT x, y, color FROM support_pixel WHERE board_id=? AND x BETWEEN ? AND ? AND y BETWEEN ? AND ?",
                boardId, minX, maxX, minY, maxY);
        for (Map<String, Object> row : rows) {
            Object color = row.get("color");
            colors.put(row.get("x") + "," + row.get("y"), color == null ? "#000000" : (String) color);
        }
        return colors;
    }

    @NonNull
    private String currentColor(Long boardId, int x, int y) {
        List<String> colors = jdbcTemplate.queryForList(
                "SELECT color FROM support_pixel WHERE board_id=? AND x=? AND y=?",
                String.class, boardId, x, y);
        String color = colors.isEmpty() ? null : colors.get(0);
        return color == null ? "#000000" : color;
    }

    @Override
    public List<SupportBoardResponse> list() {
        return boardRepository.selectList(null).stream()
                .map(SupportBoardResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public SupportBoardResponse create(String name) {
        if (name == null || name.isBlank()) {
            throw new BusinessException("请输入画板名称");
        }
        SupportBoard board = SupportBoard.builder()
                .name(name.trim())
                .width(1600)
                .height(900)
                .active(0)
                .build();
        boardRepository.insert(board);
        log.info("创建应援板: id={}, name={}", board.getId(), board.getName());
        return SupportBoardResponse.from(board);
    }

    @Override
    @Transactional
    public void setActive(Long id) {
        SupportBoard board = boardRepository.selectById(id);
        if (board == null) {
            throw new BusinessException("画板不存在");
        }
        jdbcTemplate.update("UPDATE support_board SET active=0");
        board.setActive(1);
        boardRepository.updateById(board);
        pixelCache.invalidate(id);
        log.info("设置当前应援板: id={}", id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        SupportBoard board = boardRepository.selectById(id);
        if (board == null) {
            throw new BusinessException("画板不存在");
        }
        jdbcTemplate.update("DELETE FROM support_pixel WHERE board_id=?", id);
        boardRepository.deleteById(id);
        pixelCache.invalidate(id);
        log.info("删除应援板: id={}", id);
    }

    @SuppressWarnings("null")
    private SupportBoard activeBoard() {
        return boardRepository.selectOne(new LambdaQueryWrapper<SupportBoard>()
                .eq(SupportBoard::getActive, 1)
                .orderByDesc(SupportBoard::getId)
                .last("LIMIT 1"));
    }
}
