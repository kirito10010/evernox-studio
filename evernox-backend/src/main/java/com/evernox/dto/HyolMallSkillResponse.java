package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 商城特惠商品详情里的一条技能。
 *
 * 奥义/普攻/追打/被动统一成这个结构，用 slot 区分；
 * 只有奥义有 chakra / cooldown / entryCooldown。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolMallSkillResponse {

    /** 奥义 / 普攻 / 追打 / 被动 */
    private String slot;

    private String name;

    /** 原文说明 */
    private String description;

    /** 带【】标记的说明，前端据此渲染高亮词 */
    private String marked;

    private List<String> highlights;

    /** 查克拉（仅奥义） */
    private Integer chakra;

    /** 冷却（仅奥义） */
    private Integer cooldown;

    /** 进场冷却（仅奥义） */
    private Integer entryCooldown;
}
