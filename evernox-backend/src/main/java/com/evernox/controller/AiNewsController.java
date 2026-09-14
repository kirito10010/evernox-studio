package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.AiModelRankItem;
import com.evernox.dto.AiNewsItemResponse;
import com.evernox.entity.AiZhizhiRank;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.AiNewsService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 编程资讯接口（所有登录用户可访问）
 */
@RestController
@RequestMapping("/ai-news")
@RequiredArgsConstructor
public class AiNewsController {

    private final AiNewsService aiNewsService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/list")
    public Result<IPage<AiNewsItemResponse>> list(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "time") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return Result.success(aiNewsService.listItems(source, tag, keyword, sort, page, size, getUserId(request)));
    }

    @GetMapping("/tags")
    public Result<List<String>> tags() {
        return Result.success(aiNewsService.tags());
    }

    @GetMapping("/leaderboard")
    public Result<List<AiModelRankItem>> leaderboard(@RequestParam(defaultValue = "overall") String category) {
        return Result.success(aiNewsService.getLeaderboard(category));
    }

    @GetMapping("/zhizhi-rank")
    public Result<List<AiZhizhiRank>> zhizhiRank(
            @RequestParam(defaultValue = "logic") String category,
            @RequestParam(required = false) String month) {
        return Result.success(aiNewsService.getZhizhiRank(category, month));
    }

    @GetMapping("/zhizhi-months")
    public Result<List<String>> zhizhiMonths(@RequestParam(defaultValue = "logic") String category) {
        return Result.success(aiNewsService.getZhizhiMonths(category));
    }

    @PostMapping("/{id}/favorite")
    public Result<Void> favorite(@PathVariable Long id, HttpServletRequest request) {
        aiNewsService.favorite(getUserId(request), id);
        return Result.success();
    }

    @DeleteMapping("/{id}/favorite")
    public Result<Void> unfavorite(@PathVariable Long id, HttpServletRequest request) {
        aiNewsService.unfavorite(getUserId(request), id);
        return Result.success();
    }

    @GetMapping("/favorites")
    public Result<IPage<AiNewsItemResponse>> favorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {
        return Result.success(aiNewsService.myFavorites(getUserId(request), page, size));
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
