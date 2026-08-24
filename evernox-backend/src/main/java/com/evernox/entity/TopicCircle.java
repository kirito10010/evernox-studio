package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话题圈实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("topic_circle")
public class TopicCircle {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 圈子名称 */
    private String name;

    /** 圈子简介 */
    private String description;

    /** 创建者用户ID */
    private Long ownerId;

    /** 帖子数(冗余) */
    private Integer postCount;

    /** 成员数(冗余) */
    private Integer memberCount;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
