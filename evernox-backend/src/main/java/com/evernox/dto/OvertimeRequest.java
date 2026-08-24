package com.evernox.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 加班记录创建/更新请求（加班天数由后端计算）
 */
@Data
public class OvertimeRequest {

    /** 工作日期 */
    @NotNull(message = "请选择日期")
    private LocalDate workDate;

    /** 加班时长（小时，0.5步进），必须大于0 */
    @NotNull(message = "请填写加班时长")
    @DecimalMin(value = "0.5", message = "加班时长至少0.5小时")
    @Digits(integer = 3, fraction = 1, message = "加班时长必须为0.5的倍数")
    private BigDecimal overtimeHours;
}
