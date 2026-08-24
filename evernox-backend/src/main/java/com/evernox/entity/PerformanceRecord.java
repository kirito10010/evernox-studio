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
 * 绩效记录实体（纯私有）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("performance_record")
public class PerformanceRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 项目ID */
    private Long projectId;

    /** 工作日期 */
    private LocalDate workDate;

    /** 工序类型: 0作业/1质检 */
    private Integer processType;

    /** 定额效率(记录时快照) */
    private BigDecimal quota;

    /** 实际工作量 */
    private BigDecimal actualWorkload;

    /** 绩效人天 = 实际工作量 / 定额效率 */
    private BigDecimal performanceDays;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
