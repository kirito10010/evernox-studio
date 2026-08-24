package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.QuizImportResponse;
import com.evernox.dto.QuizQuestionRequest;
import com.evernox.dto.QuizQuestionResponse;
import com.evernox.service.QuizQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 火影忍者OL测验管理控制器（仅管理员）
 */
@RestController
@RequestMapping("/admin/quiz")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminQuizController {

    private final QuizQuestionService quizService;

    @GetMapping("/list")
    public Result<IPage<QuizQuestionResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword) {
        return Result.success(quizService.list(page, size, status, keyword));
    }

    @PostMapping
    public Result<QuizQuestionResponse> create(@Valid @RequestBody QuizQuestionRequest request) {
        return Result.success("添加成功", quizService.create(request));
    }

    @PutMapping("/{id}")
    public Result<QuizQuestionResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody QuizQuestionRequest request) {
        return Result.success("更新成功", quizService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        quizService.delete(id);
        return Result.success("删除成功", null);
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestParam List<Long> ids) {
        quizService.batchDelete(ids);
        return Result.success("删除成功", null);
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        quizService.approve(id);
        return Result.success("已通过", null);
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        quizService.reject(id);
        return Result.success("已驳回", null);
    }

    @PostMapping("/import")
    public Result<QuizImportResponse> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.success("导入完成", quizService.importExcel(file));
    }
}
