package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 城市实时天气
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherItem {

    /** 城市名 */
    private String city;

    /** 温度(℃) */
    private String temp;

    /** 体感温度(℃) */
    private String feelsLike;

    /** 天气现象 */
    private String text;

    /** 风向 */
    private String windDir;

    /** 风力等级 */
    private String windScale;

    /** 湿度(%) */
    private String humidity;

    /** 气压(hPa) */
    private String pressure;
}
