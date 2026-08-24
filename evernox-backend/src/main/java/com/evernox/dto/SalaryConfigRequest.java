package com.evernox.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 工资配置创建/更新请求
 */
@Data
public class SalaryConfigRequest {

    /** 基本薪资 */
    @NotNull(message = "请填写基本薪资")
    @DecimalMin(value = "0", message = "基本薪资不能为负")
    private BigDecimal baseSalary;

    /** 岗位绩效 */
    @NotNull(message = "请填写岗位绩效")
    @DecimalMin(value = "0", message = "岗位绩效不能为负")
    private BigDecimal postPerformance;

    /** 餐补 */
    @NotNull(message = "请填写餐补")
    @DecimalMin(value = "0", message = "餐补不能为负")
    private BigDecimal mealAllowance;

    /** 房补 */
    @NotNull(message = "请填写房补")
    @DecimalMin(value = "0", message = "房补不能为负")
    private BigDecimal housingAllowance;

    /** 全勤奖 */
    @NotNull(message = "请填写全勤奖")
    @DecimalMin(value = "0", message = "全勤奖不能为负")
    private BigDecimal fullAttendanceBonus;

    /** 其他奖金 */
    @NotNull(message = "请填写其他奖金")
    @DecimalMin(value = "0", message = "其他奖金不能为负")
    private BigDecimal otherBonus;

    /** 养老保险（扣除） */
    @NotNull(message = "请填写养老保险")
    @DecimalMin(value = "0", message = "养老保险不能为负")
    private BigDecimal pension;

    /** 医疗保险（扣除） */
    @NotNull(message = "请填写医疗保险")
    @DecimalMin(value = "0", message = "医疗保险不能为负")
    private BigDecimal medicalInsurance;

    /** 失业保险（扣除） */
    @NotNull(message = "请填写失业保险")
    @DecimalMin(value = "0", message = "失业保险不能为负")
    private BigDecimal unemploymentInsurance;
}
