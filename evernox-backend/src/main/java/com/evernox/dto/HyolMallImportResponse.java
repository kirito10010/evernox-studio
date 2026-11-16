package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商城特惠导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolMallImportResponse {

    private Long periodId;

    /** 2026-11-05 ~ 2026-11-11 */
    private String periodLabel;

    /** 导入归属日 yyyy-MM-dd */
    private String day;

    /** 归属日星期文案 */
    private String weekday;

    /** 本次导入的商品数 */
    private Integer itemCount;

    /** 成功落盘的图片数 */
    private Integer imageCount;

    /** 数据引用了但没拿到文件的图片数 */
    private Integer missingImages;

    /** true = 新建了这一期；false = 复用已有期 */
    private Boolean created;
}
