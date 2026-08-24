package com.evernox.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量删除请求（加班/迟到多选删除通用）
 */
@Data
public class BatchDeleteRequest {

    /** 待删除记录ID列表 */
    @NotEmpty(message = "请选择要删除的记录")
    private List<Long> ids;
}
