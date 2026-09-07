package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.VisitTrackRequest;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.VisitLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 访问上报（普通登录用户）
 */
@RestController
@RequestMapping("/visit")
@RequiredArgsConstructor
public class VisitController {

    private final VisitLogService visitLogService;
    private final JwtTokenProvider jwtTokenProvider;

    @PostMapping("/track")
    public Result<Void> track(@RequestBody(required = false) VisitTrackRequest body, HttpServletRequest request) {
        Long userId = getUserId(request);
        if (userId == null) {
            return Result.success();
        }
        String path = body == null ? null : body.getPath();
        visitLogService.recordVisit(userId, resolveClientIp(request), request.getHeader("User-Agent"), path);
        return Result.success();
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
