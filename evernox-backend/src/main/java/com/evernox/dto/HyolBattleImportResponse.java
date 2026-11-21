package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 战场的召唤导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolBattleImportResponse {

    private Long periodId;

    /** 截止 2026-12-03 */
    private String periodLabel;

    private int itemCount;
    private int campCount;
    private int imageCount;
    private int missingImages;

    /** true=新建期，false=更新已存在的期 */
    private boolean created;
}
