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
 * 一键发包方案的必发玩家
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("org_distribution_forced_player")
public class OrgDistributionForcedPlayer {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 方案ID */
    private Long planId;

    /** 组织成员ID（org_member.id） */
    private Long memberId;

    /** 必发礼包ID（必须存在于方案队列中） */
    private Long packageId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
