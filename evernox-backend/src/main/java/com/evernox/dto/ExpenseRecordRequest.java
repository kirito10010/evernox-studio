package com.evernox.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 消费记录创建/更新请求
 */
@Data
public class ExpenseRecordRequest {

    /** 消费类型ID，必须属于当前用户 */
    @NotNull(message = "请选择消费类型")
    private Long categoryId;

    /** 消费金额，必须大于 0，最多 10 位整数 + 2 位小数 */
    @NotNull(message = "请填写消费金额")
    @DecimalMin(value = "0.01", message = "消费金额必须大于 0")
    @Digits(integer = 10, fraction = 2, message = "消费金额格式不正确")
    private BigDecimal amount;

    /** 消费日期，只到天 */
    @NotNull(message = "请选择消费日期")
    private LocalDate expenseDate;

    /** 备注，可空 */
    @Size(max = 500, message = "备注不能超过500字")
    private String remark;
}
