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
 * 记事本实体
 *
 * 与网站分享一致：公开与否只由 status 决定，不另设 visibility。
 * 正文里的插图只以 image.id 出现（见 note_image 表），content 中不含任何可直接访问的 URL。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("note")
public class Note {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者用户ID */
    private Long userId;

    /** 标题 */
    private String title;

    /** 正文HTML，入库前已按白名单消毒 */
    private String content;

    /** 纯文本摘要，列表页展示用 */
    private String summary;

    /** 是否置顶: 0否/1是 */
    private Integer pinned;

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
