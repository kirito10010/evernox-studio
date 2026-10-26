package com.evernox.service.impl;

import cn.hutool.http.HttpRequest;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.evernox.dto.CityItem;
import com.evernox.dto.EarthquakeItem;
import com.evernox.dto.WeatherItem;
import com.evernox.service.UtilityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 生活服务实现
 */
@Slf4j
@Service
public class UtilityServiceImpl implements UtilityService {

    /**
     * 国家地震科学数据中心原页面已下线（站点维护），改用 Wolfx 转发的中国地震台网速报列表。
     * 数据字段与 CENC 一致：time / location / magnitude / depth / latitude / longitude。
     */
    private static final String EARTHQUAKE_URL = "https://api.wolfx.jp/cenc_eqlist.json";

    /** 单次最多返回多少条速报 */
    private static final int EARTHQUAKE_LIMIT = 50;

    /** 和风天气 API Host（个人专属） */
    @Value("${evernox.qweather.host:}")
    private String qweatherHost;

    /** 和风天气 API Key */
    @Value("${evernox.qweather.key:}")
    private String qweatherKey;

    /** 常用城市：城市名 -> QWeather LocationID */
    private static final Map<String, String> CITIES = Map.ofEntries(
            Map.entry("北京", "101010100"),
            Map.entry("上海", "101020100"),
            Map.entry("广州", "101280101"),
            Map.entry("深圳", "101280601"),
            Map.entry("成都", "101270101"),
            Map.entry("杭州", "101210101"),
            Map.entry("武汉", "101200101"),
            Map.entry("西安", "101110101"),
            Map.entry("重庆", "101040100"),
            Map.entry("南京", "101190101"),
            Map.entry("天津", "101030100"),
            Map.entry("青岛", "101120201"));

    @Override
    public List<EarthquakeItem> listEarthquakes() {
        List<EarthquakeItem> result = new ArrayList<>();
        try {
            String body = HttpRequest.get(EARTHQUAKE_URL)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(8000)
                    .execute()
                    .body();
            JSONObject json = JSONUtil.parseObj(body);
            for (Object value : json.values()) {
                if (!(value instanceof JSONObject item)) {
                    continue;
                }
                String magnitude = item.getStr("magnitude");
                String location = item.getStr("location");
                if (location == null || location.isBlank()) {
                    location = item.getStr("placeName");
                }
                result.add(EarthquakeItem.builder()
                        .time(item.getStr("time"))
                        .longitude(item.getStr("longitude"))
                        .latitude(item.getStr("latitude"))
                        .depth(item.getStr("depth"))
                        .magnitude(magnitude)
                        .location(location)
                        .type(typeText(item.getStr("type")))
                        .build());
            }
            // 时间字符串是 yyyy-MM-dd HH:mm:ss，可直接按字符串倒序
            result.sort(Comparator.comparing((EarthquakeItem item) -> item.getTime(),
                    Comparator.nullsLast(Comparator.reverseOrder())));
            if (result.size() > EARTHQUAKE_LIMIT) {
                result = new ArrayList<>(result.subList(0, EARTHQUAKE_LIMIT));
            }
        } catch (Exception e) {
            log.warn("获取地震速报数据失败: {}", e.getMessage());
        }
        return result;
    }

    /** reviewed=正式测定，automatic=自动测定 */
    private String typeText(String type) {
        if (type == null) {
            return null;
        }
        return switch (type.toLowerCase()) {
            case "reviewed" -> "正式测定";
            case "automatic" -> "自动测定";
            default -> type;
        };
    }

    @Override
    public List<WeatherItem> listWeather() {
        List<WeatherItem> result = new ArrayList<>();
        if (qweatherHost.isBlank() || qweatherKey.isBlank()) {
            log.warn("和风天气 API Host/Key 未配置");
            return result;
        }
        for (Map.Entry<String, String> entry : CITIES.entrySet()) {
            try {
                String url = "https://" + qweatherHost + "/v7/weather/now?location=" + entry.getValue();
                String body = HttpRequest.get(url)
                        .header("X-QW-Api-Key", qweatherKey)
                        .timeout(8000)
                        .execute()
                        .body();
                JSONObject json = JSONUtil.parseObj(body);
                if (!"200".equals(json.getStr("code"))) {
                    continue;
                }
                JSONObject now = json.getJSONObject("now");
                if (now == null) {
                    continue;
                }
                result.add(WeatherItem.builder()
                        .city(entry.getKey())
                        .temp(now.getStr("temp"))
                        .feelsLike(now.getStr("feelsLike"))
                        .text(now.getStr("text"))
                        .windDir(now.getStr("windDir"))
                        .windScale(now.getStr("windScale"))
                        .humidity(now.getStr("humidity"))
                        .pressure(now.getStr("pressure"))
                        .build());
            } catch (Exception e) {
                log.warn("获取城市天气失败: city={}, err={}", entry.getKey(), e.getMessage());
            }
        }
        return result;
    }

    @Override
    public List<CityItem> searchCities(String keyword) {
        List<CityItem> result = new ArrayList<>();
        if (qweatherHost.isBlank() || qweatherKey.isBlank() || keyword == null || keyword.isBlank()) {
            return result;
        }
        try {
            String url = "https://" + qweatherHost + "/geo/v2/city/lookup?location="
                    + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&range=cn&number=10";
            String body = HttpRequest.get(url)
                    .header("X-QW-Api-Key", qweatherKey)
                    .timeout(8000)
                    .execute()
                    .body();
            JSONObject json = JSONUtil.parseObj(body);
            if (!"200".equals(json.getStr("code"))) {
                return result;
            }
            JSONArray locations = json.getJSONArray("location");
            if (locations == null) {
                return result;
            }
            for (int i = 0; i < locations.size(); i++) {
                JSONObject loc = locations.getJSONObject(i);
                result.add(CityItem.builder()
                        .name(loc.getStr("name"))
                        .id(loc.getStr("id"))
                        .adm1(loc.getStr("adm1"))
                        .adm2(loc.getStr("adm2"))
                        .build());
            }
        } catch (Exception e) {
            log.warn("搜索城市失败: keyword={}, err={}", keyword, e.getMessage());
        }
        return result;
    }

    @Override
    public WeatherItem getWeather(String locationId, String cityName) {
        if (qweatherHost.isBlank() || qweatherKey.isBlank() || locationId == null || locationId.isBlank()) {
            return null;
        }
        try {
            String url = "https://" + qweatherHost + "/v7/weather/now?location=" + locationId;
            String body = HttpRequest.get(url)
                    .header("X-QW-Api-Key", qweatherKey)
                    .timeout(8000)
                    .execute()
                    .body();
            JSONObject json = JSONUtil.parseObj(body);
            if (!"200".equals(json.getStr("code"))) {
                return null;
            }
            JSONObject now = json.getJSONObject("now");
            if (now == null) {
                return null;
            }
            return WeatherItem.builder()
                    .city(cityName)
                    .temp(now.getStr("temp"))
                    .feelsLike(now.getStr("feelsLike"))
                    .text(now.getStr("text"))
                    .windDir(now.getStr("windDir"))
                    .windScale(now.getStr("windScale"))
                    .humidity(now.getStr("humidity"))
                    .pressure(now.getStr("pressure"))
                    .build();
        } catch (Exception e) {
            log.warn("获取城市天气失败: location={}, err={}", locationId, e.getMessage());
        }
        return null;
    }
}
