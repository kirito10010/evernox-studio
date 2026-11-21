package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 战场的召唤商品响应。
 *
 * <p>详情结构与商城特惠一致，复用 {@link HyolMallDetailResponse}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolBattleItemResponse {

    private Long id;
    private Long periodId;
    private String camp;
    private Integer slot;
    private Long itemId;
    private String name;
    private Integer quantity;
    private String description;
    private Long iconId;
    private Integer price;
    private String priceText;
    private String currency;
    private Integer limitCount;
    private String image;
    private HyolMallDetailResponse detail;
}
