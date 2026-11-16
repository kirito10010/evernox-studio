package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商城特惠礼包掉落的一项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolMallDropResponse {

    private String name;

    private Integer count;

    private Long itemId;
}
