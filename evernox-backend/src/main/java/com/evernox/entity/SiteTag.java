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
 * 站点标签实体
 *
 * 纯字典数据，仅管理员维护；删除走物理删除（先清理 site_link_tag 关联），
 * 因为 name 上有唯一索引，逻辑删除会导致同名标签无法重建。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("site_tag")
public class SiteTag {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名 */
    private String name;

    /** 排序权重，越小越前 */
    private Integer sort;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
