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
 * 应援板画板
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("support_board")
public class SupportBoard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer width;

    private Integer height;

    /** 是否当前展示 0/1 */
    private Integer active;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
