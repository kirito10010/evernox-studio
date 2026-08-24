package com.evernox.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 生产项目创建/更新请求
 */
@Data
public class PerformanceProjectRequest {

    /** 项目名称 */
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 100, message = "项目名称不能超过100字")
    private String name;

    /** 作业定额，必须为正整数 */
    @NotNull(message = "请填写作业定额")
    @DecimalMin(value = "1", message = "作业定额必须为正整数")
    @Digits(integer = 10, fraction = 0, message = "作业定额必须为整数")
    private BigDecimal workQuota;

    /** 质检定额，必须为正整数 */
    @NotNull(message = "请填写质检定额")
    @DecimalMin(value = "1", message = "质检定额必须为正整数")
    @Digits(integer = 10, fraction = 0, message = "质检定额必须为整数")
    private BigDecimal inspectQuota;
}
