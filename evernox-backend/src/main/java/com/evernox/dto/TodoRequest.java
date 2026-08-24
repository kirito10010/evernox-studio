package com.evernox.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 待办创建/更新请求
 */
@Data
public class TodoRequest {

    /** 待办内容 */
    @NotBlank(message = "待办内容不能为空")
    @Size(max = 500, message = "待办内容不能超过500字")
    private String content;

    /** 优先级: 0低/1中/2高，为空按中处理 */
    @Min(value = 0, message = "优先级取值为 0-2")
    @Max(value = 2, message = "优先级取值为 0-2")
    private Integer priority;

    /** 截止日期，可空 */
    private LocalDate dueDate;
}
