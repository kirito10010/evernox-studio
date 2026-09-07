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
 * 应援板像素点（稀疏存储，只存已绘制点）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("support_pixel")
public class SupportPixel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long boardId;

    private Integer x;

    private Integer y;

    /** 颜色 #RRGGBB */
    private String color;

    /** 最后绘制该点的用户 */
    private Long userId;

    /** 是否锁定 0/1 */
    private Integer locked;

    private LocalDateTime updatedAt;
}
