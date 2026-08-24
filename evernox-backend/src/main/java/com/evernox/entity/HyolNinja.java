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
 * 火影忍者OL忍者图鉴实体（抓取缓存，nid 唯一键做增量，无逻辑删除）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_ninja")
public class HyolNinja {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 官网忍者ID iNid */
    private String nid;

    /** 完整名称 szName */
    private String name;

    /** 昵称 szNickname */
    private String nickname;

    /** 属性 szAttr */
    private String attr;

    /** 星级 iStar */
    private String star;

    /** 阵营标签 szOrg */
    private String org;

    /** 定位 szPos */
    private String pos;

    /** 获得方式 szGetWay */
    private String getWay;

    /** 造成状态 szEffect */
    private String effect;

    /** 追打状态 szEffectChase */
    private String effectChase;

    /** 本地头像路径 */
    private String avatarUrl;

    /** 本地高清立绘路径 szPicUrl3 */
    private String avatarUrl3;

    /** 奥义技能ID */
    private String skillOy;

    /** 普攻技能ID */
    private String skillPg;

    /** 被动1技能ID */
    private String skillBd1;

    /** 被动2技能ID */
    private String skillBd2;

    /** 被动3技能ID */
    private String skillBd3;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
