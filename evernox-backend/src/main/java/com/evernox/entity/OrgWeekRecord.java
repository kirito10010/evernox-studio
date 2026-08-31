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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 组织每周成员记录实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("org_week_record")
public class OrgWeekRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 组织ID快照 */
    private Long organizationId;

    /** 组织名称快照 */
    private String organizationName;

    /** 本周周日 */
    private LocalDate weekDate;

    /** 成员ID（成员删除后置空，保留快照） */
    private Long memberId;

    /** 玩家名快照 */
    private String memberName;

    /** 职务快照 */
    private String position;

    /** 忍战次数 */
    private Integer ninjaBattleCount;

    /** 总战力 */
    private Integer totalPower;

    /** 战力增幅 */
    private Integer powerIncrease;

    /** 铜币贡献 */
    private Integer copperContribution;

    /** 通灵兽献祭 */
    private Integer beastSacrifice;

    /** 叛忍次数 */
    private Integer renegadeCount;

    /** 是否叛忍车头: 0否/1是 */
    private Integer isRenegadeLeader;

    /** 上周剩余积分 */
    private BigDecimal lastWeekPoints;

    /** 本周积分 */
    private BigDecimal thisWeekPoints;

    /** 总积分 */
    private BigDecimal totalPoints;

    /** 扣除比例(来自礼包) */
    private BigDecimal deductionRatio;

    /** 扣除后总积分 */
    private BigDecimal pointsAfterDeduction;

    /** 奖励礼包ID */
    private Long rewardPackageId;

    /** 礼包名称快照 */
    private String rewardPackageName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
