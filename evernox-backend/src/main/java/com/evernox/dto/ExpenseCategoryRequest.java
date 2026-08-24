package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 消费类型创建/更新请求
 */
@Data
public class ExpenseCategoryRequest {

    /** 类型名称 */
    @NotBlank(message = "类型名称不能为空")
    @Size(max = 50, message = "类型名称不能超过50字")
    private String name;
}
