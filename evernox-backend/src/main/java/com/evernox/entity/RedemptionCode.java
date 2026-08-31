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
 * 超级会员卡密
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("redemption_code")
public class RedemptionCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 卡密 */
    private String code;

    /** 时长天数: 7或30 */
    private Integer days;

    /** 状态: 0未使用/1已使用 */
    private Integer status;

    /** 使用账户ID */
    private Long usedBy;

    /** 使用时间 */
    private LocalDateTime usedAt;

    /** 生成管理员ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
