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
 * 火影忍者OL技能实体（抓取缓存，skill_id 唯一键做增量，无逻辑删除）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_skill")
public class HyolSkill {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 官网技能ID iSkillId */
    private String skillId;

    /** 技能名 szTitle */
    private String title;

    /** 所属忍者 szName */
    private String name;

    /** 类型 szType（追打/普攻/被动/奥义/触发） */
    private String type;

    /** 是否瞬发 iMoment（"1"=瞬发，"0"=非瞬发） */
    private String moment;

    /** 技能描述 szDesc */
    private String description;

    /** 伤害类型 szHurtType */
    private String hurtType;

    /** 追打条件 szChaseStatus */
    private String chaseStatus;

    /** 造成状态 szHurtStatus */
    private String hurtStatus;

    /** 稀有度 szRare */
    private String rare;

    /** 本地技能图标路径 */
    private String iconUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
