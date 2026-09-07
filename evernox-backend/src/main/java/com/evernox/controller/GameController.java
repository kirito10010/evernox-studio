package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.GameActionRequest;
import com.evernox.dto.GameMapResponse;
import com.evernox.dto.GameRankItemDto;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.GameService;
import com.evernox.service.impl.GameSseRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 沙盘争霸游戏控制器
 */
@RestController
@RequestMapping("/game")
@RequiredArgsConstructor
public class GameController {

    private final GameService gameService;
    private final GameSseRegistry sseRegistry;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/map")
    public Result<GameMapResponse> map(HttpServletRequest request) {
        return Result.success(gameService.map(getUserId(request)));
    }

    @PostMapping("/join")
    public Result<Void> join(@RequestParam Long baseCityId, HttpServletRequest request) {
        gameService.join(getUserId(request), baseCityId);
        return Result.success("已加入", null);
    }

    @PostMapping("/action")
    public Result<Void> action(@RequestBody GameActionRequest body, HttpServletRequest request) {
        gameService.act(getUserId(request), body);
        return Result.success();
    }

    @GetMapping("/rank")
    public Result<List<GameRankItemDto>> rank(HttpServletRequest request) {
        return Result.success(gameService.rank(getUserId(request)));
    }

    /** SSE 实时推送：EventSource 无法带 Authorization 头，token 走 query 参数 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestParam("token") String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(sseRegistry.register());
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
