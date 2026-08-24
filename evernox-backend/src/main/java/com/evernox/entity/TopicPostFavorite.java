package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话题帖子收藏实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("topic_post_favorite")
public class TopicPostFavorite {

    /** 帖子ID */
    @TableId(value = "post_id")
    private Long postId;

    /** 用户ID */
    private Long userId;

    private LocalDateTime createdAt;
}
