package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话题帖子-图片关联实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("topic_post_image")
public class TopicPostImage {

    /** 帖子ID */
    @TableId(value = "post_id")
    private Long postId;

    /** 图片ID */
    private Long imageId;

    /** 排序权重，越小越前 */
    private Integer sort;

    private LocalDateTime createdAt;
}
