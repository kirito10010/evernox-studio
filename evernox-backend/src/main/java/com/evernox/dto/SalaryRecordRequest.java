package com.evernox.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资记录创建请求
 */
@Data
public class SalaryRecordRequest {

    /** 月份 YYYY-MM */
    @NotBlank(message = "请选择月份")
    private String month;

    /** 出勤天数（可手动改） */
    @NotNull(message = "请填写出勤天数")
    @DecimalMin(value = "0", message = "出勤天数不能为负")
    private BigDecimal attendanceDays;
}
