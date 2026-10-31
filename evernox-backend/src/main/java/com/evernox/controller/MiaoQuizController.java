package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.MiaoPendingResponse;
import com.evernox.dto.MiaoQuestionRequest;
import com.evernox.dto.MiaoQuestionResponse;
import com.evernox.dto.QuizVoteRequest;
import com.evernox.dto.QuizVoteResponse;
import com.evernox.security.SecurityUtils;
import com.evernox.service.MiaoQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 火影忍者OL 喵问答控制器（用户侧）
 */
@RestController
@RequestMapping("/miao")
@RequiredArgsConstructor
public class MiaoQuizController {

    private final MiaoQuestionService miaoService;

    @GetMapping("/search")
    public Result<List<MiaoQuestionResponse>> search(@RequestParam(required = false) String keyword) {
        return Result.success(miaoService.search(keyword, SecurityUtils.currentUserIdOrNull()));
    }

    /** 待确认（无答案）题目，匿名可看，登录后带上自己已投的答案 */
    @GetMapping("/unanswered")
    public Result<MiaoPendingResponse> unanswered(@RequestParam(defaultValue = "2") int limit) {
        return Result.success(miaoService.unanswered(limit, SecurityUtils.currentUserIdOrNull()));
    }

    /** 对无答案题目投票；连续 3 位用户选择一致后自动写入答案并奖励积分 */
    @PostMapping("/vote")
    public Result<QuizVoteResponse> vote(@Valid @RequestBody QuizVoteRequest request) {
        return Result.success(miaoService.vote(request, SecurityUtils.currentUserId()));
    }

    @PostMapping("/submit")
    public Result<MiaoQuestionResponse> submit(@Valid @RequestBody MiaoQuestionRequest request) {
        return Result.success("提交成功，等待管理员审核",
                miaoService.submit(request, SecurityUtils.currentUserId()));
    }

    @GetMapping("/my-submissions")
    public Result<List<MiaoQuestionResponse>> mySubmissions() {
        return Result.success(miaoService.listMine(SecurityUtils.currentUserId()));
    }

    @PutMapping("/my-submission/{id}")
    public Result<MiaoQuestionResponse> updateMySubmission(@PathVariable Long id,
                                                           @Valid @RequestBody MiaoQuestionRequest request) {
        return Result.success("修改并重新提交成功",
                miaoService.updateMine(id, request, SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/my-submission/{id}")
    public Result<Void> deleteMySubmission(@PathVariable Long id) {
        miaoService.deleteMine(id, SecurityUtils.currentUserId());
        return Result.success("删除成功", null);
    }

    @PostMapping("/my-submission/{id}/resubmit")
    public Result<Void> resubmit(@PathVariable Long id) {
        miaoService.resubmit(id, SecurityUtils.currentUserId());
        return Result.success("已重新提交", null);
    }
}
