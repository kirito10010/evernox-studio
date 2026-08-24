package com.evernox.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 批量修改图片可见性请求
 */
@Data
public class AdminBatchVisibilityRequest {

    @NotEmpty(message = "图片ID列表不能为空")
    private List<Long> ids;

    /** 可见性: 0私密/1公开 */
    @NotNull(message = "可见性不能为空")
    private Integer visibility;
}
