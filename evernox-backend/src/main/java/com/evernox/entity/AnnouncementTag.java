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
 * 公告标签实体（仅管理员维护，纯字典数据，物理删除）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("announcement_tag")
public class AnnouncementTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名 */
    private String name;

    /** 标签颜色(HEX，如 #409EFF) */
    private String color;

    /** 排序权重，越小越前 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
