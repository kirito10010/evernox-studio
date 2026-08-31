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
 * 组织成员实体（火影忍者OL 组织积分）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("org_member")
public class OrgMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属组织ID */
    private Long organizationId;

    /** 玩家名 */
    private String name;

    /** 职务 */
    private String position;

    /** 成员状态: 1在组织/0已离开 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
