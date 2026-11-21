package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 彩旗献礼商品响应。
 *
 * <p>详情结构与商城特惠一致，直接复用 {@link HyolMallDetailResponse}，前端浮窗零改动。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolFlagItemResponse {

    private Long id;
    private Long periodId;
    private String category;
    private Integer slot;
    private Long itemId;
    private String name;
    private String description;
    private Long iconId;
    private Integer price;
    private String currency;
    private Integer dailyLimit;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String image;
    private HyolMallDetailResponse detail;

    /** 价格选项（元宝/点券）；旧格式数据为空数组 */
    private List<HyolPriceOptionResponse> priceOptions;
}
