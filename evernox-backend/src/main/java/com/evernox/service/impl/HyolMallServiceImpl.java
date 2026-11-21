package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolMallDayResponse;
import com.evernox.dto.HyolMallImportResponse;
import com.evernox.dto.HyolMallItemResponse;
import com.evernox.dto.HyolMallPeriodResponse;
import com.evernox.entity.HyolMallItem;
import com.evernox.entity.HyolMallPeriod;
import com.evernox.exception.BusinessException;
import com.evernox.repository.HyolMallItemRepository;
import com.evernox.repository.HyolMallPeriodRepository;
import com.evernox.service.HyolMallService;
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
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 火影忍者OL 活动详情 · 商城特惠实现。
 *
 * 全部查询都用字符串列名的 QueryWrapper：
 * LambdaQueryWrapper 只认方法引用，传普通 lambda 会在运行时解析不出字段
 * （can not find lambda cache），方法引用又会触发 iDev 空安全告警。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyolMallServiceImpl implements HyolMallService {

    /** 每日窗口：05:00:00 ~ 次日 04:59:59 */
    private static final LocalTime DAY_START = LocalTime.of(5, 0, 0);
    private static final LocalTime DAY_END = LocalTime.of(4, 59, 59);

    private static final String[] WEEKDAY_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_SLASH = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");

    private final HyolMallPeriodRepository periodRepository;
    private final HyolMallItemRepository itemRepository;
    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;
    private final HyolShopItemParser itemParser;

    // ==================== 查询 ====================

    @Override
    public List<HyolMallPeriodResponse> periods() {
        List<HyolMallPeriod> rows = periodRepository.selectList(
                new QueryWrapper<HyolMallPeriod>().orderByDesc("start_time"));
        if (rows.isEmpty()) {
            return List.of();
        }

        List<Long> periodIds = new ArrayList<>();
        for (HyolMallPeriod row : rows) {
            periodIds.add(row.getId());
        }
        List<HyolMallItem> items = itemRepository.selectList(
                new QueryWrapper<HyolMallItem>().in("period_id", periodIds));

        // 期 -> (日期 -> 商品数)
        Map<Long, Map<LocalDate, Integer>> counters = new HashMap<>();
        for (HyolMallItem item : items) {
            if (item.getPeriodId() == null || item.getDayDate() == null) {
                continue;
            }
            Map<LocalDate, Integer> dayCounter = counters.get(item.getPeriodId());
            if (dayCounter == null) {
                dayCounter = new HashMap<>();
                counters.put(item.getPeriodId(), dayCounter);
            }
            Integer current = dayCounter.get(item.getDayDate());
            dayCounter.put(item.getDayDate(), current == null ? 1 : current + 1);
        }

        List<HyolMallPeriodResponse> result = new ArrayList<>();
        for (HyolMallPeriod row : rows) {
            Map<LocalDate, Integer> dayCounter = counters.get(row.getId());
            if (dayCounter == null) {
                dayCounter = Collections.emptyMap();
            }
            result.add(toPeriodResponse(row, dayCounter));
        }
        return result;
    }

    @Override
    public List<HyolMallItemResponse> items(Long periodId, String day) {
        if (periodId == null) {
            throw new BusinessException("缺少期 ID");
        }
        QueryWrapper<HyolMallItem> wrapper = new QueryWrapper<HyolMallItem>()
                .eq("period_id", periodId)
                .orderByAsc("day_date")
                .orderByAsc("slot")
                .orderByAsc("item_id");
        String trimmed = safeTrim(day);
        if (!trimmed.isEmpty()) {
            LocalDate date = parseDate(trimmed);
            if (date == null) {
                throw new BusinessException("日期格式必须是 yyyy-MM-dd");
            }
            wrapper.eq("day_date", date);
        }

        List<HyolMallItem> rows = itemRepository.selectList(wrapper);
        List<HyolMallItemResponse> result = new ArrayList<>();
        for (HyolMallItem row : rows) {
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
        itemRepository.delete(new QueryWrapper<HyolMallItem>().eq("period_id", periodId));
        periodRepository.deleteById(periodId);
        log.info("商城特惠删除整期：periodId={}", periodId);
    }

    @Override
    @Transactional
    public void deleteDay(Long periodId, String day) {
        if (periodId == null) {
            throw new BusinessException("缺少期 ID");
        }
        LocalDate date = parseDate(safeTrim(day));
        if (date == null) {
            throw new BusinessException("日期格式必须是 yyyy-MM-dd");
        }
        if (periodRepository.selectById(periodId) == null) {
            throw new BusinessException("该期不存在，可能已被删除");
        }

        itemRepository.delete(new QueryWrapper<HyolMallItem>()
                .eq("period_id", periodId)
                .eq("day_date", date));

        // 删完这一天后整期空了，期记录也删掉：否则下拉框里会留一条 0 件的空期
        Long left = itemRepository.selectCount(
                new QueryWrapper<HyolMallItem>().eq("period_id", periodId));
        if (left == null || left == 0L) {
            periodRepository.deleteById(periodId);
            log.info("商城特惠删除当天后整期已空，期记录一并删除：periodId={}", periodId);
        }
        log.info("商城特惠删除当天数据：periodId={} day={}", periodId, date);
    }

    // ==================== 导入 ====================

    @Override
    @Transactional
    public HyolMallImportResponse importFolder(MultipartFile manifest, List<MultipartFile> images) {
        if (manifest == null || manifest.isEmpty()) {
            throw new BusinessException("请选择包含「商城特惠.json」的文件夹");
        }
        String manifestName = baseName(manifest.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!manifestName.endsWith(".json")) {
            throw new BusinessException("请选择「商城特惠.json」文件");
        }

        JsonNode root = readJson(manifest);
        JsonNode activity = root.path("活动");
        LocalDateTime periodStart = readTime(activity.path("开始").asText());
        if (periodStart == null) {
            throw new BusinessException("JSON 里缺少「活动.开始」，无法判断属于哪一周");
        }
        LocalDateTime periodEnd = readTime(activity.path("结束").asText());
        if (periodEnd == null || !periodEnd.isAfter(periodStart)) {
            periodEnd = periodStart.toLocalDate().plusDays(6).atTime(23, 59, 59);
        }

        LocalDate dayDate = resolveDayDate(root);
        if (dayDate == null) {
            throw new BusinessException("JSON 里缺少「当日窗口.开始」，无法判断属于哪一天");
        }

        LocalDateTime dayWindowStart = dayDate.atTime(DAY_START);
        LocalDateTime dayWindowEnd = dayDate.plusDays(1).atTime(DAY_END);
        List<HyolShopItemParser.ShopItem> shopItems =
                itemParser.parseItems(root, "折扣价", dayWindowStart, dayWindowEnd);
        List<HyolMallItem> parsed = new ArrayList<>();
        for (HyolShopItemParser.ShopItem shopItem : shopItems) {
            parsed.add(HyolMallItem.builder()
                    .slot(shopItem.slot())
                    .itemId(shopItem.itemId())
                    .name(shopItem.name())
                    .description(shopItem.description())
                    .iconId(shopItem.iconId())
                    .price(shopItem.price())
                    .currency(shopItem.currency())
                    .dailyLimit(shopItem.dailyLimit())
                    .startTime(shopItem.startTime())
                    .endTime(shopItem.endTime())
                    .image(shopItem.image())
                    .detailType(shopItem.detailType())
                    .ninjaId(shopItem.ninjaId())
                    .detailJson(shopItem.detailJson())
                    .build());
        }
        if (parsed.isEmpty()) {
            throw new BusinessException("JSON 里没有可识别的商品");
        }

        // 直接对 selectOne 的结果判空：拆成「先算 boolean created 再 if」会让
        // 编译器丢失空值收敛信息（iDev 会报 period may be null）
        HyolMallPeriod existing = periodRepository.selectOne(
                new QueryWrapper<HyolMallPeriod>().eq("start_time", periodStart).last("limit 1"));
        HyolMallPeriod period;
        boolean created;
        if (existing == null) {
            period = HyolMallPeriod.builder()
                    .activityId(activity.path("活动ID").asLong(0))
                    .shopId(activity.path("商店ID").asLong(0))
                    .name(defaultIfBlank(activity.path("活动名称").asText(), "商城特惠"))
                    .startTime(periodStart)
                    .endTime(periodEnd)
                    .build();
            periodRepository.insert(period);
            created = true;
        } else {
            // 已存在的期：以最新一次导入的元信息为准
            existing.setActivityId(activity.path("活动ID").asLong(0));
            existing.setShopId(activity.path("商店ID").asLong(0));
            existing.setName(defaultIfBlank(activity.path("活动名称").asText(), "商城特惠"));
            existing.setEndTime(periodEnd);
            periodRepository.updateById(existing);
            period = existing;
            created = false;
        }

        Set<String> referenced = itemParser.collectReferencedImages(root);
        int imageCount = itemParser.storeImages(
                images, referenced, storageConfig.getMallImagePath(), "商城特惠");
        int missingImages = 0;
        for (String name : referenced) {
            if (!Files.exists(storageConfig.getMallImagePath().resolve(name))) {
                missingImages++;
            }
        }

        // 当天的数据是快照：先删后插，重复导入天然幂等
        itemRepository.delete(new QueryWrapper<HyolMallItem>()
                .eq("period_id", period.getId())
                .eq("day_date", dayDate));
        for (HyolMallItem item : parsed) {
            item.setPeriodId(period.getId());
            item.setDayDate(dayDate);
            itemRepository.insert(item);
        }

        String periodLabel = periodStart.toLocalDate().format(DATE)
                + " ~ " + periodEnd.toLocalDate().format(DATE);
        log.info("商城特惠导入完成：期 {} 日 {} 商品 {} 件，图片 {} 张，缺图 {} 张",
                periodLabel, dayDate, parsed.size(), imageCount, missingImages);

        return HyolMallImportResponse.builder()
                .periodId(period.getId())
                .periodLabel(periodLabel)
                .day(dayDate.format(DATE))
                .weekday(weekdayOf(dayDate))
                .itemCount(parsed.size())
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

    /** 归属日：优先「当日窗口.开始」，退而取第一件商品的开始时间 */
    private LocalDate resolveDayDate(JsonNode root) {
        LocalDateTime dayStart = readTime(root.path("当日窗口").path("开始").asText());
        if (dayStart != null) {
            return dayStart.toLocalDate();
        }
        for (JsonNode node : root.path("商品")) {
            LocalDateTime itemStart = readTime(node.path("开始").asText());
            if (itemStart != null) {
                return itemStart.toLocalDate();
            }
        }
        return null;
    }

    // ==================== 组装 ====================

    private HyolMallPeriodResponse toPeriodResponse(HyolMallPeriod row, Map<LocalDate, Integer> dayCounter) {
        LocalDate start = row.getStartTime().toLocalDate();
        LocalDate end = row.getEndTime().toLocalDate();
        if (end.isBefore(start)) {
            end = start;
        }

        List<HyolMallDayResponse> days = new ArrayList<>();
        int total = 0;
        int filled = 0;
        LocalDate cursor = start;
        while (!cursor.isAfter(end)) {
            Integer count = dayCounter.get(cursor);
            int itemCount = count == null ? 0 : count;
            total += itemCount;
            if (itemCount > 0) {
                filled++;
            }
            LocalDateTime windowEnd = cursor.plusDays(1).atTime(DAY_END);
            if (windowEnd.isAfter(row.getEndTime())) {
                windowEnd = row.getEndTime();
            }
            days.add(HyolMallDayResponse.builder()
                    .date(cursor.format(DATE))
                    .weekday(weekdayOf(cursor))
                    .itemCount(itemCount)
                    .windowStart(cursor.atTime(DAY_START))
                    .windowEnd(windowEnd)
                    .build());
            cursor = cursor.plusDays(1);
        }

        return HyolMallPeriodResponse.builder()
                .id(row.getId())
                .activityId(row.getActivityId())
                .shopId(row.getShopId())
                .name(row.getName())
                .startTime(row.getStartTime())
                .endTime(row.getEndTime())
                .label(start.format(DATE) + " ~ " + end.format(DATE))
                .dayCount(days.size())
                .itemCount(total)
                .filledDayCount(filled)
                .days(days)
                .build();
    }

    private HyolMallItemResponse toItemResponse(HyolMallItem row) {
        return HyolMallItemResponse.builder()
                .id(row.getId())
                .periodId(row.getPeriodId())
                .dayDate(row.getDayDate())
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
                .build();
    }

    // ==================== 小工具 ====================

    private static LocalDateTime readTime(String raw) {
        String value = safeTrim(raw);
        if (value.isEmpty()) {
            return null;
        }
        LocalDateTime parsed = tryParse(value, DATE_TIME);
        if (parsed == null) {
            parsed = tryParse(value, DATE_MINUTE);
        }
        if (parsed == null) {
            parsed = tryParse(value, DATE_SLASH);
        }
        return parsed;
    }

    private static LocalDateTime tryParse(String value, DateTimeFormatter formatter) {
        try {
            return LocalDateTime.parse(value, formatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value, DATE);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String weekdayOf(LocalDate date) {
        int index = date.getDayOfWeek().getValue() - 1;
        return WEEKDAY_LABELS[index];
    }

    /** 取文件名部分：兼容 Windows 反斜杠路径，并挡住 ../ 之类的路径穿越 */
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
}
