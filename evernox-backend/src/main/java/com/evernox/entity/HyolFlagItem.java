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
 * 彩旗献礼商品。
 *
 * <p>字段与商城特惠商品基本一致，区别是多一个「类别」、没有归属日；
 * 商品自身的开始/结束按导出数据原样保存（首周商品会早于整期结束）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_flag_item")
public class HyolFlagItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属期 hyol_flag_period.id */
    private Long periodId;

    /** 类别：忍者碎片 / 通灵兽碎片 / 礼包 / 技能书 / 材料 / 勾玉/符石 */
    private String category;

    /** 游戏内槽位 */
    private Integer slot;

    /** 物品ID */
    private Long itemId;

    /** 物品名称 */
    private String name;

    /** 物品说明 */
    private String description;

    /** 图标ID */
    private Long iconId;

    /** 价格（第一个价格选项的数值，用于排序/兜底展示） */
    private Integer price;

    /** 货币（原始文本，可能为「元宝/点券」） */
    private String currency;

    /** 价格选项 JSON：[{currency,price,icon}]；旧格式数据为 null */
    private String priceOptions;

    /** 每日限购 */
    private Integer dailyLimit;

    /** 商品自身开始 */
    private LocalDateTime startTime;

    /** 商品自身结束 */
    private LocalDateTime endTime;

    /** 图标文件名，如 20400507.png */
    private String image;

    /** 详情类型：ninja=忍者技能 / drops=礼包掉落 / beast=通灵兽 / item=普通材料 */
    private String detailType;

    /** 忍者ID（detailType=ninja 时有值） */
    private Long ninjaId;

    /** 详情原始 JSON（保持导出结构，不改写字段名） */
    private String detailJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
