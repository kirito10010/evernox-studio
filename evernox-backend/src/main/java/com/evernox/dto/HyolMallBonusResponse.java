package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通灵兽碎片的「上阵忍者属性加成」（游戏内 tooltip 底部那四行）。
 *
 * 导出数据里是 {"生命":30,"攻击":15,"防御":12,"忍术":15}，这里转成英文键给前端。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolMallBonusResponse {

    private Integer life;

    private Integer attack;

    private Integer defense;

    private Integer ninjutsu;
}
