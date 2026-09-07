package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.PointsService;
import com.evernox.service.impl.UserPointsSseRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 积分接口（签到）
 */
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserPointsSseRegistry pointsSseRegistry;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestParam("token") String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Long userId = jwtTokenProvider.getUserIdFromToken(token);
        return ResponseEntity.ok(pointsSseRegistry.register(userId));
    }

    @PostMapping("/sign-in")
    public Result<Void> signIn(HttpServletRequest request) {
        pointsService.signIn(getUserId(request));
        return Result.success("签到成功，+10 积分", null);
    }

    @PostMapping("/upgrade-super-member")
    public Result<Void> upgradeSuperMember(@RequestParam Integer days, HttpServletRequest request) {
        pointsService.upgradeSuperMember(getUserId(request), days);
        return Result.success("开通成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
