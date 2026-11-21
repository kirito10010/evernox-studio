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
 * 战场的召唤阵营：波风之力 / 宇智波之力。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_battle_camp")
public class HyolBattleCamp {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属期 */
    private Long periodId;

    /** 阵营名称（同时作为该阵营的货币名） */
    private String name;

    /** 代表忍者 */
    private String representative;

    /** 展示顺序 */
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
