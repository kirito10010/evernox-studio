package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 地震速报条目
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EarthquakeItem {

    /** 发震时间（北京时间） */
    private String time;

    /** 经度(°) */
    private String longitude;

    /** 纬度(°) */
    private String latitude;

    /** 震源深度(Km) */
    private String depth;

    /** 震级(M) */
    private String magnitude;

    /** 震中位置 */
    private String location;

    /** 事件类型 */
    private String type;
}
