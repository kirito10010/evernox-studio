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
 * 沙盘争霸轮次（每周一轮）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("game_round")
public class GameRound {

    public static final int STATUS_NOT_STARTED = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_SETTLED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 轮次号 */
    private Integer roundNo;

    /** 状态：0未开始/1进行中/2已结算 */
    private Integer status;

    /** 开局时间（周一10点） */
    private LocalDateTime startAt;

    /** 结算时间（周日18点） */
    private LocalDateTime settleAt;

    /** 结束时间（下周一10点） */
    private LocalDateTime endAt;

    private LocalDateTime createdAt;
}
