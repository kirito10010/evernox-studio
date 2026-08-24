package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记-插图关联实体（复合主键，风格对齐 SiteLinkTag）
 *
 * 显式记录引用关系：删除笔记时据此清理插图，审批通过时据此批量改可见性。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("note_image")
public class NoteImage {

    /** 笔记ID */
    @TableId(value = "note_id")
    private Long noteId;

    /** 图片ID */
    private Long imageId;

    private LocalDateTime createdAt;
}
