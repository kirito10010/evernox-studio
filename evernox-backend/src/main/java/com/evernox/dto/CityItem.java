package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 城市搜索结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityItem {

    /** 城市名 */
    private String name;

    /** LocationID */
    private String id;

    /** 省级行政区 */
    private String adm1;

    /** 市级行政区 */
    private String adm2;
}
