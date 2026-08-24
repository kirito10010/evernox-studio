package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 公告-插图关联实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("announcement_image")
public class AnnouncementImage {

    /** 公告ID */
    @TableId(value = "announcement_id")
    private Long announcementId;

    /** 图片ID(image.id) */
    private Long imageId;

    private LocalDateTime createdAt;
}
