package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 组织一键发包方案
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("org_distribution_plan")
public class OrgDistributionPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属组织ID */
    private Long organizationId;

    /** 方案名称 */
    private String name;

    /** 排名依据字段：totalPoints / thisWeekPoints / powerIncrease / totalPower / copperContribution / beastSacrifice / renegadeCount */
    private String rankField;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
