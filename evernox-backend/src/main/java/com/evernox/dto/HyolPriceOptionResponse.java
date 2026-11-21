package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品的一个价格选项（新版导出明确了「元宝 / 点券」两种货币）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolPriceOptionResponse {

    /** 货币：元宝 / 点券 */
    private String currency;

    private Integer price;

    /** 货币图标文件名，如 货币_元宝.png */
    private String icon;
}
