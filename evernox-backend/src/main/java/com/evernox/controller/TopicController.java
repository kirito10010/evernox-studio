package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.TopicCircleRequest;
import com.evernox.dto.TopicCircleResponse;
import com.evernox.dto.TopicCommentRequest;
import com.evernox.dto.TopicCommentResponse;
import com.evernox.dto.TopicInteractionResponse;
import com.evernox.dto.TopicMemberResponse;
import com.evernox.dto.TopicPostRequest;
import com.evernox.dto.TopicPostResponse;
import com.evernox.dto.TopicRankResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.ImageService;
import com.evernox.service.TopicCircleService;
import com.evernox.service.TopicCommentService;
import com.evernox.service.TopicPostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 话题集中营控制器（用户侧）
 */
@RestController
@RequestMapping("/topic")
@RequiredArgsConstructor
public class TopicController {

    private final TopicCircleService circleService;
    private final TopicPostService postService;
    private final TopicCommentService commentService;
    private final ImageService imageService;
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== 圈子 ====================

    @PostMapping("/circle")
    public Result<TopicCircleResponse> createCircle(@Valid @RequestBody TopicCircleRequest request,
                                                    HttpServletRequest http) {
        return Result.success("创建成功", circleService.create(request, getUserId(http)));
    }

    @GetMapping("/circle/list")
    public Result<IPage<TopicCircleResponse>> listCircles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean mine,
            HttpServletRequest http) {
        return Result.success(circleService.list(page, size, keyword, mine, getUserId(http)));
    }

    @GetMapping("/circle/{id}")
    public Result<TopicCircleResponse> circleDetail(@PathVariable Long id, HttpServletRequest http) {
        return Result.success(circleService.getDetail(id, getUserId(http)));
    }

    @PutMapping("/circle/{id}")
    public Result<TopicCircleResponse> updateCircle(@PathVariable Long id,
                                                    @Valid @RequestBody TopicCircleRequest request,
                                                    HttpServletRequest http) {
        return Result.success("更新成功", circleService.update(id, request, getUserId(http)));
    }

    @DeleteMapping("/circle/{id}")
    public Result<Void> deleteCircle(@PathVariable Long id, HttpServletRequest http) {
        circleService.delete(id, getUserId(http));
        return Result.success("删除成功", null);
    }

    @PostMapping("/circle/{id}/follow")
    public Result<Void> follow(@PathVariable Long id, HttpServletRequest http) {
        circleService.follow(id, getUserId(http));
        return Result.success();
    }

    @DeleteMapping("/circle/{id}/follow")
    public Result<Void> unfollow(@PathVariable Long id, HttpServletRequest http) {
        circleService.unfollow(id, getUserId(http));
        return Result.success();
    }

    @PostMapping("/circle/{id}/transfer")
    public Result<Void> transferCircle(@PathVariable Long id, @RequestParam Long userId, HttpServletRequest http) {
        circleService.transfer(id, userId, getUserId(http));
        return Result.success("转让成功", null);
    }

    @GetMapping("/circle/{id}/members")
    public Result<List<TopicMemberResponse>> listMembers(@PathVariable Long id, HttpServletRequest http) {
        return Result.success(circleService.listMembers(id, getUserId(http)));
    }

    @GetMapping("/rank")
    public Result<TopicRankResponse> rank() {
        return Result.success(postService.getRank());
    }

    // ==================== 帖子 ====================

    @PostMapping("/post")
    public Result<TopicPostResponse> createPost(@Valid @RequestBody TopicPostRequest request,
                                                HttpServletRequest http) {
        return Result.success("发布成功", postService.create(request, getUserId(http)));
    }

    @GetMapping("/post/list")
    public Result<IPage<TopicPostResponse>> square(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "hot") String sort,
            HttpServletRequest http) {
        return Result.success(postService.listSquare(page, size, sort, getUserId(http)));
    }

    @GetMapping("/post/circle/{circleId}")
    public Result<IPage<TopicPostResponse>> circlePosts(
            @PathVariable Long circleId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest http) {
        return Result.success(postService.listByCircle(page, size, circleId, getUserId(http)));
    }

    @GetMapping("/post/mine")
    public Result<IPage<TopicPostResponse>> myPosts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest http) {
        return Result.success(postService.listMine(page, size, getUserId(http)));
    }

    @GetMapping("/post/favorites")
    public Result<IPage<TopicPostResponse>> myFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest http) {
        return Result.success(postService.listFavorites(page, size, getUserId(http)));
    }

    @GetMapping("/post/{id}")
    public Result<TopicPostResponse> postDetail(@PathVariable Long id, HttpServletRequest http) {
        return Result.success(postService.getDetail(id, getUserId(http)));
    }

    @PutMapping("/post/{id}")
    public Result<TopicPostResponse> updatePost(@PathVariable Long id,
                                                @Valid @RequestBody TopicPostRequest request,
                                                HttpServletRequest http) {
        return Result.success("更新成功", postService.update(id, request, getUserId(http)));
    }

    @DeleteMapping("/post/{id}")
    public Result<Void> deletePost(@PathVariable Long id, HttpServletRequest http) {
        postService.delete(id, getUserId(http));
        return Result.success("删除成功", null);
    }

    @PostMapping("/post/{id}/like")
    public Result<TopicInteractionResponse> like(@PathVariable Long id, HttpServletRequest http) {
        return Result.success(postService.toggleLike(id, getUserId(http)));
    }

    @PostMapping("/post/{id}/favorite")
    public Result<TopicInteractionResponse> favorite(@PathVariable Long id, HttpServletRequest http) {
        return Result.success(postService.toggleFavorite(id, getUserId(http)));
    }

    // ==================== 评论 ====================

    @GetMapping("/comment/list")
    public Result<IPage<TopicCommentResponse>> listComments(
            @RequestParam Long postId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(commentService.list(postId, page, size));
    }

    @PostMapping("/comment")
    public Result<TopicCommentResponse> createComment(@Valid @RequestBody TopicCommentRequest request,
                                                      HttpServletRequest http) {
        return Result.success("评论成功", commentService.create(request, getUserId(http)));
    }

    @DeleteMapping("/comment/{id}")
    public Result<Void> deleteComment(@PathVariable Long id, HttpServletRequest http) {
        commentService.delete(id, getUserId(http));
        return Result.success("删除成功", null);
    }

    // ==================== 图片 ====================

    @GetMapping("/image/{imageId}/file")
    public ResponseEntity<Resource> imageFile(@PathVariable Long imageId) {
        Resource resource = imageService.getTopicImageFile(imageId, null);
        String mimeType = imageService.getTopicImageMimeType(imageId);
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
}
