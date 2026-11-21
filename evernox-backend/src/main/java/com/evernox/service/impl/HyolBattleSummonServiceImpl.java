package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolBattleCampResponse;
import com.evernox.dto.HyolBattleImportResponse;
import com.evernox.dto.HyolBattleItemResponse;
import com.evernox.dto.HyolBattlePeriodResponse;
import com.evernox.entity.HyolBattleCamp;
import com.evernox.entity.HyolBattleItem;
import com.evernox.entity.HyolBattlePeriod;
import com.evernox.exception.BusinessException;
import com.evernox.repository.HyolBattleCampRepository;
import com.evernox.repository.HyolBattleItemRepository;
import com.evernox.repository.HyolBattlePeriodRepository;
import com.evernox.service.HyolBattleSummonService;
import com.evernox.service.HyolShopItemParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 火影忍者OL 活动详情 · 战场的召唤实现。
 *
 * <p>与彩旗献礼的区别：导出没有开始时间，期身份用「活动.结束」；
 * 商品按两个阵营（波风之力 / 宇智波之力）分组，货币就是阵营贡献材料。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyolBattleSummonServiceImpl implements HyolBattleSummonService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final int MAX_NAME = 128;
    private static final int MAX_DESCRIPTION = 512;
    private static final int MAX_CAMP = 64;
    private static final int MAX_PRICE_TEXT = 64;
    private static final int MAX_CURRENCY = 32;

    private final HyolBattlePeriodRepository periodRepository;
    private final HyolBattleCampRepository campRepository;
    private final HyolBattleItemRepository itemRepository;
    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;
    private final HyolShopItemParser itemParser;

    // ==================== 查询 ====================

    @Override
    public List<HyolBattlePeriodResponse> periods() {
        List<HyolBattlePeriod> rows = periodRepository.selectList(
                new QueryWrapper<HyolBattlePeriod>().orderByDesc("end_time"));
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> periodIds = new ArrayList<>();
        for (HyolBattlePeriod row : rows) {
            periodIds.add(row.getId());
        }

        List<HyolBattleCamp> camps = campRepository.selectList(
                new QueryWrapper<HyolBattleCamp>().in("period_id", periodIds).orderByAsc("sort_order"));
        Map<Long, List<HyolBattleCampResponse>> campsByPeriod = new LinkedHashMap<>();
        for (HyolBattleCamp camp : camps) {
            if (camp.getPeriodId() == null) {
                continue;
            }
            campsByPeriod.computeIfAbsent(camp.getPeriodId(), k -> new ArrayList<>())
                    .add(HyolBattleCampResponse.builder()
                            .name(camp.getName())
                            .representative(camp.getRepresentative())
                            .build());
        }

        List<HyolBattleItem> items = itemRepository.selectList(
                new QueryWrapper<HyolBattleItem>().in("period_id", periodIds));
        Map<Long, Integer> counters = new HashMap<>();
        for (HyolBattleItem item : items) {
            if (item.getPeriodId() == null) {
                continue;
            }
            Integer current = counters.get(item.getPeriodId());
            counters.put(item.getPeriodId(), current == null ? 1 : current + 1);
        }

        List<HyolBattlePeriodResponse> result = new ArrayList<>();
        for (HyolBattlePeriod row : rows) {
            result.add(toPeriodResponse(row,
                    counters.getOrDefault(row.getId(), 0),
                    campsByPeriod.getOrDefault(row.getId(), List.of())));
        }
        return result;
    }

    @Override
    public List<HyolBattleItemResponse> items(Long periodId) {
        if (periodId == null) {
            throw new BusinessException("缺少期 ID");
        }
        List<HyolBattleItem> rows = itemRepository.selectList(new QueryWrapper<HyolBattleItem>()
                .eq("period_id", periodId)
                .orderByAsc("camp")
                .orderByAsc("slot")
                .orderByAsc("item_id"));
        List<HyolBattleItemResponse> result = new ArrayList<>();
        for (HyolBattleItem row : rows) {
            result.add(toItemResponse(row));
        }
        return result;
    }

    // ==================== 删除 ====================

    @Override
    @Transactional
    public void deletePeriod(Long periodId) {
        if (periodId == null) {
            throw new BusinessException("缺少期 ID");
        }
        if (periodRepository.selectById(periodId) == null) {
            throw new BusinessException("该期不存在，可能已被删除");
        }
        itemRepository.delete(new QueryWrapper<HyolBattleItem>().eq("period_id", periodId));
        campRepository.delete(new QueryWrapper<HyolBattleCamp>().eq("period_id", periodId));
        periodRepository.deleteById(periodId);
        log.info("战场的召唤删除整期：periodId={}", periodId);
    }

    // ==================== 导入 ====================

    @Override
    @Transactional
    public HyolBattleImportResponse importFolder(MultipartFile manifest, List<MultipartFile> images) {
        if (manifest == null || manifest.isEmpty()) {
            throw new BusinessException("请选择包含「战场的召唤.json」的文件夹");
        }
        String manifestName = HyolShopItemParser.fileNameOf(manifest.getOriginalFilename())
                .toLowerCase(Locale.ROOT);
        if (!manifestName.endsWith(".json")) {
            throw new BusinessException("请选择「战场的召唤.json」文件");
        }

        JsonNode root = readJson(manifest);
        JsonNode activity = root.path("活动");
        LocalDateTime periodEnd = HyolShopItemParser.parseTime(activity.path("结束").asText());
        if (periodEnd == null) {
            throw new BusinessException("JSON 里缺少「活动.结束」，无法判断属于哪一期");
        }

        List<HyolBattleCamp> camps = new ArrayList<>();
        List<HyolBattleItem> parsedItems = new ArrayList<>();
        List<JsonNode> itemNodes = new ArrayList<>();
        int sortOrder = 0;
        for (JsonNode campNode : root.path("阵营")) {
            String campName = cut(safeTrim(campNode.path("名称").asText()), MAX_CAMP);
            if (campName.isEmpty()) {
                continue;
            }
            camps.add(HyolBattleCamp.builder()
                    .name(campName)
                    .representative(cut(safeTrim(campNode.path("代表忍者").asText()), MAX_CAMP))
                    .sortOrder(sortOrder++)
                    .build());
            for (JsonNode node : campNode.path("商品")) {
                itemNodes.add(node);
                HyolBattleItem item = parseItem(node, campName);
                if (item != null) {
                    parsedItems.add(item);
                }
            }
        }
        if (camps.isEmpty()) {
            throw new BusinessException("JSON 里没有可识别的阵营");
        }
        if (parsedItems.isEmpty()) {
            throw new BusinessException("JSON 里没有可识别的商品");
        }

        HyolBattlePeriod existing = periodRepository.selectOne(
                new QueryWrapper<HyolBattlePeriod>().eq("end_time", periodEnd).last("limit 1"));
        HyolBattlePeriod period;
        boolean created;
        if (existing == null) {
            period = HyolBattlePeriod.builder()
                    .activityId(activity.path("活动ID").asLong(0))
                    .name(defaultIfBlank(activity.path("活动名称").asText(), "战场的召唤"))
                    .endTime(periodEnd)
                    .build();
            periodRepository.insert(period);
            created = true;
        } else {
            existing.setActivityId(activity.path("活动ID").asLong(0));
            existing.setName(defaultIfBlank(activity.path("活动名称").asText(), "战场的召唤"));
            periodRepository.updateById(existing);
            period = existing;
            created = false;
        }

        Set<String> referenced = itemParser.collectReferencedImages(itemNodes);
        int imageCount = itemParser.storeImages(
                images, referenced, storageConfig.getBattleImagePath(), "战场的召唤");
        int missingImages = 0;
        for (String name : referenced) {
            if (!Files.exists(storageConfig.getBattleImagePath().resolve(name))) {
                missingImages++;
            }
        }

        // 整期先删后插：重复导入天然幂等
        itemRepository.delete(new QueryWrapper<HyolBattleItem>().eq("period_id", period.getId()));
        campRepository.delete(new QueryWrapper<HyolBattleCamp>().eq("period_id", period.getId()));
        for (HyolBattleCamp camp : camps) {
            camp.setPeriodId(period.getId());
            campRepository.insert(camp);
        }
        for (HyolBattleItem item : parsedItems) {
            item.setPeriodId(period.getId());
            itemRepository.insert(item);
        }

        String periodLabel = "截止 " + periodEnd.toLocalDate().format(DATE);
        log.info("战场的召唤导入完成：{} 阵营 {} 个、商品 {} 件、图片 {} 张、缺图 {} 张",
                periodLabel, camps.size(), parsedItems.size(), imageCount, missingImages);

        return HyolBattleImportResponse.builder()
                .periodId(period.getId())
                .periodLabel(periodLabel)
                .itemCount(parsedItems.size())
                .campCount(camps.size())
                .imageCount(imageCount)
                .missingImages(missingImages)
                .created(created)
                .build();
    }

    private HyolBattleItem parseItem(JsonNode node, String camp) {
        long itemId = node.path("物品ID").asLong(0);
        if (itemId <= 0) {
            return null;
        }
        Detail detail = classifyDetail(node);
        return HyolBattleItem.builder()
                .camp(camp)
                .slot(node.path("槽位").asInt(0))
                .itemId(itemId)
                .name(cut(safeTrim(node.path("名称").asText()), MAX_NAME))
                .quantity(node.path("数量").asInt(1))
                .description(cut(safeTrim(node.path("说明").asText()), MAX_DESCRIPTION))
                .iconId(node.path("图标ID").asLong(0))
                .price(node.path("价格").asInt(0))
                .priceText(cut(safeTrim(node.path("价格文本").asText()), MAX_PRICE_TEXT))
                .currency(cut(defaultIfBlank(node.path("货币").asText(), camp), MAX_CURRENCY))
                .limitCount(node.path("限购").asInt(0))
                .image(baseName(node.path("图片").asText()))
                .detailType(detail.type)
                .ninjaId(detail.ninjaId)
                .detailJson(detail.json)
                .build();
    }

    /** 详情分类：与商城/彩旗保持一致，优先级 忍者 > 掉落 > 通灵兽 > 普通 */
    private Detail classifyDetail(JsonNode node) {
        JsonNode ninjaNode = node.path("忍者");
        if (ninjaNode.isObject()) {
            long ninjaIdValue = ninjaNode.path("忍者ID").asLong(0);
            return new Detail("ninja", ninjaIdValue > 0 ? ninjaIdValue : null, ninjaNode.toString());
        }
        JsonNode dropsNode = node.path("掉落");
        if (dropsNode.isObject()) {
            return new Detail("drops", null, dropsNode.toString());
        }
        if (node.hasNonNull("需求碎片")) {
            ObjectNode beast = objectMapper.createObjectNode();
            beast.put("需求碎片", node.path("需求碎片").asInt(0));
            beast.put("说明", safeTrim(node.path("说明").asText()));
            beast.put("说明标记", safeTrim(node.path("说明标记").asText()));
            JsonNode highlights = node.path("高亮");
            if (highlights.isArray()) {
                beast.set("高亮", highlights);
            }
            JsonNode bonus = node.path("属性加成");
            if (bonus.isObject()) {
                beast.set("属性加成", bonus);
            }
            return new Detail("beast", null, beast.toString());
        }
        return new Detail("item", null, null);
    }

    private JsonNode readJson(MultipartFile manifest) {
        try {
            String text = new String(manifest.getBytes(), StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                text = text.substring(1);
            }
            JsonNode root = objectMapper.readTree(text);
            if (root == null || root.isNull()) {
                throw new BusinessException("JSON 内容为空");
            }
            return root;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new BusinessException("无法读取 JSON 文件：" + e.getMessage());
        }
    }

    // ==================== 组装 ====================

    private HyolBattlePeriodResponse toPeriodResponse(HyolBattlePeriod row, int itemCount,
                                                      List<HyolBattleCampResponse> camps) {
        LocalDate end = row.getEndTime().toLocalDate();
        return HyolBattlePeriodResponse.builder()
                .id(row.getId())
                .activityId(row.getActivityId())
                .name(row.getName())
                .endTime(row.getEndTime())
                .label("截止 " + end.format(DATE))
                .itemCount(itemCount)
                .camps(camps)
                .build();
    }

    private HyolBattleItemResponse toItemResponse(HyolBattleItem row) {
        return HyolBattleItemResponse.builder()
                .id(row.getId())
                .periodId(row.getPeriodId())
                .camp(row.getCamp())
                .slot(row.getSlot())
                .itemId(row.getItemId())
                .name(row.getName())
                .quantity(row.getQuantity())
                .description(row.getDescription())
                .iconId(row.getIconId())
                .price(row.getPrice())
                .priceText(row.getPriceText())
                .currency(row.getCurrency())
                .limitCount(row.getLimitCount())
                .image(row.getImage())
                .detail(itemParser.toDetail(row.getDetailType(), row.getNinjaId(),
                        row.getDetailJson(), row.getDescription(), row.getName()))
                .build();
    }

    private record Detail(String type, Long ninjaId, String json) {
    }

    // ==================== 小工具 ====================

    private static String baseName(String raw) {
        String value = safeTrim(raw).replace('\\', '/');
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        return name.contains("..") ? "" : name;
    }

    private static String safeTrim(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private static String defaultIfBlank(String raw, String fallback) {
        String value = safeTrim(raw);
        return value.isEmpty() ? fallback : value;
    }

    private static String cut(String raw, int max) {
        String value = safeTrim(raw);
        return value.length() <= max ? value : value.substring(0, max);
    }
}
