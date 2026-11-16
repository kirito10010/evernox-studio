package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商城特惠商品详情：忍者技能（type=ninja）或礼包掉落（type=drops）。
 *
 * 库里存的是导出工具的原始 JSON，这里是读取时归一化的结果，
 * 前端只认这一套字段名。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolMallDetailResponse {

    /** ninja=忍者技能 / beast=通灵兽碎片 / drops=礼包掉落 / item=普通材料（只有说明） */
    private String type;

    /** 忍者ID（type=ninja，来自导出数据，仅作标识，不关联任何其它模块） */
    private Long ninjaId;

    /** 忍者名：商品名去掉「碎片」，如「蝎[绯流琥]」 */
    private String ninjaName;

    /** 物品说明原文（beast / drops / item 用；ninja 不返回，浮窗只显示技能） */
    private String description;

    /** 带【】标记的说明（beast / drops / item 有则返回，前端据此渲染高亮词） */
    private String marked;

    /** 招募所需碎片数（type=beast，如梦貘 80） */
    private Integer needFragments;

    /** 上阵忍者属性加成（type=beast；导出数据没带时为 null） */
    private HyolMallBonusResponse bonus;

    /** 奥义 → 普攻 → 追打 → 被动（按原序），type=ninja 时有值 */
    private List<HyolMallSkillResponse> skills;

    /** 固定掉落，type=drops 时有值 */
    private List<HyolMallDropResponse> fixedDrops;

    /** 随机掉落，type=drops 时有值 */
    private List<HyolMallDropResponse> randomDrops;
}
