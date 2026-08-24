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
 * 火影忍者OL官方公告实体（抓取缓存，无逻辑删除）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_announcement")
public class HyolAnnouncement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公告标题 */
    private String title;

    /** 官网详情地址 */
    private String sourceUrl;

    /** 发布时间(官网原文字符串) */
    private String publishTime;

    /** 正文HTML(消毒后) */
    private String content;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
