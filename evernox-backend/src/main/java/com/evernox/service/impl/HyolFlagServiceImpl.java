package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolFlagImportResponse;
import com.evernox.dto.HyolFlagItemResponse;
import com.evernox.dto.HyolFlagPeriodResponse;
import com.evernox.dto.HyolPriceOptionResponse;
import com.evernox.entity.HyolFlagItem;
import com.evernox.entity.HyolFlagPeriod;
import com.evernox.exception.BusinessException;
import com.evernox.repository.HyolFlagItemRepository;
import com.evernox.repository.HyolFlagPeriodRepository;
import com.evernox.service.HyolFlagService;
import com.evernox.service.HyolShopItemParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 火影忍者OL 活动详情 · 彩旗献礼实现。
 *
 * <p>与商城特惠的区别：一期一份商品快照，没有按天分隔；重复导入按整期覆盖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyolFlagServiceImpl implements HyolFlagService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final HyolFlagPeriodRepository periodRepository;
    private final HyolFlagItemRepository itemRepository;
    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;
    private final HyolShopItemParser itemParser;

    // ==================== 查询 ====================

    @Override
    public List<HyolFlagPeriodResponse> periods() {
        List<HyolFlagPeriod> rows = periodRepository.selectList(
                new QueryWrapper<HyolFlagPeriod>().orderByDesc("start_time"));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> periodIds = new ArrayList<>();
        for (HyolFlagPeriod row : rows) {
            periodIds.add(row.getId());
        }
        List<HyolFlagItem> items = itemRepository.selectList(
                new QueryWrapper<HyolFlagItem>().in("period_id", periodIds));

        Map<Long, Integer> counters = new HashMap<>();
        for (HyolFlagItem item : items) {
            if (item.getPeriodId() == null) {
                continue;
            }
            Integer current = counters.get(item.getPeriodId());
            counters.put(item.getPeriodId(), current == null ? 1 : current + 1);
        }

        List<HyolFlagPeriodResponse> result = new ArrayList<>();
        for (HyolFlagPeriod row : rows) {
            result.add(toPeriodResponse(row, counters.getOrDefault(row.getId(), 0)));
        }
        return result;
    }

    @Override
    public List<HyolFlagItemResponse> items(Long periodId) {
        if (periodId == null) {
            throw new BusinessException("缺少期 ID");
        }
        List<HyolFlagItem> rows = itemRepository.selectList(new QueryWrapper<HyolFlagItem>()
                .eq("period_id", periodId)
                .orderByAsc("slot")
                .orderByAsc("item_id"));
        List<HyolFlagItemResponse> result = new ArrayList<>();
        for (HyolFlagItem row : rows) {
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
        itemRepository.delete(new QueryWrapper<HyolFlagItem>().eq("period_id", periodId));
        periodRepository.deleteById(periodId);
        log.info("彩旗献礼删除整期：periodId={}", periodId);
    }

    // ==================== 导入 ====================

    @Override
    @Transactional
    public HyolFlagImportResponse importFolder(MultipartFile manifest, List<MultipartFile> images) {
        if (manifest == null || manifest.isEmpty()) {
            throw new BusinessException("请选择包含「彩旗献礼.json」的文件夹");
        }
        String manifestName = HyolShopItemParser.fileNameOf(manifest.getOriginalFilename())
                .toLowerCase(Locale.ROOT);
        if (!manifestName.endsWith(".json")) {
            throw new BusinessException("请选择「彩旗献礼.json」文件");
        }

        JsonNode root = readJson(manifest);
        JsonNode activity = root.path("活动");
        LocalDateTime periodStart = HyolShopItemParser.parseTime(activity.path("开始").asText());
        if (periodStart == null) {
            throw new BusinessException("JSON 里缺少「活动.开始」，无法判断属于哪一期");
        }
        LocalDateTime periodEnd = HyolShopItemParser.parseTime(activity.path("结束").asText());
        if (periodEnd == null || !periodEnd.isAfter(periodStart)) {
            periodEnd = periodStart.toLocalDate().plusDays(14).atTime(23, 59, 59);
        }

        List<HyolShopItemParser.ShopItem> shopItems =
                itemParser.parseItems(root, "价格", periodStart, periodEnd);
        if (shopItems.isEmpty()) {
            throw new BusinessException("JSON 里没有可识别的商品");
        }

        HyolFlagPeriod existing = periodRepository.selectOne(
                new QueryWrapper<HyolFlagPeriod>().eq("start_time", periodStart).last("limit 1"));
        HyolFlagPeriod period;
        boolean created;
        if (existing == null) {
            period = HyolFlagPeriod.builder()
                    .activityId(activity.path("活动ID").asLong(0))
                    .shopId(activity.path("商店ID").asLong(0))
                    .name(defaultIfBlank(activity.path("活动名称").asText(), "彩旗献礼"))
                    .startTime(periodStart)
                    .endTime(periodEnd)
                    .build();
            periodRepository.insert(period);
            created = true;
        } else {
            // 已存在的期：以最新一次导入的元信息为准
            existing.setActivityId(activity.path("活动ID").asLong(0));
            existing.setShopId(activity.path("商店ID").asLong(0));
            existing.setName(defaultIfBlank(activity.path("活动名称").asText(), "彩旗献礼"));
            existing.setEndTime(periodEnd);
            periodRepository.updateById(existing);
            period = existing;
            created = false;
        }

        Set<String> referenced = itemParser.collectReferencedImages(root);
        int imageCount = itemParser.storeImages(
                images, referenced, storageConfig.getFlagImagePath(), "彩旗献礼");
        int missingImages = 0;
        for (String name : referenced) {
            if (!Files.exists(storageConfig.getFlagImagePath().resolve(name))) {
                missingImages++;
            }
        }

        // 整期先删后插：重复导入天然幂等，以最后一次导出为准
        itemRepository.delete(new QueryWrapper<HyolFlagItem>().eq("period_id", period.getId()));
        for (HyolShopItemParser.ShopItem item : shopItems) {
            itemRepository.insert(HyolFlagItem.builder()
                    .periodId(period.getId())
                    .category(item.category())
                    .slot(item.slot())
                    .itemId(item.itemId())
                    .name(item.name())
                    .description(item.description())
                    .iconId(item.iconId())
                    .price(item.price())
                    .currency(item.currency())
                    .priceOptions(item.priceOptionsJson())
                    .dailyLimit(item.dailyLimit())
                    .startTime(item.startTime())
                    .endTime(item.endTime())
                    .image(item.image())
                    .detailType(item.detailType())
                    .ninjaId(item.ninjaId())
                    .detailJson(item.detailJson())
                    .build());
        }

        String periodLabel = periodStart.toLocalDate().format(DATE)
                + " ~ " + periodEnd.toLocalDate().format(DATE);
        log.info("彩旗献礼导入完成：期 {} 商品 {} 件，图片 {} 张，缺图 {} 张",
                periodLabel, shopItems.size(), imageCount, missingImages);

        return HyolFlagImportResponse.builder()
                .periodId(period.getId())
                .periodLabel(periodLabel)
                .itemCount(shopItems.size())
                .imageCount(imageCount)
                .missingImages(missingImages)
                .created(created)
                .build();
    }

    private JsonNode readJson(MultipartFile manifest) {
        try {
            String text = new String(manifest.getBytes(), StandardCharsets.UTF_8);
            // 部分导出工具会带 BOM，Jackson 会因此报错
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

    private HyolFlagPeriodResponse toPeriodResponse(HyolFlagPeriod row, int itemCount) {
        LocalDate start = row.getStartTime().toLocalDate();
        LocalDate end = row.getEndTime().toLocalDate();
        if (end.isBefore(start)) {
            end = start;
        }
        return HyolFlagPeriodResponse.builder()
                .id(row.getId())
                .activityId(row.getActivityId())
                .shopId(row.getShopId())
                .name(row.getName())
                .startTime(row.getStartTime())
                .endTime(row.getEndTime())
                .label(start.format(DATE) + " ~ " + end.format(DATE))
                .itemCount(itemCount)
                .build();
    }

    private HyolFlagItemResponse toItemResponse(HyolFlagItem row) {
        return HyolFlagItemResponse.builder()
                .id(row.getId())
                .periodId(row.getPeriodId())
                .category(row.getCategory())
                .slot(row.getSlot())
                .itemId(row.getItemId())
                .name(row.getName())
                .description(row.getDescription())
                .iconId(row.getIconId())
                .price(row.getPrice())
                .currency(row.getCurrency())
                .dailyLimit(row.getDailyLimit())
                .startTime(row.getStartTime())
                .endTime(row.getEndTime())
                .image(row.getImage())
                .detail(itemParser.toDetail(row.getDetailType(), row.getNinjaId(),
                        row.getDetailJson(), row.getDescription(), row.getName()))
                .priceOptions(readPriceOptions(row.getPriceOptions()))
                .build();
    }

    /** 价格选项 JSON（入库时已归一化为英文键）→ 响应对象 */
    private List<HyolPriceOptionResponse> readPriceOptions(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(HyolPriceOptionResponse.class).readValue(json);
        } catch (IOException e) {
            log.warn("彩旗献礼价格选项 JSON 解析失败：{}", e.getMessage());
            return List.of();
        }
    }

    private static String defaultIfBlank(String raw, String fallback) {
        String value = raw == null ? "" : raw.trim();
        return value.isEmpty() ? fallback : value;
    }
}
