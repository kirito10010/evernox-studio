package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.AnnouncementResponse;
import com.evernox.dto.UnreadCountResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.AnnouncementService;
import com.evernox.service.ImageService;
import com.evernox.service.impl.AnnouncementSseRegistry;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 公告控制器（用户侧，所有登录用户可见）
 */
@RestController
@RequestMapping("/announcement")
@RequiredArgsConstructor
public class AnnouncementController {

    private final AnnouncementService announcementService;
    private final ImageService imageService;
    private final JwtTokenProvider jwtTokenProvider;
    private final AnnouncementSseRegistry sseRegistry;

    @GetMapping("/list")
    public Result<List<AnnouncementResponse>> list(HttpServletRequest request) {
        return Result.success(announcementService.listForUser(getUserId(request)));
    }

    @GetMapping("/unread-count")
    public Result<UnreadCountResponse> unreadCount(HttpServletRequest request) {
        return Result.success(announcementService.unreadCount(getUserId(request)));
    }

    @GetMapping("/{id}")
    public Result<AnnouncementResponse> detail(@PathVariable Long id) {
        return Result.success(announcementService.getDetail(id));
    }

    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, HttpServletRequest request) {
        announcementService.markRead(id, getUserId(request));
        return Result.success();
    }

    /**
     * 公告插图文件
     *
     * 仅放行公告插图（purpose=4），任何登录用户可访问；返回正确 MIME 类型，
     * 由 useImageDecrypt 取流后转 ObjectURL 渲染。
     */
    @GetMapping("/image/{imageId}/file")
    public ResponseEntity<Resource> imageFile(@PathVariable Long imageId) {
        Resource resource = imageService.getAnnouncementImageFile(imageId, null);
        String mimeType = imageService.getAnnouncementImageMimeType(imageId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        mimeType != null ? mimeType : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0, must-revalidate")
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }

    /**
     * 公告实时推送（SSE）
     *
     * EventSource 无法携带 Authorization 头，token 走 query 参数，这里手动校验；
     * 该路径已在 SecurityConfig 中 permitAll。
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> stream(@RequestParam("token") String token) {
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(sseRegistry.register());
    }
}
