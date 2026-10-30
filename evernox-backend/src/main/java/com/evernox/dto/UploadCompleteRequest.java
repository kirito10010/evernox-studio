package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 完成分片上传
 */
@Data
public class UploadCompleteRequest {

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;
}
