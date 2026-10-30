package com.evernox.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 初始化分片上传
 */
@Data
public class UploadInitRequest {

    @NotNull(message = "项目ID不能为空")
    private Long projectId;

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名过长")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Min(value = 1, message = "文件大小必须大于 0")
    private Long fileSize;
}
