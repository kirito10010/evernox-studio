package com.evernox.config;

import com.evernox.entity.GameCity;
import com.evernox.repository.GameCityRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 沙盘争霸城市与邻接种子
 *
 * 幂等：仅当对应表为空或残缺时才填充。cities.json / adjacency.json 由
 * 前端同源 geoJson 合并生成；邻接关系用「多边形共边」判定（共享同一条边界线段
 * 才算相邻），并对无共边邻居的孤立城市（如海岛）兜底连接到最近城市。
 *
 * 注意：使用 JdbcTemplate 批量插入，避免逐条 insert 拖慢首次启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GameDataSeeder implements ApplicationRunner {

    private static final int IMPORTANT_WEIGHT = 3;
    private static final int IMPORTANT_DEFENSE = 150;
    private static final int NORMAL_WEIGHT = 1;
    private static final int NORMAL_DEFENSE = 100;

    private final GameCityRepository cityRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        seedCities();
        seedAdjacency();
    }

    private void seedCities() {
        List<CitySeed> seeds = readCitySeeds();
        List<GameCity> existing = cityRepository.selectList(null);

        if (existing.isEmpty()) {
            // 空表：全量插入（带差异化 weight/base_defense）
            List<Object[]> batch = new ArrayList<>(seeds.size());
            for (CitySeed c : seeds) {
                int w = isImportantCity(c.adcode()) ? IMPORTANT_WEIGHT : NORMAL_WEIGHT;
                int d = isImportantCity(c.adcode()) ? IMPORTANT_DEFENSE : NORMAL_DEFENSE;
                batch.add(new Object[]{
                        c.adcode(), c.name(), c.province(),
                        c.center().get(0), c.center().get(1), w, d
                });
            }
            jdbcTemplate.batchUpdate(
                    "INSERT INTO game_city(adcode, name, province, center_lng, center_lat, weight, base_defense, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
                    batch);
            log.info("沙盘城市种子完成：{} 城", seeds.size());
            return;
        }

        // 表非空：就地按 adcode 刷新 weight/base_defense（保留主键，避免破坏 owner 引用）
        List<Object[]> updateBatch = new ArrayList<>(seeds.size());
        for (CitySeed c : seeds) {
            int w = isImportantCity(c.adcode()) ? IMPORTANT_WEIGHT : NORMAL_WEIGHT;
            int d = isImportantCity(c.adcode()) ? IMPORTANT_DEFENSE : NORMAL_DEFENSE;
            updateBatch.add(new Object[]{ w, d, c.adcode() });
        }
        jdbcTemplate.batchUpdate("UPDATE game_city SET weight = ?, base_defense = ? WHERE adcode = ?", updateBatch);

        // 补齐缺失城市（极少发生，历史残缺时）
        Set<String> existingAdcodes = existing.stream().map(city -> city.getAdcode()).collect(Collectors.toSet());
        List<Object[]> insertBatch = new ArrayList<>();
        for (CitySeed c : seeds) {
            if (existingAdcodes.contains(c.adcode())) {
                continue;
            }
            int w = isImportantCity(c.adcode()) ? IMPORTANT_WEIGHT : NORMAL_WEIGHT;
            int d = isImportantCity(c.adcode()) ? IMPORTANT_DEFENSE : NORMAL_DEFENSE;
            insertBatch.add(new Object[]{
                    c.adcode(), c.name(), c.province(),
                    c.center().get(0), c.center().get(1), w, d
            });
        }
        if (!insertBatch.isEmpty()) {
            jdbcTemplate.batchUpdate(
                    "INSERT INTO game_city(adcode, name, province, center_lng, center_lat, weight, base_defense, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
                    insertBatch);
        }
        log.info("沙盘城市差异化刷新完成：{} 城", seeds.size());
    }

    private boolean isImportantCity(String adcode) {
        if (adcode == null) {
            return false;
        }
        // 省会 / 直辖市城区
        if (adcode.endsWith("0100")) {
            return true;
        }
        // 香港 / 澳门
        return "810000".equals(adcode) || "820000".equals(adcode);
    }

    private void seedAdjacency() {
        List<GameCity> cities = cityRepository.selectList(null);
        if (cities.isEmpty()) {
            return;
        }
        Map<String, Long> adcodeToId = cities.stream()
                .collect(Collectors.toMap(c -> c.getAdcode(), c -> c.getId()));

        // 邻接为静态数据、计算快，每次启动都重建，确保与最新共边算法一致
        jdbcTemplate.update("DELETE FROM game_city_adjacency");

        List<Object[]> batch = new ArrayList<>();
        Set<Long> hasEdge = new HashSet<>();

        // 1) 共边邻接：共享同一条边界线段才视为相邻
        List<List<String>> pairs = readAdjacencyPairs();
        for (List<String> pair : pairs) {
            Long a = adcodeToId.get(pair.get(0));
            Long b = adcodeToId.get(pair.get(1));
            if (a == null || b == null) {
                continue;
            }
            batch.add(new Object[]{a, b});
            batch.add(new Object[]{b, a});
            hasEdge.add(a);
            hasEdge.add(b);
        }

        // 2) 兜底：无共边邻居的孤立城市（如海岛三沙市）连到最近城市
        for (GameCity c : cities) {
            if (hasEdge.contains(c.getId())) {
                continue;
            }
            GameCity nearest = null;
            double min = Double.MAX_VALUE;
            for (GameCity o : cities) {
                if (o.getId().equals(c.getId())) {
                    continue;
                }
                double d = haversine(c, o);
                if (d < min) {
                    min = d;
                    nearest = o;
                }
            }
            if (nearest != null) {
                batch.add(new Object[]{c.getId(), nearest.getId()});
                batch.add(new Object[]{nearest.getId(), c.getId()});
                hasEdge.add(c.getId());
                hasEdge.add(nearest.getId());
            }
        }

        jdbcTemplate.batchUpdate(
                "INSERT INTO game_city_adjacency(city_id, adjacent_city_id) VALUES (?, ?)",
                batch);
        log.info("沙盘邻接种子完成：共边 {} 对，共 {} 条有向边", pairs.size(), batch.size());
    }

    private double haversine(GameCity a, GameCity b) {
        final double R = 6371.0;
        double dLat = Math.toRadians(b.getCenterLat() - a.getCenterLat());
        double dLng = Math.toRadians(b.getCenterLng() - a.getCenterLng());
        double sinLat = Math.sin(dLat / 2);
        double sinLng = Math.sin(dLng / 2);
        double h = sinLat * sinLat
                + Math.cos(Math.toRadians(a.getCenterLat())) * Math.cos(Math.toRadians(b.getCenterLat()))
                * sinLng * sinLng;
        return R * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    private List<CitySeed> readCitySeeds() {
        try {
            return objectMapper.readValue(
                    new ClassPathResource("game/cities.json").getInputStream(),
                    new TypeReference<List<CitySeed>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("读取 game/cities.json 失败", e);
        }
    }

    private List<List<String>> readAdjacencyPairs() {
        try {
            return objectMapper.readValue(
                    new ClassPathResource("game/adjacency.json").getInputStream(),
                    new TypeReference<List<List<String>>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("读取 game/adjacency.json 失败", e);
        }
    }

    private record CitySeed(String adcode, String name, String province, List<Double> center) {
    }
}
