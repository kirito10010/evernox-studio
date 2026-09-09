package com.evernox.service;

import com.evernox.dto.CityItem;
import com.evernox.dto.EarthquakeItem;
import com.evernox.dto.WeatherItem;

import java.util.List;

/**
 * 生活服务（地震、天气等）
 */
public interface UtilityService {

    /** 获取最新地震速报列表 */
    List<EarthquakeItem> listEarthquakes();

    /** 获取多个常用城市的当前天气 */
    List<WeatherItem> listWeather();

    /** 按关键词搜索城市（GeoAPI） */
    List<CityItem> searchCities(String keyword);

    /** 获取指定 LocationID 的当前天气 */
    WeatherItem getWeather(String locationId, String cityName);
}
