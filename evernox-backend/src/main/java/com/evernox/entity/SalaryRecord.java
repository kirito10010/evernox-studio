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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 工资记录实体（纯私有，每用户每月一条）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("salary_record")
public class SalaryRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 月份 YYYY-MM */
    private String month;

    /** 周期开始日期 */
    private LocalDate startDate;

    /** 周期结束日期 */
    private LocalDate endDate;

    /** 应出勤天数（可手动改） */
    private BigDecimal attendanceDays;

    /** 实际上班天数（绩效记录去重天数） */
    private BigDecimal actualAttendanceDays;

    /** 净绩效 */
    private BigDecimal performanceDays;

    /** 绩效薪资 */
    private BigDecimal performanceSalary;

    /** 加班天数 */
    private BigDecimal overtimeDays;

    /** 加班工资 */
    private BigDecimal overtimeSalary;

    /** 迟到总分钟数 */
    private Integer lateMinutes;

    /** 出勤比 */
    private BigDecimal attendanceRatio;

    /** 基本薪资（打折后） */
    private BigDecimal baseSalary;

    /** 岗位绩效（打折后） */
    private BigDecimal postPerformance;

    /** 餐补（打折后） */
    private BigDecimal mealAllowance;

    /** 房补（打折后） */
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

    /** 合计 */
    private BigDecimal totalSalary;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
