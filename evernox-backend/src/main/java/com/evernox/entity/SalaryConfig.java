package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 工资配置实体（纯私有，每用户一行）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("salary_config")
public class SalaryConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 基本薪资 */
    private BigDecimal baseSalary;

    /** 岗位绩效 */
    private BigDecimal postPerformance;

    /** 餐补 */
    private BigDecimal mealAllowance;

    /** 房补 */
    private BigDecimal housingAllowance;

    /** 全勤奖 */
    private BigDecimal fullAttendanceBonus;

    /** 其他奖金 */
    private BigDecimal otherBonus;

    /** 养老保险（扣除） */
    private BigDecimal pension;

    /** 医疗保险（扣除） */
    private BigDecimal medicalInsurance;

    /** 失业保险（扣除） */
    private BigDecimal unemploymentInsurance;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
