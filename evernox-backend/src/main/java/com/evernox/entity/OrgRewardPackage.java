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

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 组织奖励礼包实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("org_reward_package")
public class OrgRewardPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属组织ID */
    private Long organizationId;

    /** 礼包名称 */
    private String name;

    /** 扣除比例(0~1) */
    private BigDecimal deductionRatio;

    /** 排序 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
