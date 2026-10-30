package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 项目版本发布。
 *
 * tag 只在项目内唯一；is_latest 每个项目最多一条为 1。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("project_release")
public class ProjectRelease {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /** 版本标签，如 v1.7.0 */
    private String tag;

    private String title;

    /** 发布说明（纯文本） */
    private String notes;

    /** 0草稿/1已发布 */
    private Integer status;

    /** 0否/1是 */
    private Integer isLatest;

    private LocalDateTime publishedAt;

    /** 完整下载次数（只统计非 blockmap/yml 附件） */
    private Long downloadCount;

    private Long createdBy;


    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
