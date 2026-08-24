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
 * 公告实体
 *
 * 公告由管理员发布、全站用户可见。正文里的插图只以 image.id 出现
 * （见 announcement_image 表），content 中不含任何可直接访问的 URL。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("announcement")
public class Announcement {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公告标题 */
    private String title;

    /** 公告正文HTML，入库前已按白名单消毒 */
    private String content;

    /** 标签ID(announcement_tag.id, 可空) */
    private Long tagId;

    /** 发布管理员ID(user.id) */
    private Long createdBy;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
