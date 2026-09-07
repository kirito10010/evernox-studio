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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                .pixels(pixels)
                .build();
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
            sseRegistry.broadcast(board.getId(), x, y, null, null);
            pixelCache.erasePixel(board.getId(), x, y);
        } else {
            // 画笔：覆盖任意人，归属变为当前用户
            jdbcTemplate.update(
                    "INSERT INTO support_pixel(board_id, x, y, color, user_id, updated_at) VALUES (?,?,?,?,?,NOW()) " +
                            "ON DUPLICATE KEY UPDATE color=VALUES(color), user_id=VALUES(user_id), updated_at=NOW()",
                    board.getId(), x, y, color, userId);
            sseRegistry.broadcast(board.getId(), x, y, color, 0);
            pixelCache.setPixel(board.getId(), x, y, color);
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
        sseRegistry.broadcast(board.getId(), x, y, color, 1);
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
        sseRegistry.broadcast(board.getId(), x, y, color, 0);
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
        for (SupportPixelRequest p : pixels) {
            Integer x = p.getX();
            Integer y = p.getY();
            if (x == null || y == null || x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO support_pixel(board_id, x, y, color, user_id, locked, updated_at) VALUES (?,?,?,?,?,1,NOW()) " +
                            "ON DUPLICATE KEY UPDATE locked=1, updated_at=NOW()",
                    board.getId(), x, y, "#000000", adminId);
        }
        for (SupportPixelRequest p : pixels) {
            Integer x = p.getX();
            Integer y = p.getY();
            if (x == null || y == null || x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
                continue;
            }
            String color = currentColor(board.getId(), x, y);
            sseRegistry.broadcast(board.getId(), x, y, color, 1);
            pixelCache.lockPixel(board.getId(), x, y, color);
        }
        log.info("批量锁定像素: board={}, count={}", board.getId(), pixels.size());
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
        for (SupportPixelRequest p : pixels) {
            Integer x = p.getX();
            Integer y = p.getY();
            if (x == null || y == null || x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
                continue;
            }
            jdbcTemplate.update("UPDATE support_pixel SET locked=0 WHERE board_id=? AND x=? AND y=?", board.getId(), x, y);
        }
        for (SupportPixelRequest p : pixels) {
            Integer x = p.getX();
            Integer y = p.getY();
            if (x == null || y == null || x < 0 || y < 0 || x >= board.getWidth() || y >= board.getHeight()) {
                continue;
            }
            String color = currentColor(board.getId(), x, y);
            sseRegistry.broadcast(board.getId(), x, y, color, 0);
            pixelCache.unlockPixel(board.getId(), x, y, color);
        }
        log.info("批量解锁像素: board={}, count={}", board.getId(), pixels.size());
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
