package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.QuizQuestionRequest;
import com.evernox.dto.QuizQuestionResponse;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.QuizQuestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 火影忍者OL测验控制器（用户侧）
 */
@RestController
@RequestMapping("/quiz")
@RequiredArgsConstructor
public class QuizController {

    private final QuizQuestionService quizService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/search")
    public Result<List<QuizQuestionResponse>> search(@RequestParam(required = false) String keyword) {
        return Result.success(quizService.search(keyword));
    }

    @PostMapping("/submit")
    public Result<QuizQuestionResponse> submit(@Valid @RequestBody QuizQuestionRequest request,
                                               HttpServletRequest http) {
        return Result.success("提交成功，等待管理员审核", quizService.submit(request, getUserId(http)));
    }

    @GetMapping("/my-submissions")
    public Result<List<QuizQuestionResponse>> mySubmissions(HttpServletRequest http) {
        return Result.success(quizService.listMine(getUserId(http)));
    }

    @PutMapping("/my-submission/{id}")
    public Result<QuizQuestionResponse> updateMySubmission(@PathVariable Long id,
                                                           @Valid @RequestBody QuizQuestionRequest request,
                                                           HttpServletRequest http) {
        return Result.success("修改并重新提交成功", quizService.updateMine(id, request, getUserId(http)));
    }

    @DeleteMapping("/my-submission/{id}")
    public Result<Void> deleteMySubmission(@PathVariable Long id, HttpServletRequest http) {
        quizService.deleteMine(id, getUserId(http));
        return Result.success("删除成功", null);
    }

    @PostMapping("/my-submission/{id}/resubmit")
    public Result<Void> resubmit(@PathVariable Long id, HttpServletRequest http) {
        quizService.resubmit(id, getUserId(http));
        return Result.success("已重新提交", null);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
