package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 彩旗献礼导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolFlagImportResponse {

    private Long periodId;

    /** 2026-11-19 ~ 2026-12-03 */
    private String periodLabel;

    private int itemCount;
    private int imageCount;
    private int missingImages;

    /** true=新建期，false=更新已存在的期（重复导入） */
    private boolean created;
}
