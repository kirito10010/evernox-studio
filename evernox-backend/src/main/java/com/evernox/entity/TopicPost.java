package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话题帖子实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("topic_post")
public class TopicPost {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 圈子ID */
    private Long circleId;

    /** 作者用户ID */
    private Long userId;

    /** 标题 */
    private String title;

    /** 正文文本 */
    private String content;

    /** 点赞数(冗余) */
    private Integer likeCount;

    /** 评论数(冗余) */
    private Integer commentCount;

    /** 收藏数(冗余) */
    private Integer favoriteCount;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
