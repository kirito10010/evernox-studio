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
 * 战场的召唤商品：整期一份快照；同一物品可能在两个阵营各出现一次。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_battle_item")
public class HyolBattleItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属期 */
    private Long periodId;

    /** 阵营名称 */
    private String camp;

    /** 游戏内槽位 */
    private Integer slot;

    /** 物品ID */
    private Long itemId;

    /** 物品名称 */
    private String name;

    /** 兑换一次获得数量 */
    private Integer quantity;

    /** 物品说明 */
    private String description;

    /** 图标ID */
    private Long iconId;

    /** 价格（阵营贡献材料） */
    private Integer price;

    /** 价格原文，如「80 波风之力」 */
    private String priceText;

    /** 货币（阵营名） */
    private String currency;

    /** 限购次数 */
    private Integer limitCount;

    /** 图标文件名 */
    private String image;

    /** 详情类型：ninja/drops/beast/item */
    private String detailType;

    /** 忍者ID（detailType=ninja 时有值） */
    private Long ninjaId;

    /** 详情原始 JSON */
    private String detailJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
