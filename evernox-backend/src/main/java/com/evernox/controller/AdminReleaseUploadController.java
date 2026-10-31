package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.ReleaseAssetResponse;
import com.evernox.dto.UploadCompleteRequest;
import com.evernox.dto.UploadInitRequest;
import com.evernox.dto.UploadInitResponse;
import com.evernox.entity.ReleaseAsset;
import com.evernox.security.SecurityUtils;
import com.evernox.service.ReleaseStorageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理员 - 版本附件分片上传
 */
@RestController
@RequestMapping("/admin/release")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('admin', 'super_member')")
public class AdminReleaseUploadController {

    private final ReleaseStorageService storageService;

    @PostMapping("/upload/init")
    public Result<UploadInitResponse> init(@Valid @RequestBody UploadInitRequest request) {
        return Result.success(storageService.initUpload(
                request.getProjectId(), request.getFileName(), request.getFileSize(),
                SecurityUtils.currentUserId()));
    }

    @PostMapping("/upload/chunk")
    public Result<Integer> chunk(@RequestParam String uploadId,
                                 @RequestParam int index,
                                 @RequestPart("chunk") MultipartFile chunk) {
        return Result.success("分片已上传",
                storageService.saveChunk(uploadId, index, chunk, SecurityUtils.currentUserId()));
    }

    @PostMapping("/upload/complete")
    public Result<ReleaseAssetResponse> complete(@Valid @RequestBody UploadCompleteRequest request) {
        ReleaseAsset asset = storageService.completeUpload(request.getUploadId(),
                SecurityUtils.currentUserId());

        return Result.success("上传完成", toResponse(asset));
    }

    @DeleteMapping("/upload/{uploadId}")
    public Result<Void> cancel(@PathVariable String uploadId) {
        storageService.cancelUpload(uploadId, SecurityUtils.currentUserId());
        return Result.success("上传已取消", null);
    }

    @DeleteMapping("/asset/{id}")
    public Result<Void> deleteStagedAsset(@PathVariable Long id) {
        storageService.deleteStagedAsset(id, SecurityUtils.currentUserId());
        return Result.success("附件已删除", null);
    }


    private ReleaseAssetResponse toResponse(ReleaseAsset asset) {
        return ReleaseAssetResponse.builder()
                .id(asset.getId())
                .fileName(asset.getFileName())
                .size(asset.getSize())
                .sha512(asset.getSha512())
                .downloadCount(asset.getDownloadCount())
                .createdAt(asset.getCreatedAt())
                .build();
    }
}
