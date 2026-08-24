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
 * 网站分享实体
 *
 * 公开与否完全由 status 决定，不另设 visibility，避免出现「已公开但未审批」的不一致组合。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("site_link")
public class SiteLink {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分享者用户ID */
    private Long userId;

    /** 网站名称 */
    private String title;

    /** 网站链接，仅允许 http/https */
    private String url;

    /** 网站详情介绍 */
    private String description;

    /** 封面图片ID，复用图床 image 表 */
    private Long coverImageId;

    /** 0私有/1待审批/2已公开/3已驳回 */
    private Integer status;

    /** 最近一次驳回原因，用户可见 */
    private String rejectReason;

    /** 最近一次提交审批时间 */
    private LocalDateTime submittedAt;

    /** 审批管理员ID */
    private Long reviewedBy;

    /** 审批时间 */
    private LocalDateTime reviewedAt;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
