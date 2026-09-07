package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.SupportBoardViewResponse;
import com.evernox.dto.SupportPixelRequest;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.SupportBoardService;
import com.evernox.service.impl.SupportBoardSseRegistry;
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

/**
 * 应援板用户侧接口
 */
@RestController
@RequestMapping("/support-board")
@RequiredArgsConstructor
public class SupportBoardController {

    private final SupportBoardService supportBoardService;
    private final SupportBoardSseRegistry sseRegistry;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/active")
    public Result<SupportBoardViewResponse> active() {
        return Result.success(supportBoardService.getActive());
    }

    @PostMapping("/pixel")
    public Result<Void> pixel(@RequestBody SupportPixelRequest request, HttpServletRequest http) {
        supportBoardService.setPixel(getUserId(http), request);
        return Result.success();
    }

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
