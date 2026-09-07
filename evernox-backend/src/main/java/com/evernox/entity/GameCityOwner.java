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
 * 沙盘争霸城市归属（共享地图：每轮每城的归属/等级/驻防）
 *
 * 无主城市不落行（无该(round_id, city_id)记录即代表无主）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("game_city_owner")
public class GameCityOwner {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roundId;

    private Long cityId;

    /** 归属用户ID */
    private Long ownerUserId;

    /** 城市等级（繁荣度） */
    private Integer level;

    /** 驻防兵力 */
    private Integer garrison;

    private LocalDateTime capturedAt;

    private LocalDateTime updatedAt;
}
