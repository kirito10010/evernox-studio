package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evernox.config.StorageConfig;
import com.evernox.dto.HyolMallBonusResponse;
import com.evernox.dto.HyolMallDayResponse;
import com.evernox.dto.HyolMallDetailResponse;
import com.evernox.dto.HyolMallDropResponse;
import com.evernox.dto.HyolMallImportResponse;
import com.evernox.dto.HyolMallItemResponse;
import com.evernox.dto.HyolMallPeriodResponse;
import com.evernox.dto.HyolMallSkillResponse;
import com.evernox.entity.HyolMallItem;
import com.evernox.entity.HyolMallPeriod;
import com.evernox.exception.BusinessException;
import com.evernox.repository.HyolMallItemRepository;
import com.evernox.repository.HyolMallPeriodRepository;
import com.evernox.service.HyolMallService;
import com.evernox.util.ImageTypeValidator;
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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final Pattern IMAGE_NAME =
            Pattern.compile("^[0-9A-Za-z_\\-]{1,60}\\.(png|jpg|jpeg|webp|gif)$");
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    private static final int MAX_NAME = 128;
    private static final int MAX_DESCRIPTION = 512;
    private static final int MAX_CURRENCY = 16;
    /** 技能说明原文可能很长，DTO 里放宽（不落库，只在响应里出现） */
    private static final int MAX_SKILL_TEXT = 2000;

    /** 详情类型 */
    private static final String DETAIL_NINJA = "ninja";
    private static final String DETAIL_DROPS = "drops";
    /** 通灵兽碎片：只有说明 + 需求碎片（如梦貘） */
    private static final String DETAIL_BEAST = "beast";
    /** 普通材料：只有说明 */
    private static final String DETAIL_ITEM = "item";

    private final HyolMallPeriodRepository periodRepository;
    private final HyolMallItemRepository itemRepository;
    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;

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

        List<HyolMallItem> parsed = parseItems(root, dayDate);
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

        Set<String> referenced = new HashSet<>();
        for (HyolMallItem item : parsed) {
            if (item.getImage() != null && !item.getImage().isEmpty()) {
                referenced.add(item.getImage());
            }
        }
        int imageCount = storeImages(images, referenced);
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

    private List<HyolMallItem> parseItems(JsonNode root, LocalDate dayDate) {
        List<HyolMallItem> parsed = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (JsonNode node : root.path("商品")) {
            long itemId = node.path("物品ID").asLong(0);
            if (itemId <= 0 || !seen.add(itemId)) {
                continue;
            }
            LocalDateTime start = readTime(node.path("开始").asText());
            LocalDateTime end = readTime(node.path("结束").asText());
            if (start == null) {
                start = dayDate.atTime(DAY_START);
            }
            if (end == null) {
                end = dayDate.plusDays(1).atTime(DAY_END);
            }

            // 详情分四类，优先级：忍者 > 掉落 > 通灵兽(需求碎片) > 普通材料
            // 原始 JSON 原样入库，不改字段名
            JsonNode ninjaNode = node.path("忍者");
            JsonNode dropsNode = node.path("掉落");
            boolean hasNinja = ninjaNode.isObject();
            boolean hasDrops = dropsNode.isObject();
            boolean hasBeast = node.hasNonNull("需求碎片");
            String detailType;
            Long ninjaId = null;
            String detailJson = null;
            if (hasNinja) {
                detailType = DETAIL_NINJA;
                long ninjaIdValue = ninjaNode.path("忍者ID").asLong(0);
                ninjaId = ninjaIdValue > 0 ? ninjaIdValue : null;
                detailJson = ninjaNode.toString();
                if (hasDrops) {
                    log.warn("商城特惠商品同时带「忍者」与「掉落」，按忍者处理，itemId={}", itemId);
                }
            } else if (hasDrops) {
                detailType = DETAIL_DROPS;
                detailJson = dropsNode.toString();
            } else if (hasBeast) {
                // 通灵兽碎片：详情就写在商品自身（需求碎片 + 属性加成 + 带【】的说明标记），拼一个小对象入库
                detailType = DETAIL_BEAST;
                ObjectNode beast = objectMapper.createObjectNode();
                beast.put("需求碎片", node.path("需求碎片").asInt(0));
                beast.put("说明", safeTrim(node.path("说明").asText()));
                beast.put("说明标记", safeTrim(node.path("说明标记").asText()));
                JsonNode beastHighlights = node.path("高亮");
                if (beastHighlights.isArray()) {
                    beast.set("高亮", beastHighlights);
                }
                JsonNode beastBonus = node.path("属性加成");
                if (beastBonus.isObject()) {
                    beast.set("属性加成", beastBonus);
                }
                detailJson = beast.toString();
            } else {
                // 普通材料：浮窗只显示「说明」，说明已在 description 列，不需要额外 JSON
                detailType = DETAIL_ITEM;
            }

            parsed.add(HyolMallItem.builder()
                    .slot(node.path("槽位").asInt(0))
                    .itemId(itemId)
                    .name(cut(safeTrim(node.path("名称").asText()), MAX_NAME))
                    .description(cut(safeTrim(node.path("说明").asText()), MAX_DESCRIPTION))
                    .iconId(node.path("图标ID").asLong(0))
                    .price(node.path("折扣价").asInt(0))
                    .currency(cut(defaultIfBlank(node.path("货币").asText(), "点券"), MAX_CURRENCY))
                    .dailyLimit(node.path("每日限购").asInt(0))
                    .startTime(start)
                    .endTime(end)
                    .image(baseName(node.path("图片").asText()))
                    .detailType(detailType)
                    .ninjaId(ninjaId)
                    .detailJson(detailJson)
                    .build());
        }
        return parsed;
    }

    /** 图片落盘：只保存数据里真正引用到的文件名，路径穿越/超大/非图片一律跳过 */
    private int storeImages(List<MultipartFile> images, Set<String> referenced) {
        if (images == null || images.isEmpty() || referenced.isEmpty()) {
            return 0;
        }
        int stored = 0;
        for (MultipartFile file : images) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            String name = baseName(file.getOriginalFilename());
            if (!referenced.contains(name) || !IMAGE_NAME.matcher(name).matches()) {
                continue;
            }
            if (file.getSize() > MAX_IMAGE_BYTES) {
                log.warn("商城特惠图片超过 8MB，已跳过: {}", name);
                continue;
            }
            try {
                byte[] bytes = file.getBytes();
                if (!ImageTypeValidator.isSupportedImage(bytes)) {
                    log.warn("商城特惠图片格式不支持，已跳过: {}", name);
                    continue;
                }
                Path target = storageConfig.getMallImagePath().resolve(name);
                Files.write(target, bytes,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                stored++;
            } catch (IOException e) {
                log.warn("商城特惠图片保存失败: {}, {}", name, e.getMessage());
            }
        }
        return stored;
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
                .detail(buildDetail(row))
                .build();
    }

    // ==================== 详情归一化 ====================

    /** 原始 JSON → 稳定的前端契约；解析失败退化成「只有说明」，不影响列表 */
    private HyolMallDetailResponse buildDetail(HyolMallItem row) {
        String type = safeTrim(row.getDetailType());
        String description = safeTrim(row.getDescription());

        // 本次改造前导入的老数据没有 detail_type：材料/普通道具按「物品说明」处理
        if (type.isEmpty()) {
            // 老数据里的通灵兽碎片无法还原「需求碎片」，重新导入即可补齐
            if (description.isEmpty()) {
                return null;
            }
            return HyolMallDetailResponse.builder()
                    .type(DETAIL_ITEM)
                    .description(description)
                    .build();
        }

        // 普通材料不需要 JSON：说明就在 description 列
        if (DETAIL_ITEM.equals(type)) {
            return HyolMallDetailResponse.builder()
                    .type(DETAIL_ITEM)
                    .description(description)
                    .build();
        }

        JsonNode detail;
        try {
            detail = objectMapper.readTree(row.getDetailJson());
        } catch (IOException e) {
            log.warn("商城特惠详情 JSON 解析失败，itemId={}，{}", row.getItemId(), e.getMessage());
            return description.isEmpty() ? null : HyolMallDetailResponse.builder()
                    .type(DETAIL_ITEM)
                    .description(description)
                    .build();
        }
        if (detail == null || !detail.isObject()) {
            return description.isEmpty() ? null : HyolMallDetailResponse.builder()
                    .type(DETAIL_ITEM)
                    .description(description)
                    .build();
        }

        if (DETAIL_NINJA.equals(type)) {
            // 忍者浮窗只显示技能，不显示「招募，升星X所需材料」这句
            return buildNinjaDetail(row, detail);
        }
        if (DETAIL_DROPS.equals(type)) {
            return HyolMallDetailResponse.builder()
                    .type(DETAIL_DROPS)
                    .description(description)
                    .marked(safeTrim(detail.path("说明标记").asText()))
                    .fixedDrops(toDrops(detail.path("固定")))
                    .randomDrops(toDrops(detail.path("随机")))
                    .build();
        }
        if (DETAIL_BEAST.equals(type)) {
            Integer needFragments = intOrNull(detail.path("需求碎片"));
            String beastDesc = defaultIfBlank(detail.path("说明").asText(), description);
            return HyolMallDetailResponse.builder()
                    .type(DETAIL_BEAST)
                    .description(beastDesc)
                    .marked(defaultIfBlank(detail.path("说明标记").asText(), beastDesc))
                    .needFragments(needFragments == null || needFragments <= 0 ? null : needFragments)
                    .bonus(toBonus(detail.path("属性加成")))
                    .build();
        }
        return description.isEmpty() ? null : HyolMallDetailResponse.builder()
                .type(DETAIL_ITEM)
                .description(description)
                .build();
    }

    private HyolMallDetailResponse buildNinjaDetail(HyolMallItem row, JsonNode detail) {
        List<HyolMallSkillResponse> skills = new ArrayList<>();

        // 新格式：统一放在「技能列表」里，每条自带「槽位」（奥义/普攻/追打/触发/被动 都可能出现），
        // 且顺序不固定（有的忍者「触发」在普攻后，有的在追打后），必须按数组原序渲染。
        JsonNode skillList = detail.path("技能列表");
        if (skillList.isArray()) {
            for (JsonNode node : skillList) {
                addSkill(skills, safeTrim(node.path("槽位").asText()), node);
            }
        } else {
            // 老格式（2026-11-06 225633 及更早导出）：奥义/普攻/追打平铺 + 「被动」数组
            addSkill(skills, "奥义", detail.path("奥义"));
            addSkill(skills, "普攻", detail.path("普攻"));
            addSkill(skills, "追打", detail.path("追打"));
            for (JsonNode node : detail.path("被动")) {
                addSkill(skills, "被动", node);
            }
        }

        // 忍者名只用商品名去掉「碎片」。商城特惠不读忍者图鉴（图鉴以后要重做），
        // 导出的「忍者ID」与图鉴 nid 体系也不一致，耦合只会显示成别的忍者。
        return HyolMallDetailResponse.builder()
                .type(DETAIL_NINJA)
                .ninjaId(row.getNinjaId())
                .ninjaName(fallbackNinjaName(row.getName()))
                .skills(skills)
                .fixedDrops(List.of())
                .randomDrops(List.of())
                .build();
    }

    /** 一条技能：空对象不补占位；槽位缺失时归到「其他」，不丢技能 */
    private void addSkill(List<HyolMallSkillResponse> skills, String slot, JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        String name = safeTrim(node.path("名称").asText());
        if (name.isEmpty()) {
            return;
        }
        skills.add(HyolMallSkillResponse.builder()
                .slot(slot.isEmpty() ? "其他" : slot)
                .name(cut(name, MAX_NAME))
                .description(cut(safeTrim(node.path("说明").asText()), MAX_SKILL_TEXT))
                .marked(cut(safeTrim(node.path("说明标记").asText()), MAX_SKILL_TEXT))
                .highlights(readHighlights(node.path("高亮")))
                .chakra(intOrNull(node.path("查克拉")))
                .cooldown(intOrNull(node.path("冷却")))
                .entryCooldown(intOrNull(node.path("进场冷却")))
                .build());
    }

    private List<HyolMallDropResponse> toDrops(JsonNode node) {
        List<HyolMallDropResponse> drops = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return drops;
        }
        for (JsonNode item : node) {
            String name = safeTrim(item.path("名称").asText());
            if (name.isEmpty()) {
                continue;
            }
            drops.add(HyolMallDropResponse.builder()
                    .name(cut(name, MAX_NAME))
                    .count(item.path("数量").asInt(1))
                    .itemId(item.path("物品ID").asLong(0))
                    .build());
        }
        return drops;
    }

    private static List<String> readHighlights(JsonNode node) {
        List<String> highlights = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return highlights;
        }
        for (JsonNode item : node) {
            String keyword = safeTrim(item.asText());
            if (!keyword.isEmpty()) {
                highlights.add(keyword);
            }
        }
        return highlights;
    }

    private static Integer intOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    /** 通灵兽的属性加成：{"生命":30,"攻击":15,"防御":12,"忍术":15} → 英文键；缺字段留 null */
    private static HyolMallBonusResponse toBonus(JsonNode node) {
        if (node == null || !node.isObject() || node.isEmpty()) {
            return null;
        }
        HyolMallBonusResponse bonus = HyolMallBonusResponse.builder()
                .life(intOrNull(node.path("生命")))
                .attack(intOrNull(node.path("攻击")))
                .defense(intOrNull(node.path("防御")))
                .ninjutsu(intOrNull(node.path("忍术")))
                .build();
        if (bonus.getLife() == null && bonus.getAttack() == null
                && bonus.getDefense() == null && bonus.getNinjutsu() == null) {
            return null;
        }
        return bonus;
    }

    /** 图鉴里查不到时：物品名去掉「碎片」（蝎[绯流琥]碎片 → 蝎[绯流琥]） */
    private static String fallbackNinjaName(String itemName) {
        String name = safeTrim(itemName);
        return name.endsWith("碎片") ? name.substring(0, name.length() - 2) : name;
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

    private static String cut(String raw, int max) {
        String value = safeTrim(raw);
        return value.length() <= max ? value : value.substring(0, max);
    }
}
