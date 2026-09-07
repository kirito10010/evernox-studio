package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 沙盘争霸玩家每周参赛状态
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("game_player")
public class GamePlayer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roundId;

    private Long userId;

    /** 本营城市ID */
    private Long baseCityId;

    /** 当前兵力 */
    private Integer force;

    /** 当前体力（有上限） */
    private Integer actionPoints;

    /** 本周新加入：0否/1是（新星榜用） */
    private Integer isNew;

    private LocalDateTime joinedAt;

    /** 上次小时发放时间（用于按小时结算资源） */
    private LocalDateTime lastGrantAt;

    private LocalDateTime updatedAt;
}
