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
 * 用户积分流水
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_points_log")
public class UserPointsLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 变动值（正加负减） */
    private Integer amount;

    /** 变动后余额 */
    private Integer balance;

    /** 类型: signin/recharge */
    private String type;

    /** 说明 */
    private String description;

    /** 操作人（充值时为管理员 id） */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
