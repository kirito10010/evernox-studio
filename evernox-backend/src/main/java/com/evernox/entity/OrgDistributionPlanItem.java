package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 一键发包方案中的礼包队列条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("org_distribution_plan_item")
public class OrgDistributionPlanItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案ID */
    private Long planId;

    /** 礼包ID */
    private Long packageId;

    /** 发放数量 */
    private Integer quantity;

    /** 队列顺序 */
    private Integer sortOrder;
}
