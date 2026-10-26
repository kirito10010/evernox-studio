package com.evernox.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 绩效记录创建/更新请求（定额与绩效人天由后端计算）
 */
@Data
public class PerformanceRecordRequest {

    /** 项目ID，必须属于当前用户 */
    @NotNull(message = "请选择项目")
    private Long projectId;

    /** 工作日期 */
    @NotNull(message = "请选择日期")
    private LocalDate workDate;

    /** 工序类型: 0作业/1质检 */
    @NotNull(message = "请选择工序类型")
    @Min(value = 0, message = "工序类型不正确")
    @Max(value = 1, message = "工序类型不正确")
    private Integer processType;

    /** 实际工作量，最多 3 位小数（定额为 8 时，0.125 表示 1 小时） */
    @NotNull(message = "请填写实际工作量")
    @DecimalMin(value = "0.001", message = "实际工作量必须大于 0")
    @Digits(integer = 10, fraction = 3, message = "实际工作量最多保留 3 位小数")
    private BigDecimal actualWorkload;
}
