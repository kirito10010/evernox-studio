package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 公告已读记录实体（每用户每公告一条）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("announcement_read")
public class AnnouncementRead {

    /** 公告ID */
    @TableId(value = "announcement_id")
    private Long announcementId;

    /** 用户ID */
    private Long userId;

    /** 阅读时间 */
    private LocalDateTime readAt;
}
