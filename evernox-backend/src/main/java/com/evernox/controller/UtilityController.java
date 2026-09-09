package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.CityItem;
import com.evernox.dto.EarthquakeItem;
import com.evernox.dto.WeatherItem;
import com.evernox.service.UtilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生活服务接口（地震、天气等）
 */
@RestController
@RequestMapping("/utility")
@RequiredArgsConstructor
public class UtilityController {

    private final UtilityService utilityService;

    @GetMapping("/earthquake/list")
    public Result<List<EarthquakeItem>> listEarthquakes() {
        return Result.success(utilityService.listEarthquakes());
    }

    @GetMapping("/weather/list")
    public Result<List<WeatherItem>> listWeather() {
        return Result.success(utilityService.listWeather());
    }

    @GetMapping("/weather/search")
    public Result<List<CityItem>> searchCities(@RequestParam String keyword) {
        return Result.success(utilityService.searchCities(keyword));
    }

    @GetMapping("/weather/now")
    public Result<WeatherItem> getWeather(@RequestParam String location, @RequestParam String name) {
        return Result.success(utilityService.getWeather(location, name));
    }
}
