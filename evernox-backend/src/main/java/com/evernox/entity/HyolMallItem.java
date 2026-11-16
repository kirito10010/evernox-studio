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

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商城特惠每日商品。
 *
 * 每日数据是当天的全量快照，导入时按 (period_id, day_date) 先删后插。
 * 「已购 / 剩余」是抓取者本人的账号状态，不入库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("hyol_mall_item")
public class HyolMallItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属期 hyol_mall_period.id */
    private Long periodId;

    /** 归属日（当日窗口开始日期） */
    private LocalDate dayDate;

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

    /** 折扣价 */
    private Integer price;

    /** 货币 */
    private String currency;

    /** 每日限购 */
    private Integer dailyLimit;

    /** 当日窗口开始 */
    private LocalDateTime startTime;

    /** 当日窗口结束 */
    private LocalDateTime endTime;

    /** 图标文件名，如 20400446.png */
    private String image;

    /** 详情类型：ninja=忍者技能 / drops=礼包掉落；普通道具为 null */
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
