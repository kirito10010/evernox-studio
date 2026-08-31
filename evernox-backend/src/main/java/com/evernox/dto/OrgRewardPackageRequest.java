package com.evernox.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 组织奖励礼包请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrgRewardPackageRequest {

    @NotBlank(message = "礼包名称不能为空")
    @Size(max = 50, message = "礼包名称长度不能超过50字符")
    private String name;

    @NotNull(message = "扣除比例不能为空")
    @DecimalMin(value = "0", message = "扣除比例不能小于0")
    @DecimalMax(value = "1", message = "扣除比例不能大于1")
    private BigDecimal deductionRatio;

    private Integer sortOrder;
}
