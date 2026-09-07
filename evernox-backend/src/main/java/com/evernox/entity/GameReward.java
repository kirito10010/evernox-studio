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
 * 沙盘争霸结算发奖记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("game_reward")
public class GameReward {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roundId;

    private Long userId;

    /** 排名（参与奖可为0） */
    private Integer rank;

    private Integer points;

    /** 榜单类型：total/rookie/participation */
    private String boardType;

    private LocalDateTime createdAt;
}
