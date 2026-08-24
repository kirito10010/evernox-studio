package com.evernox.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 迟到记录创建请求（迟到天数由后端计算）
 */
@Data
public class LateRequest {

    /** 工作日期 */
    @NotNull(message = "请选择日期")
    private LocalDate workDate;

    /** 迟到分钟（整数），必须大于0 */
    @NotNull(message = "请填写迟到分钟")
    @Min(value = 1, message = "迟到分钟必须为正整数")
    @Max(value = 480, message = "迟到分钟不能超过480分钟")
    private Integer lateMinutes;
}
