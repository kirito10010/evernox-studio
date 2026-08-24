package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 话题圈关注关系实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("topic_circle_member")
public class TopicCircleMember {

    /** 圈子ID */
    @TableId(value = "circle_id")
    private Long circleId;

    /** 用户ID */
    private Long userId;

    private LocalDateTime createdAt;
}
