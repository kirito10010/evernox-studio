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
 * 版本发布项目。
 *
 * slug 是公开更新地址的一部分（/release/public/{slug}），创建后不可修改。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("release_project")
public class ReleaseProject {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属管理员用户ID */
    private Long ownerId;

    private String name;


    /** 公开地址标识，如 naruto-launcher */
    private String slug;

    private String description;

    /** 排序，越大越靠前 */
    private Integer sort;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
