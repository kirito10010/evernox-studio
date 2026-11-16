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
 * 商城特惠「期」：一期 = 周四 05:00 ~ 下周三 23:59:59
 *
 * 同一期的任意一天导入，「活动.开始」都相同，因此用 start_time 作为期唯一键。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_mall_period")
public class HyolMallPeriod {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 游戏活动ID */
    private Long activityId;

    /** 商店ID */
    private Long shopId;

    /** 活动名（保持原始名，不做归类改写） */
    private String name;

    /** 本期开始（周四 05:00） */
    private LocalDateTime startTime;

    /** 本期结束（下周三 23:59:59） */
    private LocalDateTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
