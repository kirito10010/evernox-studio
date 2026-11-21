package com.evernox.service;

import com.evernox.dto.HyolMallBonusResponse;
import com.evernox.dto.HyolMallDetailResponse;
import com.evernox.dto.HyolMallDropResponse;
import com.evernox.dto.HyolMallSkillResponse;
import com.evernox.util.ImageTypeValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 活动商店商品解析（商城特惠 / 彩旗献礼共用）。
 *
 * <p>两边导出的 JSON 商品结构完全一致（忍者 / 掉落 / 需求碎片 / 普通说明），
 * 只有「价格字段名」和「图片目录」不同，因此解析、详情归一化、图片落盘都放在这里，
 * 各活动只负责把 {@link ShopItem} 映射成自己的实体。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HyolShopItemParser {

    /** 详情类型 */
    public static final String DETAIL_NINJA = "ninja";
    public static final String DETAIL_DROPS = "drops";
    /** 通灵兽碎片：只有说明 + 需求碎片（如梦貘） */
    public static final String DETAIL_BEAST = "beast";
    /** 普通材料：只有说明 */
    public static final String DETAIL_ITEM = "item";

    /** 新格式导出的图标文件名带中文（技能_xxx.png / 货币_元宝.png），白名单需放行中文 */
    private static final Pattern IMAGE_NAME =
            Pattern.compile("^[0-9A-Za-z_\\-\\u4e00-\\u9fa5]{1,80}\\.(png|jpg|jpeg|webp|gif)$");
    /** 旧格式「价格」是数字；新格式是「元宝250 或 点券250」这样的文本，兜底抽第一个数字 */
    private static final Pattern FIRST_NUMBER = Pattern.compile("(\\d+)");
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    private static final int MAX_CATEGORY = 32;
    private static final int MAX_NAME = 128;
    private static final int MAX_DESCRIPTION = 512;
    private static final int MAX_CURRENCY = 16;
    /** 技能说明原文可能很长，DTO 里放宽（不落库，只在响应里出现） */
    private static final int MAX_SKILL_TEXT = 2000;

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_SLASH = DateTimeFormatter.ofPattern("yyyy/M/d H:mm");

    private final ObjectMapper objectMapper;

    /** 与实体解耦：各活动自行映射成自己的实体 */
    public record ShopItem(
            String category,
            int slot,
            long itemId,
            String name,
            String description,
            long iconId,
            int price,
            String currency,
            int dailyLimit,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String image,
            String detailType,
            Long ninjaId,
            String detailJson,
            String priceOptionsJson) {
    }

    /**
     * 解析「商品」数组。
     *
     * @param priceField   价格字段名：商城特惠=折扣价，彩旗献礼=价格
     * @param fallbackStart 商品缺「开始」时的回退时间
     * @param fallbackEnd   商品缺「结束」时的回退时间
     */
    public List<ShopItem> parseItems(JsonNode root, String priceField,
                                     LocalDateTime fallbackStart, LocalDateTime fallbackEnd) {
        List<ShopItem> parsed = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (JsonNode node : root.path("商品")) {
            long itemId = node.path("物品ID").asLong(0);
            if (itemId <= 0 || !seen.add(itemId)) {
                continue;
            }
            LocalDateTime start = readTime(node.path("开始").asText());
            LocalDateTime end = readTime(node.path("结束").asText());
            if (start == null) {
                start = fallbackStart;
            }
            if (end == null) {
                end = fallbackEnd;
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
                    log.warn("活动商品同时带「忍者」与「掉落」，按忍者处理，itemId={}", itemId);
                }
            } else if (hasDrops) {
                detailType = DETAIL_DROPS;
                detailJson = dropsNode.toString();
            } else if (hasBeast) {
                // 通灵兽碎片：详情就写在商品自身，拼一个小对象入库
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
                // 普通材料：浮窗只显示「说明」，说明已在 description 列
                detailType = DETAIL_ITEM;
            }

            // 价格：新格式用「价格选项」明确币种；旧格式是数字；再兜底从文本抽数字
            String priceOptionsJson = null;
            int price = -1;
            JsonNode optionNodes = node.path("价格选项");
            if (optionNodes.isArray() && !optionNodes.isEmpty()) {
                ArrayNode options = objectMapper.createArrayNode();
                for (JsonNode option : optionNodes) {
                    int optionPrice = option.path("价格").asInt(0);
                    ObjectNode normalized = objectMapper.createObjectNode();
                    normalized.put("currency", cut(safeTrim(option.path("货币").asText()), MAX_CURRENCY));
                    normalized.put("price", optionPrice);
                    String optionIcon = baseName(option.path("图标").asText());
                    if (!optionIcon.isEmpty()) {
                        normalized.put("icon", optionIcon);
                    }
                    options.add(normalized);
                    if (price < 0) {
                        price = optionPrice;
                    }
                }
                priceOptionsJson = options.toString();
            }
            if (price < 0) {
                JsonNode priceNode = node.path(priceField);
                if (priceNode.isNumber()) {
                    price = priceNode.asInt();
                } else {
                    Matcher matcher = FIRST_NUMBER.matcher(priceNode.asText());
                    if (matcher.find()) {
                        price = Integer.parseInt(matcher.group(1));
                    }
                }
            }
            if (price < 0) {
                // 字段名对不上时兜底，两个活动都不会因为改字段名直接变成 0 价
                price = node.path("折扣价").asInt(node.path("价格").asInt(0));
            }

            parsed.add(new ShopItem(
                    cut(safeTrim(node.path("类别").asText()), MAX_CATEGORY),
                    node.path("槽位").asInt(0),
                    itemId,
                    cut(safeTrim(node.path("名称").asText()), MAX_NAME),
                    cut(safeTrim(node.path("说明").asText()), MAX_DESCRIPTION),
                    node.path("图标ID").asLong(0),
                    price,
                    cut(defaultIfBlank(node.path("货币").asText(), "点券"), MAX_CURRENCY),
                    node.path("每日限购").asInt(0),
                    start,
                    end,
                    baseName(node.path("图片").asText()),
                    detailType,
                    ninjaId,
                    detailJson,
                    priceOptionsJson));
        }
return parsed;
    }

    /**
     * 导入时需要落盘的图片集合：商品图 + 技能图标 + 货币图标。
     *
     * <p>新版导出把技能/货币图标也放在 images/ 下，只按商品「图片」字段收集会全部漏掉。
     */
    public Set<String> collectReferencedImages(JsonNode root) {
        // 注意：这里不能直接调用 collectReferencedImages(JsonNode) 的同名重载，
        // 因为 root.path("商品") 的静态类型仍是 JsonNode，会递归命中自己。
        Set<String> names = collectImagesOf(root.path("商品"));
        JsonNode currencyIcons = root.path("货币图标");
        if (currencyIcons.isObject()) {
            for (JsonNode icon : currencyIcons) {
                addImageName(names, icon.asText());
            }
        }
        return names;
    }

    /** 按商品节点集合收集：商品图 + 价格选项图标 + 技能图标 */
    public Set<String> collectReferencedImages(Iterable<JsonNode> items) {
        return collectImagesOf(items);
    }

    private Set<String> collectImagesOf(Iterable<JsonNode> items) {
        Set<String> names = new HashSet<>();
        for (JsonNode node : items) {
            addImageName(names, node.path("图片").asText());
            for (JsonNode option : node.path("价格选项")) {
                addImageName(names, option.path("图标").asText());
            }
            for (JsonNode skill : node.path("忍者").path("技能列表")) {
                addImageName(names, skill.path("图标").asText());
            }
        }
        return names;
    }

    private void addImageName(Set<String> names, String raw) {
        String name = baseName(raw);
        if (!name.isEmpty()) {
            names.add(name);
        }
    }

    /** 图片落盘：只保存数据里真正引用到的文件名，路径穿越/超大/非图片一律跳过 */
    public int storeImages(List<MultipartFile> images, Set<String> referenced,
                           Path targetDir, String logLabel) {
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
                log.warn("{}图片超过 8MB，已跳过: {}", logLabel, name);
                continue;
            }
            try {
                byte[] bytes = file.getBytes();
                if (!ImageTypeValidator.isSupportedImage(bytes)) {
                    log.warn("{}图片格式不支持，已跳过: {}", logLabel, name);
                    continue;
                }
                Path target = targetDir.resolve(name);
                Files.write(target, bytes,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                stored++;
            } catch (IOException e) {
                log.warn("{}图片保存失败: {}, {}", logLabel, name, e.getMessage());
            }
        }
        return stored;
    }

    /**
     * 详情原始 JSON → 稳定的前端契约；解析失败退化成「只有说明」，不影响列表。
     */
    public HyolMallDetailResponse toDetail(String detailType, Long ninjaId,
                                           String detailJson, String description, String itemName) {
        String type = safeTrim(detailType);
        String desc = safeTrim(description);

        // 老数据没有 detail_type：按「物品说明」处理
        if (type.isEmpty()) {
            return desc.isEmpty() ? null : itemDetail(desc);
        }
        // 普通材料不需要 JSON：说明就在 description 列
        if (DETAIL_ITEM.equals(type)) {
            return itemDetail(desc);
        }

        JsonNode detail;
        try {
            detail = objectMapper.readTree(detailJson);
        } catch (IOException e) {
            log.warn("活动商品详情 JSON 解析失败，itemId 未知，{}", e.getMessage());
            return desc.isEmpty() ? null : itemDetail(desc);
        }
        if (detail == null || !detail.isObject()) {
            return desc.isEmpty() ? null : itemDetail(desc);
        }

        if (DETAIL_NINJA.equals(type)) {
            return buildNinjaDetail(ninjaId, detail, itemName);
        }
        if (DETAIL_DROPS.equals(type)) {
            return HyolMallDetailResponse.builder()
                    .type(DETAIL_DROPS)
                    .description(desc)
                    .marked(safeTrim(detail.path("说明标记").asText()))
                    .fixedDrops(toDrops(detail.path("固定")))
                    .randomDrops(toDrops(detail.path("随机")))
                    .build();
        }
        if (DETAIL_BEAST.equals(type)) {
            Integer needFragments = intOrNull(detail.path("需求碎片"));
            String beastDesc = defaultIfBlank(detail.path("说明").asText(), desc);
            return HyolMallDetailResponse.builder()
                    .type(DETAIL_BEAST)
                    .description(beastDesc)
                    .marked(defaultIfBlank(detail.path("说明标记").asText(), beastDesc))
                    .needFragments(needFragments == null || needFragments <= 0 ? null : needFragments)
                    .bonus(toBonus(detail.path("属性加成")))
                    .build();
        }
        return desc.isEmpty() ? null : itemDetail(desc);
    }

    private HyolMallDetailResponse itemDetail(String description) {
        return HyolMallDetailResponse.builder()
                .type(DETAIL_ITEM)
                .description(description)
                .build();
    }

    private HyolMallDetailResponse buildNinjaDetail(Long ninjaId, JsonNode detail, String itemName) {
        List<HyolMallSkillResponse> skills = new ArrayList<>();

        // 新格式：统一放在「技能列表」里，每条自带「槽位」，顺序不固定，必须按数组原序渲染
        JsonNode skillList = detail.path("技能列表");
        if (skillList.isArray()) {
            for (JsonNode node : skillList) {
                addSkill(skills, safeTrim(node.path("槽位").asText()), node);
            }
        } else {
            // 老格式：奥义/普攻/追打平铺 + 「被动」数组
            addSkill(skills, "奥义", detail.path("奥义"));
            addSkill(skills, "普攻", detail.path("普攻"));
            addSkill(skills, "追打", detail.path("追打"));
            for (JsonNode node : detail.path("被动")) {
                addSkill(skills, "被动", node);
            }
        }

        // 忍者名只用商品名去掉「碎片」：导出「忍者ID」与图鉴 nid 体系不一致，不做关联
        return HyolMallDetailResponse.builder()
                .type(DETAIL_NINJA)
                .ninjaId(ninjaId)
                .ninjaName(fallbackNinjaName(itemName))
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
                .skillId(longOrNull(node.path("技能ID")))
                .slot(slot.isEmpty() ? "其他" : slot)
                .name(cut(name, MAX_NAME))
                .icon(emptyToNull(baseName(node.path("图标").asText())))
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

    private static Long longOrNull(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asLong();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** 通灵兽的属性加成：{"生命":30,...} → 英文键；缺字段留 null */
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

    /** 物品名去掉「碎片」（蝎[绯流琥]碎片 → 蝎[绯流琥]） */
    private static String fallbackNinjaName(String itemName) {
        String name = safeTrim(itemName);
        return name.endsWith("碎片") ? name.substring(0, name.length() - 2) : name;
    }

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

    /** 供上层复用的文件名清洗（导入入口校验 manifest 名时使用） */
    public static String fileNameOf(String raw) {
        return baseName(raw);
    }

    /** 供上层复用的时间解析 */
    public static LocalDateTime parseTime(String raw) {
        return readTime(raw);
    }

    /** 统一小写扩展名判断，避免各活动各写一套 */
    public static String lowerName(String raw) {
        return baseName(raw).toLowerCase(Locale.ROOT);
    }
}
