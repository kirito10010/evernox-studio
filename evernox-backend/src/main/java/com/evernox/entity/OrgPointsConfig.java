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
 * 组织积分换算比配置（单行，id 固定为 1）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("org_points_config")
public class OrgPointsConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属组织ID */
    private Long organizationId;

    /** 忍战次数: 每1次积分 */
    private BigDecimal ninjaBattlePoints;
    /** 忍战次数: 是否启用 0否/1是 */
    private Integer ninjaBattleEnabled;
    /** 总战力: 每1战力积分 */
    private BigDecimal totalPowerPoints;
    /** 总战力: 是否启用 0否/1是 */
    private Integer totalPowerEnabled;
    /** 战力增幅: 每1战力积分 */
    private BigDecimal powerIncreasePoints;
    /** 战力增幅: 是否启用 0否/1是 */
    private Integer powerIncreaseEnabled;
    /** 铜币贡献: 每1积分 */
    private BigDecimal copperPoints;
    /** 铜币贡献: 是否启用 0否/1是 */
    private Integer copperEnabled;
    /** 通灵兽献祭: 每1积分 */
    private BigDecimal beastPoints;
    /** 通灵兽献祭: 是否启用 0否/1是 */
    private Integer beastEnabled;
    /** 叛忍: 每1次积分 */
    private BigDecimal renegadePoints;
    /** 叛忍: 是否启用 0否/1是 */
    private Integer renegadeEnabled;
    /** 叛忍车头额外积分 */
    private BigDecimal renegadeLeaderBonus;
    /** 叛忍车头: 是否启用 0否/1是 */
    private Integer renegadeLeaderEnabled;
    /** 未领礼包玩家积分继承调整(可正可负) */
    private BigDecimal noPackageAdjustment;
    /** 忍战次数: 是否显示列 0否/1是 */
    private Integer ninjaBattleVisible;
    /** 总战力: 是否显示列 0否/1是 */
    private Integer totalPowerVisible;
    /** 战力增幅: 是否显示列 0否/1是 */
    private Integer powerIncreaseVisible;
    /** 铜币贡献: 是否显示列 0否/1是 */
    private Integer copperVisible;
    /** 通灵兽献祭: 是否显示列 0否/1是 */
    private Integer beastVisible;
    /** 叛忍: 是否显示列 0否/1是 */
    private Integer renegadeVisible;
    /** 叛忍车头: 是否显示列 0否/1是 */
    private Integer renegadeLeaderVisible;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
