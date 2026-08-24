package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 相册实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("album")
public class Album {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 创建者用户ID */
    private Long userId;

    /** 相册名称 */
    private String name;

    /** 相册描述 */
    private String description;

    /** 封面图片ID */
    private Long coverImageId;

    /** 0私密/1公开 */
    private Integer visibility;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
