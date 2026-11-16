package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 商城特惠商品
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolMallItemResponse {

    private Long id;

    private Long periodId;

    private LocalDate dayDate;

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

    /** 图标文件名；前端拼 /api/hyol/mall/image/{image} */
    private String image;

    /** 详情（忍者技能 / 礼包掉落），普通道具为 null */
    private HyolMallDetailResponse detail;
}
