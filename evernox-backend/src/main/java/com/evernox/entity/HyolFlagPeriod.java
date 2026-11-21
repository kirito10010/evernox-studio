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
 * 彩旗献礼「期」：一年约 3 期，一期一份商品清单。
 *
 * <p>与商城特惠不同，这里没有按天快照；同一期重复导入按整期覆盖。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_flag_period")
public class HyolFlagPeriod {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游戏活动ID */
    private Long activityId;

    /** 商店ID */
    private Long shopId;

    /** 活动名（保持原始名） */
    private String name;

    /** 本期开始（JSON 活动.开始） */
    private LocalDateTime startTime;

    /** 本期结束（JSON 活动.结束） */
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
