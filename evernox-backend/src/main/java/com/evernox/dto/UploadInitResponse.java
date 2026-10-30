package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 初始化分片上传响应
 */
@Data
@AllArgsConstructor
public class UploadInitResponse {

    private String uploadId;
    private long chunkSize;
    private int totalChunks;
}
