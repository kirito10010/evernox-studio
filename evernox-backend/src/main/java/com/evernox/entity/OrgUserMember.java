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
 * 组织成员关系（平台用户 ↔ 组织）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("org_user_member")
public class OrgUserMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 组织ID */
    private Long organizationId;

    /** 平台用户ID */
    private Long userId;

    /** 状态: 0待审批/1已加入/2已拒绝 */
    private Integer status;

    /** 申请时间 */
    private LocalDateTime appliedAt;

    /** 审批时间 */
    private LocalDateTime reviewedAt;

    /** 审批管理员ID */
    private Long reviewedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
