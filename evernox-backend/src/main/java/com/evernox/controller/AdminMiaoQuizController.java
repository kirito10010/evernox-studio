package com.evernox.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.common.Result;
import com.evernox.dto.MiaoImportResponse;
import com.evernox.dto.MiaoQuestionRequest;
import com.evernox.dto.MiaoQuestionResponse;
import com.evernox.service.MiaoQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 喵问答题库管理控制器（仅管理员）
 */
@RestController
@RequestMapping("/admin/miao")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminMiaoQuizController {

    private final MiaoQuestionService miaoService;

    @GetMapping("/list")
    public Result<IPage<MiaoQuestionResponse>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        return Result.success(miaoService.list(
                page, size, status, keyword, startDate, endDate, sortField, sortOrder));
    }

    @PostMapping
    public Result<MiaoQuestionResponse> create(@Valid @RequestBody MiaoQuestionRequest request) {
        return Result.success("添加成功", miaoService.create(request));
    }

    @PutMapping("/{id}")
    public Result<MiaoQuestionResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody MiaoQuestionRequest request) {
        return Result.success("更新成功", miaoService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        miaoService.delete(id);
        return Result.success("删除成功", null);
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestParam List<Long> ids) {
        miaoService.batchDelete(ids);
        return Result.success("删除成功", null);
    }

    @PostMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id) {
        miaoService.approve(id);
        return Result.success("已通过", null);
    }

    @PostMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id) {
        miaoService.reject(id);
        return Result.success("已驳回", null);
    }

    /**
     * 导入题库文件夹：manifest = questions.json，images = 文件夹内所有截图
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<MiaoImportResponse> importFolder(
            @RequestParam("manifest") MultipartFile manifest,
            @RequestParam(value = "images", required = false) List<MultipartFile> images) {
        return Result.success("导入完成", miaoService.importFolder(manifest, images));
    }
}
