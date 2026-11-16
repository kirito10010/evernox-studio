package com.evernox.codex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 由客户端配置表推导图鉴数据集。
 *
 * <p>推导链：{@code CardItemCFG}（名单）→ {@code NinjaInfoCFG}（基础信息 / 星级模型）
 * → {@code NinjaSkillCFG}（槽位）→ {@code SkillCFG}（技能正文）→ {@code SkillKeyDescCFG}（状态词条）。
 */
public final class CodexBuilder {

    /** 旧数据集里的补充信息：稀有度（客户端配置无法推导）与缺失技能名。 */
    public record Legacy(Map<String, Integer> rare, Map<String, String> skillNames) {
        public static Legacy empty() {
            return new Legacy(Map.of(), Map.of());
        }
    }

    public record Result(CodexDataset dataset, List<String> warnings) {
    }

    private static final int DATASET_VERSION = 9;
    private static final List<String> ROLE_ORDER = List.of("奥义", "普攻", "追打", "触发", "被动", "其他");
    private static final Pattern CARD_NAME = Pattern.compile("招募，升星(.+?)所需材料");
    private static final Pattern FONT_TAG = Pattern.compile("<font[^>]*>(.*?)</font>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    /** 变身后候选的语义线索（filterTxt / description / title） */
    private static final Pattern TRANSFORM_HINT = Pattern.compile(
            "变身|变回|阶段|状态|暴走|强化|复活|六道|死门|突破用|爆发|附着");
    /** 分身 / NPC / 剧情副本等非变身后行 */
    private static final Pattern NPC_ROW = Pattern.compile(
            "分身|NPC|战场|试炼|BOSS|首领|神符|组织据点|剧情|挑战|训练|召唤");
    private static final Set<String> GENERIC_KEYWORDS = Set.of(
            "技能效果", "新效果", "效果", "部分技能", "奥义效果", "额外效果", "无");
    private static final Map<Integer, String> ROLE_BY_TYPE = Map.of(
            0, "普攻", 1, "追打", 2, "奥义", 3, "被动", 4, "触发");

    /** NinjaInfoCFG.property → 游戏内属性 */
    private static final Map<Integer, String> ATTRIBUTE_BY_PROPERTY = Map.of(
            1, "水", 2, "火", 3, "风", 4, "雷", 5, "土");

    /** NinjaInfoCFG.showPropertys → 官方标签（与游戏内 property/*.png 编号一致） */
    private static final Map<Integer, String> TAG_BY_PROPERTY = Map.ofEntries(
            Map.entry(1, "木叶"), Map.entry(2, "砂隐"), Map.entry(3, "音隐"), Map.entry(4, "雾隐"),
            Map.entry(5, "云隐"), Map.entry(6, "雨隐"), Map.entry(7, "泷隐"), Map.entry(9, "岩隐"),
            Map.entry(10, "死司凭血"), Map.entry(11, "晓"), Map.entry(12, "神秘"), Map.entry(13, "人柱力"),
            Map.entry(14, "血继限界"), Map.entry(15, "咒印"), Map.entry(16, "通灵"), Map.entry(17, "傀儡"),
            Map.entry(18, "影"), Map.entry(19, "暗部"), Map.entry(31, "宇智波一族"), Map.entry(32, "千手一族"),
            Map.entry(33, "日向一族"), Map.entry(34, "奈良一族"), Map.entry(35, "山中一族"), Map.entry(36, "秋道一族"),
            Map.entry(46, "轮回眼"), Map.entry(47, "漩涡一族"), Map.entry(48, "秘传技"), Map.entry(49, "带刀"),
            Map.entry(51, "秽土转生"), Map.entry(52, "男性"), Map.entry(53, "雄性"), Map.entry(54, "女性"),
            Map.entry(55, "雌性"), Map.entry(56, "大筒木一族"), Map.entry(57, "忍联合军"), Map.entry(58, "仙术"),
            Map.entry(59, "血继淘汰"), Map.entry(60, "血继网罗"), Map.entry(61, "佐助真传"), Map.entry(62, "新春"),
            Map.entry(63, "夏装"), Map.entry(64, "泳装"), Map.entry(65, "中秋"), Map.entry(66, "冬日"),
            Map.entry(67, "中国风"), Map.entry(68, "西装"), Map.entry(69, "异装"), Map.entry(70, "和服"),
            Map.entry(71, "武士"), Map.entry(72, "博人传"));

    private final CaptureReader.Bundle bundle;
    private final Legacy legacy;
    private final List<String> warnings = new ArrayList<>();

    private final Map<String, Map<String, Object>> infos = new LinkedHashMap<>();
    private final Map<String, String> ninjaIdByModelId = new HashMap<>();
    private final Map<String, List<String>> ninjaIdsByName = new HashMap<>();
    private final Map<String, Map<String, Object>> ninjaSkills = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> battleInfos = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> skills = new LinkedHashMap<>();
    private final List<Map<String, Object>> cards = new ArrayList<>();
    /** ninjaSeries → 该系列全部忍者 ID（含无卡形态 / NPC 分身） */
    private final Map<String, List<String>> ninjaIdsBySeries = new HashMap<>();
    /** CardItemCFG 中真实可玩的忍者 ID */
    private final Set<String> cardNinjaIds = new HashSet<>();
    /** 赛季忍者（SeasonTaskNinjaInfoCFG 的 key 集合） */
    private final Set<String> seasonNinjaIds = new HashSet<>();
    /** 基础忍者 → 变身后形态（全局一对一分配） */
    private final Map<String, String> extraBodyByBase = new HashMap<>();
    private final Map<String, Integer> attrTupoStarByNinja = new HashMap<>();
    private final List<Map<String, Object>> keyDescs = new ArrayList<>();
    private final Set<String> keywordKeys = new LinkedHashSet<>();
    private final Map<String, String> exStateNameById = new HashMap<>();

    private final Map<String, CodexDataset.CodexSkill> skillDict = new LinkedHashMap<>();
    private final Map<String, String> skillRoles = new HashMap<>();
    /** 变身后技能与基础形态同名时的兜底：描述文本 → 基础技能名 */
    private Map<String, String> nameHints = Map.of();

    /** 变身后技能复用基础形态同技能图标：技能键 → skills/{iconId}.png */
    private final Map<String, String> iconOverrides = new HashMap<>();
    /** 变身后技能名（按技能键整体 +10 从变身前继承） */
    private final Map<String, String> nameOverrides = new HashMap<>();
    /** 后 4 位序号 → 全部 {@code 21xxxxxx} 技能键（惰性构建） */
    private Map<String, List<String>> idsByTail4Cache;
    /** 技能描述 → 同描述技能键（惰性构建，用于「同一技能多条候选」的名字复用） */
    private Map<String, List<String>> idsByDescCache;

    private CodexBuilder(CaptureReader.Bundle bundle, Legacy legacy) {
        this.bundle = bundle;
        this.legacy = legacy;
    }

    public static Result build(CaptureReader.Bundle bundle, Legacy legacy) {
        CodexBuilder builder = new CodexBuilder(bundle, legacy == null ? Legacy.empty() : legacy);
        return builder.run();
    }

    private Result run() {
        try {
            loadTables();
        } catch (IOException e) {
            throw new IllegalStateException("解析配置表失败：" + e.getMessage(), e);
        }
        Map<String, Map<String, Object>> cardByNinja = pickCards();
        cardNinjaIds.addAll(cardByNinja.keySet());
        buildExtraFormMap();
        List<CodexDataset.CodexNinja> ninjas = buildNinjas(cardByNinja);
        applyVariantNames(ninjas);
        applyIconFallbacks(ninjas);
        Map<String, List<String>> keywords = buildKeywords();
        CodexDataset dataset = CodexDataset.builder()
                .version(DATASET_VERSION)
                .build(bundle.build())
                .stats(stats(ninjas, skillDict.size()))
                .keywords(keywords)
                .skills(skillDict)
                .ninjas(ninjas)
                .build();
        return new Result(dataset, warnings);
    }

    // ---------------------------------------------------------------- 表加载

    private void loadTables() throws IOException {
        byte[] item1 = bundle.tables().get("item1");
        byte[] item3 = bundle.tables().get("item3");
        if (item1 == null) {
            throw new IOException("抓包缺少 item1.pkg（技能表 / 忍者信息表）");
        }
        if (item3 == null) {
            throw new IOException("抓包缺少 item3.pkg（忍者卡牌表）");
        }
        Map<String, byte[]> archive1 = CfgArchive.unpack(item1);
        Map<String, byte[]> archive3 = CfgArchive.unpack(item3);

        byte[] infoBytes = CfgArchive.find(archive1, "NinjaInfoCFG.cfg");
        byte[] skillBytes = CfgArchive.find(archive1, "SkillCFG.cfg");
        byte[] cardBytes = CfgArchive.find(archive3, "CardItemCFG.cfg");
        if (infoBytes == null || skillBytes == null || cardBytes == null) {
            throw new IOException("抓包缺少 NinjaInfoCFG / SkillCFG / CardItemCFG");
        }

        infos.putAll(load(infoBytes));
        for (Map.Entry<String, Map<String, Object>> entry : infos.entrySet()) {
            ninjaIdByModelId.put(asStr(entry.getValue().get("id")), entry.getKey());
            String name = sanitize(asStr(entry.getValue().get("name")));
            if (isGoodName(name)) {
                ninjaIdsByName.computeIfAbsent(name, k -> new ArrayList<>()).add(entry.getKey());
            }
        }
        skills.putAll(load(skillBytes));
        cards.addAll(load(cardBytes).values());

        // item2 只用来判断「变身/NPC 分身」：真实变身的 battle type=0、noticeUI=true、hpType=0
        byte[] item2 = bundle.tables().get("item2");
        if (item2 != null) {
            byte[] battleBytes = CfgArchive.find(CfgArchive.unpack(item2), "NinjaBattleInfoCFG.cfg");
            if (battleBytes != null) {
                battleInfos.putAll(load(battleBytes));
            }
        }

        byte[] ninjaSkillBytes = bundle.tables().get("NinjaSkillCFG");
        if (ninjaSkillBytes == null) {
            warnings.add("抓包缺少 NinjaSkillCFG.cfg，本次只能生成基础信息");
        } else {
            ninjaSkills.putAll(load(ninjaSkillBytes));
        }

        byte[] upgradeBytes = bundle.tables().get("UpgradeStarAttributeCFG");
        if (upgradeBytes != null) {
            for (Map<String, Object> row : load(upgradeBytes).values()) {
                String ninjaId = asStr(row.get("ninjaId"));
                if (!ninjaId.isEmpty()) {
                    attrTupoStarByNinja.put(ninjaId, (int) asLong(row.get("attrTupoStar")));
                }
            }
        }

        byte[] keyDescBytes = bundle.tables().get("SkillKeyDescCFG");
        if (keyDescBytes != null) {
            keyDescs.addAll(load(keyDescBytes).values());
            for (Map<String, Object> row : keyDescs) {
                String keys = sanitize(asStr(row.get("keys")));
                if (!keys.isEmpty()) {
                    keywordKeys.add(keys);
                }
            }
        }

        byte[] exStateBytes = bundle.tables().get("SkillExStateCFG");
        if (exStateBytes != null) {
            for (Map<String, Object> row : load(exStateBytes).values()) {
                exStateNameById.put(asStr(row.get("id")), sanitize(asStr(row.get("name"))));
            }
        }

        byte[] seasonBytes = bundle.tables().get("SeasonTaskNinjaInfoCFG");
        if (seasonBytes != null) {
            seasonNinjaIds.addAll(load(seasonBytes).keySet());
        }

        for (Map.Entry<String, Map<String, Object>> entry : infos.entrySet()) {
            String series = asStr(entry.getValue().get("ninjaSeries"));
            if (!series.isEmpty()) {
                ninjaIdsBySeries.computeIfAbsent(series, k -> new ArrayList<>()).add(entry.getKey());
            }
        }
    }

    private static Map<String, Map<String, Object>> load(byte[] data) throws IOException {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Amf3Reader.Entry entry : Amf3Reader.parse(data)) {
            result.put(entry.key(), entry.fields());
        }
        return result;
    }

    // ---------------------------------------------------------------- 忍者

    private List<CodexDataset.CodexNinja> buildNinjas(Map<String, Map<String, Object>> cardByNinja) {
        List<CodexDataset.CodexNinja> result = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : cardByNinja.entrySet()) {
            String ninjaId = entry.getKey();
            Map<String, Object> card = entry.getValue();
            Map<String, Object> info = infos.get(ninjaId);
            if (info == null) {
                continue;
            }
            List<Slot> baseSlots = slotsOf(ninjaId, false, false);
            if (baseSlots.isEmpty()) {
                warnings.add("忍者 " + ninjaId + " 没有可用技能槽，已跳过");
                continue;
            }
            int star = starOf(card, info);
            List<CodexDataset.CodexForm> forms = buildForms(ninjaId, info, baseSlots, star);
            nameHints = hintsFrom(baseSlots);
            List<CodexDataset.CodexForm> extraForms = buildExtraForms(ninjaId, star, baseSlots);
            nameHints = Map.of();

            boolean hasBreak = false;
            boolean hasY = false;
            boolean hasL = false;
            for (CodexDataset.CodexForm form : concat(forms, extraForms)) {
                for (CodexDataset.CodexSlot slot : form.getSlots()) {
                    hasBreak |= slot.getBreakSkills() != null && !slot.getBreakSkills().isEmpty();
                    hasY |= slot.getY() != null && !slot.getY().isEmpty();
                    hasL |= slot.getL() != null && !slot.getL().isEmpty();
                }
            }

            result.add(CodexDataset.CodexNinja.builder()
                    .id(ninjaId)
                    .name(ninjaName(card, info))
                    .star(star)
                    .rare(rareOf(ninjaId, star))
                    .portrait("portraits/" + ninjaId + ".png")
                    .head("heads/" + ninjaId + ".png")
                    .breakthroughStar(breakthroughStar(ninjaId, star))
                    .attribute(ATTRIBUTE_BY_PROPERTY.getOrDefault((int) asLong(info.get("property")), ""))
                    .tags(tagsOf(info, ninjaId))
                    .flags(CodexDataset.Flags.builder()
                            .star(forms.size() > 1)
                            .hasBreak(hasBreak)
                            .y(hasY)
                            .l(hasL)
                            .build())
                    .forms(forms)
                    .extraForms(extraForms)
                    .build());
        }
        result.sort((a, b) -> a.getId().compareTo(b.getId()));
        return result;
    }

    private Map<String, Map<String, Object>> pickCards() {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> card : cards) {
            String modelId = asStr(card.get("ninja"));
            if (modelId.isEmpty() || "0".equals(modelId)) {
                continue;
            }
            String ninjaId = ninjaIdByModelId.get(modelId);
            if (ninjaId == null) {
                // 少数卡牌的 ninja 字段指向了不存在的模型（如日向日足），用卡面名字兜底
                ninjaId = resolveByCardName(card);
            }
            if (ninjaId == null) {
                continue;
            }
            Map<String, Object> exists = result.get(ninjaId);
            if (exists == null || nameFromCard(card) != null && nameFromCard(exists) == null) {
                result.put(ninjaId, card);
            }
        }
        return result;
    }

    /** 卡牌的 ninja 字段失效时，用卡面名字在 NinjaInfoCFG 中找唯一同名忍者。 */
    private String resolveByCardName(Map<String, Object> card) {
        String name = nameFromCard(card);
        if (name == null) {
            return null;
        }
        List<String> candidates = ninjaIdsByName.get(name);
        return candidates != null && candidates.size() == 1 ? candidates.get(0) : null;
    }

    private String ninjaName(Map<String, Object> card, Map<String, Object> info) {
        String fromCard = nameFromCard(card);
        if (fromCard != null) {
            return fromCard;
        }
        String name = sanitize(asStr(info.get("name")));
        String title = sanitize(asStr(info.get("title")));
        if (isGoodName(name)) {
            return name + title;
        }
        if (isGoodName(title)) {
            return title;
        }
        return "忍者" + asStr(card.get("ninja"));
    }

    private String nameFromCard(Map<String, Object> card) {
        Matcher matcher = CARD_NAME.matcher(asStr(card.get("desc")));
        if (matcher.find()) {
            String name = matcher.group(1).trim();
            if (isGoodName(name)) {
                return name;
            }
        }
        String name = sanitize(asStr(card.get("name")));
        return isGoodName(name) ? name : null;
    }

    private int starOf(Map<String, Object> card, Map<String, Object> info) {
        int frag = (int) asLong(card.get("cardFragNum"));
        int star = switch (frag) {
            case 5, 2 -> 1;
            case 30, 15 -> 2;
            case 80, 40 -> 3;
            default -> 0;
        };
        if (star == 0) {
            star = (int) asLong(info.get("starLevel"));
        }
        return Math.max(1, Math.min(star, 4));
    }

    /** showPropertys 里的编号 → 官方标签名（去重、保持配置顺序） */
    private static List<String> baseTagsOf(Map<String, Object> info) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (Object raw : asList(info.get("showPropertys"))) {
            String tag = TAG_BY_PROPERTY.get((int) asLong(raw));
            if (tag != null) {
                tags.add(tag);
            }
        }
        return new ArrayList<>(tags);
    }

    /** 官方标签 + 运营标签（赛季忍者来自 SeasonTaskNinjaInfoCFG） */
    private List<String> tagsOf(Map<String, Object> info, String ninjaId) {
        List<String> tags = baseTagsOf(info);
        if (seasonNinjaIds.contains(ninjaId) && !tags.contains("赛季")) {
            tags.add("赛季");
        }
        return tags;
    }

    private int rareOf(String ninjaId, int star) {
        Integer legacyRare = legacy.rare().get(ninjaId);
        if (legacyRare != null) {
            return legacyRare;
        }
        return star >= 3 ? 4 : star == 2 ? 3 : 2;
    }

    private int breakthroughStar(String ninjaId, int star) {
        Integer tupoStar = attrTupoStarByNinja.get(ninjaId);
        if (tupoStar == null) {
            return star;
        }
        return Math.max(star, tupoStar + 2);
    }

    private List<CodexDataset.CodexForm> buildForms(String ninjaId, Map<String, Object> info,
                                                    List<Slot> baseSlots, int star) {
        List<CodexDataset.CodexForm> forms = new ArrayList<>();
        forms.add(CodexDataset.CodexForm.builder()
                .key("base")
                .label(star + "星")
                .star(star)
                .kind("base")
                .slots(toRefs(baseSlots))
                .build());

        List<?> starIds = asList(info.get("starIdArr"));
        String previous = signature(baseSlots);
        for (int i = 1; i < starIds.size(); i++) {
            // 只补比初始星级更高的形态，避免出现「3星忍者 + 2星形态」这种倒挂
            if (i + 1 <= star) {
                continue;
            }
            String starId = asStr(starIds.get(i));
            if (starId.isEmpty() || starId.equals(ninjaId) || !ninjaSkills.containsKey(starId)) {
                continue;
            }
            List<Slot> slots = slotsOf(starId, false, false);
            if (slots.isEmpty()) {
                continue;
            }
            String current = signature(slots);
            if (current.equals(previous)) {
                continue;
            }
            forms.add(CodexDataset.CodexForm.builder()
                    .key("star" + (i + 1))
                    .label((i + 1) + "星")
                    .star(i + 1)
                    .kind("star")
                    .slots(toRefs(slots))
                    .build());
            previous = current;
        }
        return forms;
    }

    private record BodyChoice(String base, String body, double score) {
    }

    /**
     * 从源数据推导「基础忍者 → 变身后形态」。
     *
     * <p>客户端没有显式的变身映射表，只能按形态行的结构特征推导：
     * 无卡、非星级形态、战斗表正常、技能组完整，且满足以下任一条件：
     * 1. ID 恰为基础 ID + 10（主线索，绝大多数变身后都在这个位置）；
     * 2. series=0 且技能键整体 +10 的比例 ≥ 0.5（如水门二阶段 11004472）；
     * 3. filterTxt / description / title 命中「变身 / 状态 / 强化 / 六道」等语义线索。
     *
     * <p>最后按「+10 优先、技能相似度高优先、ID 距离近优先」打分，做全局一对一分配，
     * 避免同一个形态被多个同系列忍者重复挂载。
     */
    private void buildExtraFormMap() {
        List<BodyChoice> choices = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : infos.entrySet()) {
            String body = entry.getKey();
            if (cardNinjaIds.contains(body) || !body.matches("11\\d{6}")) {
                continue;
            }
            Map<String, Object> bodyInfo = entry.getValue();
            Map<String, Object> bodySkills = ninjaSkills.get(body);
            if (bodySkills == null || slotCount(bodySkills) < 3 || !battleFieldsOk(body)) {
                continue;
            }
            String desc = asStr(bodyInfo.get("description"));
            String filter = asStr(bodyInfo.get("filterTxt"));
            String title = asStr(bodyInfo.get("title"));
            String text = desc + "|" + filter + "|" + title;
            if (NPC_ROW.matcher(text).find()) {
                continue;
            }
            // 版本名（樱[长发]、鹿丸[中忍]）是独立形态，不是变身后；
            // 变身后的描述通常是空 / 数字 / 原名 + 强化、突破。
            if (desc.contains("[") && !desc.contains("强化") && !desc.contains("突破")) {
                continue;
            }
            // 「全变身+1技能」「回合末变回」这类行是变身前/变身后技能的 +1/Y 变体，不是独立技能组
            if (filter.contains("变回")) {
                continue;
            }
            boolean zeroSeries = asLong(bodyInfo.get("series")) == 0;
            boolean keyword = TRANSFORM_HINT.matcher(text).find();
            String bodyBattleName = battleName(body);
            List<String> sameSeries = ninjaIdsBySeries.getOrDefault(asStr(bodyInfo.get("ninjaSeries")), List.of());
            for (String base : sameSeries) {
                if (!cardNinjaIds.contains(base) || base.equals(body)) {
                    continue;
                }
                Map<String, Object> baseSkills = ninjaSkills.get(base);
                if (baseSkills == null || starIdsOf(base).contains(body)) {
                    continue;
                }
                String baseBattleName = battleName(base);
                if (!baseBattleName.isEmpty() && !bodyBattleName.equals(baseBattleName)
                        && !bodyBattleName.matches("\\d+")) {
                    continue;
                }
                boolean plus10 = body.equals(String.valueOf(asLong(base) + 10));
                double shift = skillShiftRatio(baseSkills, bodySkills);
                if (!plus10 && !(zeroSeries && shift >= 0.5) && !keyword) {
                    continue;
                }
                long distance = Math.abs(asLong(body) - asLong(base));
                double score = (plus10 ? 1000 : 0) + shift * 100 - distance;
                choices.add(new BodyChoice(base, body, score));
            }
        }

        choices.sort((a, b) -> Double.compare(b.score(), a.score()));
        Set<String> usedBases = new HashSet<>();
        Set<String> usedBodies = new HashSet<>();
        for (BodyChoice choice : choices) {
            if (usedBases.contains(choice.base()) || usedBodies.contains(choice.body())) {
                continue;
            }
            usedBases.add(choice.base());
            usedBodies.add(choice.body());
            extraBodyByBase.put(choice.base(), choice.body());
        }
    }

    private List<CodexDataset.CodexForm> buildExtraForms(String ninjaId, int star, List<Slot> baseSlots) {
        String extraKey = extraBodyByBase.get(ninjaId);
        if (extraKey == null) {
            return List.of();
        }
        List<Slot> slots = slotsOf(extraKey, false, true);
        if (slots.size() < 3) {
            return List.of();
        }
        // 变身后技能与变身前经常是同一个技能的不同阶段，优先按「同名技能」继承图标；
        // 找不到同名时，再按「技能键整体 +10」继承。这样凯[死门]不会把「积」错配成「夜凯」的图标，
        // 水门[新春] / 带土[暴走] 的变身后图标也不会因为 21004487 这类新键缺图而空白。
        Map<String, String> baseIdByName = new HashMap<>();
        Map<String, String> baseIdByRawName = new HashMap<>();
        for (Slot baseSlot : baseSlots) {
            String name = resolveName(baseSlot.initial());
            if (!name.isEmpty()) {
                baseIdByName.putIfAbsent(name, baseSlot.initial());
            }
            Map<String, Object> baseSkill = skills.get(baseSlot.initial());
            if (baseSkill != null) {
                String raw = plain(asStr(baseSkill.get("name")));
                if (!raw.isEmpty() && !raw.matches("\\d+")) {
                    baseIdByRawName.putIfAbsent(raw, baseSlot.initial());
                }
            }
        }
        for (Slot slot : slots) {
            String ownName = resolveName(slot.initial());
            String sameNameBase = baseIdByName.get(ownName);
            if (sameNameBase != null) {
                String icon = iconOfSkill(sameNameBase);
                if (!icon.isEmpty()) {
                    iconOverrides.put(slot.initial(), icon);
                }
                continue;
            }
            // name 字段直接复制了变身前技能名字/描述的情况（如柱间的两条同名被动、带土的追打）
            Map<String, Object> bodySkill = skills.get(slot.initial());
            String rawName = bodySkill == null ? "" : plain(asStr(bodySkill.get("name")));
            String sameRawBase = baseIdByRawName.get(rawName);
            if (sameRawBase != null) {
                String icon = iconOfSkill(sameRawBase);
                if (!icon.isEmpty()) {
                    iconOverrides.putIfAbsent(slot.initial(), icon);
                }
                if (ownName.isEmpty() || !isGoodName(ownName) || isDescriptiveName(ownName)) {
                    String baseName = resolveName(sameRawBase);
                    if (isGoodName(baseName) && (ownName.isEmpty() || baseName.length() < ownName.length())) {
                        nameOverrides.putIfAbsent(slot.initial(), baseName);
                    }
                }
            }
            // 名字无法解析（name 是一整段描述）时，说明它是变身前技能文案的变体，
            // 用 +10 对应的基础技能补名字/图标。
            if (ownName.isEmpty() || !isGoodName(ownName) || isDescriptiveName(ownName)) {
                String baseId = shiftedBaseId(slot.initial());
                if (baseId != null) {
                    String baseName = resolveName(baseId);
                    if (isGoodName(baseName) && (ownName.isEmpty() || baseName.length() < ownName.length())) {
                        nameOverrides.putIfAbsent(slot.initial(), baseName);
                    }
                    String baseIcon = iconOfSkill(baseId);
                    if (!baseIcon.isEmpty()) {
                        iconOverrides.putIfAbsent(slot.initial(), baseIcon);
                    }
                }
            }
        }
        return List.of(CodexDataset.CodexForm.builder()
                .key("extra")
                .label("变身后")
                .star(star)
                .kind("extra")
                .slots(toRefs(withInheritedNormal(slots, baseSlots)))
                .build());
    }

    /**
     * 变身后技能组没有普攻时（如凯[死门]的 11001951 记录里 normals 为空），
     * 沿用变身前的普攻槽位，避免变身后面板少一行。
     */
    private List<Slot> withInheritedNormal(List<Slot> slots, List<Slot> baseSlots) {
        for (Slot slot : slots) {
            if ("普攻".equals(slot.role())) {
                return slots;
            }
        }
        List<Slot> merged = new ArrayList<>(slots);
        for (Slot baseSlot : baseSlots) {
            if ("普攻".equals(baseSlot.role())) {
                merged.add(new Slot(baseSlot.role(), baseSlot.initial(), List.of(), null, null));
                break;
            }
        }
        merged.sort((a, b) -> {
            int byRole = Integer.compare(roleOrder(a.role()), roleOrder(b.role()));
            return byRole != 0 ? byRole : a.initial().compareTo(b.initial());
        });
        return merged;
    }

    /** 技能键整体 +10 对应的变身前技能（如 21006066 → 21006056） */
    private String shiftedBaseId(String id) {
        if (id == null || id.length() != 8) {
            return null;
        }
        int tail = Integer.parseInt(id.substring(6));
        int wanted = tail - 10;
        if (wanted < 0) {
            return null;
        }
        String two = (wanted < 10 ? "0" : "") + wanted;
        String direct = id.substring(0, 6) + two;
        if (skills.containsKey(direct)) {
            return direct;
        }
        // 212..217 变体的基准技能统一是 210
        String normalized = "210" + id.substring(3, 6) + two;
        return skills.containsKey(normalized) ? normalized : null;
    }

    /** 技能图标路径：skills/{iconId}.png */
    private String iconOfSkill(String id) {
        Map<String, Object> skill = skills.get(id);
        if (skill == null) {
            return "";
        }
        String iconId = asStr(skill.get("iconId"));
        return isSkillId(iconId) ? "skills/" + iconId + ".png" : "";
    }

    /** 战斗表判断是否为正常可玩忍者（排除召唤物 / NPC 分身） */
    private boolean battleFieldsOk(String ninjaId) {
        Map<String, Object> battle = battleInfos.get(ninjaId);
        if (battle == null) {
            return true;
        }
        return asLong(battle.get("type")) == 0
                && asBool(battle.get("noticeUI"))
                && asLong(battle.get("hpType")) == 0
                && asLong(battle.get("callupType")) == 0;
    }

    /** starIdArr 递归集合：星级形态不能当作变身后 */
    private Set<String> starIdsOf(String ninjaId) {
        Set<String> ids = new LinkedHashSet<>();
        List<String> pending = new ArrayList<>();
        pending.add(ninjaId);
        for (int i = 0; i < pending.size() && ids.size() < 32; i++) {
            Map<String, Object> row = infos.get(pending.get(i));
            if (row == null) {
                continue;
            }
            for (Object raw : asList(row.get("starIdArr"))) {
                String id = asStr(raw);
                if (isSkillId(id) && ids.add(id)) {
                    pending.add(id);
                }
            }
        }
        return ids;
    }

    /**
     * 变身后技能键整体 +10 的比例：如 21011744 → 21011754。
     * 这是比「描述相似度」更稳定的结构信号，能覆盖技能组完全换新的变身。
     */
    private static double skillShiftRatio(Map<String, Object> base, Map<String, Object> candidate) {
        Set<String> baseIds = skillIds(base);
        Set<String> candidateIds = skillIds(candidate);
        if (baseIds.isEmpty() || candidateIds.isEmpty()) {
            return 0;
        }
        int hit = 0;
        for (String id : candidateIds) {
            int tail = Integer.parseInt(id.substring(id.length() - 2));
            int wanted = tail - 10;
            if (wanted < 0) {
                continue;
            }
            String prefix = id.substring(2, id.length() - 2);
            String wantedId = id.substring(0, 2) + prefix + (wanted < 10 ? "0" : "") + wanted;
            if (baseIds.contains(wantedId)) {
                hit++;
            }
        }
        return (double) hit / candidateIds.size();
    }

    private static Set<String> skillIds(Map<String, Object> record) {
        Set<String> ids = new LinkedHashSet<>();
        for (String key : List.of("specials", "normals", "skills")) {
            for (Object raw : asList(record.get(key))) {
                String id = asStr(raw);
                if (isSkillId(id)) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private static int slotCount(Map<String, Object> record) {
        int count = 0;
        for (String key : List.of("specials", "normals", "skills")) {
            count += asList(record.get(key)).size();
        }
        return count;
    }

    private String battleName(String ninjaId) {
        Map<String, Object> battle = battleInfos.get(ninjaId);
        return battle == null ? "" : asStr(battle.get("name"));
    }

    private static String signature(List<Slot> slots) {
        StringBuilder sb = new StringBuilder();
        for (Slot slot : slots) {
            sb.append(slot.role).append(':').append(slot.initial).append(',');
        }
        return sb.toString();
    }

    private static List<CodexDataset.CodexForm> concat(List<CodexDataset.CodexForm> a,
                                                      List<CodexDataset.CodexForm> b) {
        List<CodexDataset.CodexForm> list = new ArrayList<>(a);
        list.addAll(b);
        return list;
    }

    // ---------------------------------------------------------------- 槽位

    private record Slot(String role, String initial, List<String> breakSkills, String y, String l) {
    }

    private List<Slot> slotsOf(String ninjaId, boolean keepHidden, boolean extraMode) {
        Map<String, Object> record = ninjaSkills.get(ninjaId);
        if (record == null) {
            return List.of();
        }
        List<Slot> slots = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Set<String> baseSkillIds = new HashSet<>();

        List<?> specials = asList(record.get("specials"));
        String specialId = specials.isEmpty() ? "" : asStr(specials.get(0));
        if (!specialId.isEmpty()) {
            baseSkillIds.add(specialId);
            addSlot(slots, seen, specialId, "奥义", "specialItemIds", record, keepHidden, false, extraMode);
        }

        List<?> normals = asList(record.get("normals"));
        if (!normals.isEmpty()) {
            // 客户端把同一个普攻的多条形态（不同连击数/不同状态）都塞进 normals，
            // 技能面板只显示其中一条。打分优先「showIcon=true 的正式条目」，
            // 其次是「有策划命名的条目」，最后用突破材料完整度收尾。
            String chosen = null;
            int bestScore = Integer.MIN_VALUE;
            for (Object raw : normals) {
                String candidate = asStr(raw);
                if (!isSkillId(candidate) || !skills.containsKey(candidate) || isHidden(candidate)) {
                    continue;
                }
                Map<String, Object> skill = skills.get(candidate);
                int score = (asBool(skill.get("showIcon")) ? 4 : 0)
                        + (hasFormalName(candidate) ? 2 : 0)
                        + normalFamilySize(candidate, record);
                if (score > bestScore) {
                    bestScore = score;
                    chosen = candidate;
                }
            }
            if (chosen != null) {
                baseSkillIds.add(chosen);
                // 同一个普攻的多条候选里，只要有一条在 legacy 里有策划命名，就用它
                // （如自来也 21002263 无名字，但同组 21002261 叫「超大玉螺旋丸」）。
                if (formalNameOf(chosen).isEmpty()) {
                    for (Object raw : normals) {
                        String candidate = asStr(raw);
                        String candidateName = formalNameOf(candidate);
                        if (!candidateName.isEmpty()) {
                            nameOverrides.putIfAbsent(chosen, candidateName);
                            break;
                        }
                    }
                }
                addSlot(slots, seen, chosen, "普攻", "normalItemIds", record, keepHidden, false, extraMode);
            }
        }

        List<?> skillIds = asList(record.get("skills"));
        Set<String> canonicalIds = new HashSet<>();
        canonicalIds.add(specialId);
        for (Slot slot : slots) {
            canonicalIds.add(slot.initial());
        }
        for (Object raw : skillIds) {
            String id = asStr(raw);
            if (id.startsWith("210")) {
                baseSkillIds.add(id);
            }
            if (skills.containsKey(id)) {
                canonicalIds.add(id);
            }
        }
        for (int i = 0; i < skillIds.size(); i++) {
            String id = asStr(skillIds.get(i));
            // 212..217 是 210 基础技能的变体；如果基础技能本身就在这组槽位里，
            // 说明它是变体而不是独立技能（如 21202816 与 21002816 都是双魔之攻），跳过。
            if (id.matches("21[2-7]\\d{5}")) {
                String baseId = "210" + id.substring(3);
                if (baseSkillIds.contains(baseId)) {
                    continue;
                }
            }
            // 客户端会把「另一条技能的变体 / 备份」也写进 skills（21302269 是 21002266 的
            // 变体、21003358 是 21003356 的备份），这类不单独占一行。
            if (isVariantOfExistingSlot(id, canonicalIds)) {
                continue;
            }
            addSlot(slots, seen, id, null,
                    "skillItem" + (i + 1) + "Ids", record, keepHidden, true, extraMode);
        }


        slots.sort((a, b) -> {
            int byRole = Integer.compare(roleOrder(a.role()), roleOrder(b.role()));
            return byRole != 0 ? byRole : a.initial().compareTo(b.initial());
        });
        return slots;
    }

    /**
     * skills 数组里混入的「另一条技能的变体 / 备份」：
     * 未在技能面板显示（showIcon=false）且 resId 指向本忍者已存在的另一条技能，
     * 或描述与已存在的槽位完全一致。这类不单独占一行。
     */
    private boolean isVariantOfExistingSlot(String id, Set<String> canonicalIds) {
        Map<String, Object> skill = skills.get(id);
        if (skill == null) {
            return true;
        }
        if (asBool(skill.get("showIcon"))) {
            return false;
        }
        String resId = asStr(skill.get("resId"));
        if (!resId.isEmpty() && !resId.equals(id) && canonicalIds.contains(resId)) {
            return true;
        }
        String desc = plain(asStr(skill.get("desc")));
        if (desc.isEmpty()) {
            return false;
        }
        for (String other : canonicalIds) {
            if (other.equals(id)) {
                continue;
            }
            Map<String, Object> row = skills.get(other);
            if (row != null && desc.equals(plain(asStr(row.get("desc"))))) {
                return true;
            }
        }
        return false;
    }

    /** 是否有策划命名：legacy.json 的命名表，或技能自身 name 是正式名 */
    private boolean hasFormalName(String id) {
        if (isGoodName(legacy.skillNames().get(id))) {
            return true;
        }
        Map<String, Object> skill = skills.get(id);
        if (skill == null) {
            return false;
        }
        String name = sanitize(asStr(skill.get("name")));
        return isGoodName(name) && !isDescriptiveName(name);
    }

    /** 后 4 位序号 → 全部 {@code 21xxxxxx} 技能键 */
    private Map<String, List<String>> idsByTail4() {
        if (idsByTail4Cache == null) {
            Map<String, List<String>> map = new HashMap<>();
            for (String id : skills.keySet()) {
                if (id.length() != 8 || !id.matches("21\\d{6}")) {
                    continue;
                }
                map.computeIfAbsent(id.substring(4), k -> new ArrayList<>()).add(id);
            }
            for (List<String> list : map.values()) {
                list.sort((a, b) -> a.compareTo(b));
            }
            idsByTail4Cache = map;
        }
        return idsByTail4Cache;
    }

    /** 技能描述 → 同描述的技能键（同一技能的多条候选共用描述） */
    private Map<String, List<String>> idsByDesc() {
        if (idsByDescCache == null) {
            Map<String, List<String>> map = new HashMap<>();
            for (String id : skills.keySet()) {
                String desc = plain(asStr(skills.get(id).get("desc")));
                if (desc.length() < 4 || isSkillId(desc) || desc.matches("[\\d;|]+")) {
                    continue;
                }
                map.computeIfAbsent(desc, k -> new ArrayList<>()).add(id);
            }
            idsByDescCache = map;
        }
        return idsByDescCache;
    }

    private void addSlot(List<Slot> slots, Set<String> seen, String id, String forcedRole,
                         String itemKey, Map<String, Object> record, boolean keepHidden,
                         boolean fromSkills, boolean extraMode) {
        if (!isSkillId(id) || !skills.containsKey(id) || !seen.add(id)) {
            return;
        }
        Map<String, Object> skill = skills.get(id);
        // 奥义 / 普攻是固定槽位，不能因为 name 里带「闪避」就丢掉（很多奥义描述本身就写「闪避」）；
        // 只有 skills 数组里的候选才按「附加效果」规则过滤。
        // 变身后是独立的技能组，同名/同名描述的多条技能（如凯[死门]的两条「夜凯」、
        // 佩恩[六道集结]的多条同名被动）都是真实技能，不能按重复项丢弃。
        if (!keepHidden && (extraMode ? isHidden(id) : (fromSkills ? isHiddenSlotCandidate(id) : isHidden(id)))) {
            return;
        }
        // skills 数组里常混入「名字就是另一条技能描述」的重复项（如带土 21006059 与 21006058），
        // 名字/描述与已有槽位完全相同的直接丢弃，避免详情里多出一行同名技能。
        if (fromSkills && !extraMode && isDuplicateSlot(slots, skill)) {
            return;
        }
        String role = forcedRole != null ? forcedRole : roleOf(skill);
        // 游戏内的「技能突破」只针对变身前；变身后技能组只展示技能本身，不生成突破列
        List<String> variants = extraMode ? List.of() : variantsOf(id, itemKey, record);
        Slot slot = classifyVariants(id, role, variants);
        slots.add(slot);
    }

    /**
     * 突破阶梯分类。
     *
     * <p>技能键结构为 {@code 21|家族位|等级位|4位序号}（如 {@code 21001334}、{@code 21021334}）。
     * 同一序号下的技能属于同一套升级阶梯：
     * <ul>
     *   <li>同家族：按等级升序依次是突破 +1、+2…（如 21021334/21031334/21041334/21051334）；</li>
     *   <li>跨家族：2 → 突破 +1，4 → 突破 +2，3 → 异变 Y，5 → 链接 L，
     *       6/7 在 Y/L 为空时补位，9 是废弃数据直接忽略。</li>
     * </ul>
     * 突破列最多 4 个，Y/L 各 1 个。
     */
    private Slot classifyVariants(String base, String role, List<String> variants) {
        List<String> breaks = new ArrayList<>();
        String y = null;
        String l = null;
        if (variants.isEmpty()) {
            return new Slot(role, base, breaks, null, null);
        }

        int baseCode = codeOf(base);
        int baseFamily = baseCode / 10;

        // 同家族：等级升序就是突破顺序
        List<int[]> sameFamily = new ArrayList<>();
        for (String variant : variants) {
            int code = codeOf(variant);
            if (code / 10 == baseFamily) {
                sameFamily.add(new int[]{code % 10, variants.indexOf(variant)});
            }
        }
        sameFamily.sort((a, b) -> Integer.compare(a[0], b[0]));
        List<String> ordered = new ArrayList<>();
        for (int[] item : sameFamily) {
            ordered.add(variants.get(item[1]));
        }
        for (int i = 0; i < ordered.size(); i++) {
            if (i < 4) {
                breaks.add(ordered.get(i));
            } else if (i == 4 && y == null) {
                y = ordered.get(i);
            } else if (i == 5 && l == null) {
                l = ordered.get(i);
            }
        }

        // 跨家族
        for (String variant : variants) {
            if (breaks.contains(variant) || variant.equals(y) || variant.equals(l)) {
                continue;
            }
            int family = codeOf(variant) / 10;
            switch (family) {
                case 2 -> {
                    if (breaks.size() < 4) {
                        breaks.add(variant);
                    }
                }
                case 4 -> {
                    if (breaks.size() < 4) {
                        breaks.add(variant);
                    }
                }
                case 3, 6 -> {
                    if (y == null) {
                        y = variant;
                    }
                }
                case 5, 7 -> {
                    if (l == null) {
                        l = variant;
                    }
                }
                default -> {
                    // 0 = 基础族（已处理）、9 = 废弃数据，忽略
                }
            }
        }
        if (breaks.size() > 4) {
            breaks = new ArrayList<>(breaks.subList(0, 4));
        }
        return new Slot(role, base, breaks, y, l);
    }

    /** 8 位技能键的 2 位「家族+等级」编码 */
    private static int codeOf(String id) {
        if (id == null || id.length() != 8) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(2, 4));
        } catch (NumberFormatException e) {
            return -1;
        }
    }


    private boolean isDuplicateSlot(List<Slot> slots, Map<String, Object> candidate) {
        String rawName = asStr(candidate.get("name")).trim();
        if (rawName.isEmpty()) {
            return false;
        }
        for (Slot slot : slots) {
            Map<String, Object> existing = skills.get(slot.initial());
            if (existing == null) {
                continue;
            }
            String existingName = asStr(existing.get("name")).trim();
            String existingDesc = asStr(existing.get("desc")).trim();
            if (rawName.equals(existingName) || rawName.equals(existingDesc)) {
                return true;
            }
        }
        return false;
    }

    /** normalItemIds 中属于该普攻的变体数量（用于在多个 normals 中挑「资料最完整」的一条） */
    private int normalFamilySize(String base, Map<String, Object> record) {
        String tail4 = base.substring(base.length() - 4);
        Set<String> ids = new HashSet<>();
        for (Object raw : asList(record.get("normalItemIds"))) {
            String id = asStr(raw);
            if (!isSkillId(id) || id.equals(base) || !id.endsWith(tail4)) {
                continue;
            }
            if (id.matches("21\\d{6}") && skills.containsKey(id) && !isHidden(id)) {
                ids.add(id);
            }
        }
        return ids.size();
    }

    private List<String> variantsOf(String base, String itemKey, Map<String, Object> record) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String tail4 = base.substring(base.length() - 4);
        for (Object raw : asList(record.get(itemKey))) {
            String id = asStr(raw);
            if (!isSkillId(id) || id.equals(base) || !id.endsWith(tail4)) {
                continue;
            }
            if (!id.matches("21\\d{6}") || !skills.containsKey(id) || isHidden(id)) {
                continue;
            }
            ids.add(id);
        }
        // 有序表里同一段可能混入别的槽位的同序号变体，统一按技能键升序排列，保证突破顺序稳定。
        List<String> result = new ArrayList<>(ids);
        result.sort((a, b) -> a.compareTo(b));
        if (result.isEmpty()) {
            // 变身后形态的 xxxItemIds 全是 "0;0"，退回按「同 4 位序号」直连查找
            for (String id : idsByTail4().getOrDefault(tail4, List.of())) {
                if (!id.equals(base) && !isHidden(id)) {
                    result.add(id);
                }
            }
            result.sort((a, b) -> a.compareTo(b));
        }
        return result;
    }

    private List<CodexDataset.CodexSlot> toRefs(List<Slot> slots) {
        List<CodexDataset.CodexSlot> refs = new ArrayList<>();
        for (Slot slot : slots) {
            String initial = registerSkill(slot.initial(), slot.role());
            if (initial == null) {
                continue;
            }
            List<String> breaks = new ArrayList<>();
            for (String variant : slot.breakSkills()) {
                String id = registerSkill(variant, slot.role());
                if (id != null) {
                    breaks.add(id);
                }
            }
            String y = slot.y() == null ? null : registerSkill(slot.y(), slot.role());
            String l = slot.l() == null ? null : registerSkill(slot.l(), slot.role());
            refs.add(CodexDataset.CodexSlot.builder()
                    .role(slot.role())
                    .initial(initial)
                    .breakSkills(breaks)
                    .y(y)
                    .l(l)
                    .build());
        }
        return refs;
    }

    // ---------------------------------------------------------------- 技能字典

    /**
     * 突破 / 异变 / 链接技能的命名：客户端这些变体经常只有描述没有名字，
     * 统一继承同一槽位初始技能的名字（游戏里「霸体+1」也叫「霸体」）。
     */
    private void applyVariantNames(List<CodexDataset.CodexNinja> ninjas) {
        for (CodexDataset.CodexNinja ninja : ninjas) {
            for (CodexDataset.CodexForm form : concat(ninja.getForms(), ninja.getExtraForms())) {
                for (CodexDataset.CodexSlot slot : form.getSlots()) {
                    CodexDataset.CodexSkill initial = skillDict.get(slot.getInitial());
                    if (initial == null || isPlaceholderSkillName(initial)) {
                        continue;
                    }
                    List<String> variants = new ArrayList<>();
                    if (slot.getBreakSkills() != null) {
                        variants.addAll(slot.getBreakSkills());
                    }
                    variants.add(slot.getY());
                    variants.add(slot.getL());
                    for (String variantId : variants) {
                        if (variantId == null) {
                            continue;
                        }
                        CodexDataset.CodexSkill variant = skillDict.get(variantId);
                        if (variant != null && isPlaceholderSkillName(variant)) {
                            variant.setName(initial.getName());
                        }
                    }
                }
            }
        }
    }

    private static boolean isPlaceholderSkillName(CodexDataset.CodexSkill skill) {
        String name = skill.getName();
        if (name == null || name.isEmpty()) {
            return true;
        }
        return name.equals(skill.getRole()) || name.matches("\\d+") || isDescriptiveName(name);
    }

    /**
     * 技能图标兜底链。
     *
     * <p>客户端只打包了部分技能图标（例如 21001336 / 21001861 / 21003347 在所有构建下都 404），
     * 因此每个技能额外给出 1~3 个候选图标：resId 图标 → 同 4 位序号的基础族图标 → 同形态奥义图标。
     * 前端按序回退，保证不会出现裂图。
     */
    private void applyIconFallbacks(List<CodexDataset.CodexNinja> ninjas) {
        for (Map.Entry<String, CodexDataset.CodexSkill> entry : skillDict.entrySet()) {
            String id = entry.getKey();
            CodexDataset.CodexSkill skill = entry.getValue();
            LinkedHashSet<String> pool = new LinkedHashSet<>();
            Map<String, Object> raw = skills.get(id);
            if (raw != null) {
                String resId = asStr(raw.get("resId"));
                if (isSkillId(resId) && !resId.equals(id)) {
                    String path = iconOfSkill(resId);
                    if (!path.isEmpty()) {
                        pool.add(path);
                    }
                }
            }
            String tail4 = id.length() >= 4 ? id.substring(id.length() - 4) : id;
            for (String sibling : idsByTail4().getOrDefault(tail4, List.of())) {
                if (codeOf(sibling) / 10 != 0) {
                    continue;
                }
                String path = iconOfSkill(sibling);
                if (!path.isEmpty()) {
                    pool.add(path);
                }
            }
            pool.remove(skill.getIcon());
            List<String> fallbacks = new ArrayList<>(pool);
            if (skill.getIcon() == null || skill.getIcon().isEmpty()) {
                if (!fallbacks.isEmpty()) {
                    skill.setIcon(fallbacks.remove(0));
                }
            }
            skill.setIconFallbacks(fallbacks.isEmpty() ? null : fallbacks);
        }

        // 同忍者其它形态的技能图标作为最后兜底（变身后奥义图标缺失时用变身前奥义）
        for (CodexDataset.CodexNinja ninja : ninjas) {
            List<String> formIcons = new ArrayList<>();
            for (CodexDataset.CodexForm form : concat(ninja.getForms(), ninja.getExtraForms())) {
                for (CodexDataset.CodexSlot slot : form.getSlots()) {
                    CodexDataset.CodexSkill skill = skillDict.get(slot.getInitial());
                    if (skill == null || skill.getIcon() == null || skill.getIcon().isEmpty()) {
                        continue;
                    }
                    if (!formIcons.contains(skill.getIcon())) {
                        formIcons.add(skill.getIcon());
                    }
                }
            }
            if (formIcons.isEmpty()) {
                continue;
            }
            for (CodexDataset.CodexForm form : concat(ninja.getForms(), ninja.getExtraForms())) {
                for (CodexDataset.CodexSlot slot : form.getSlots()) {
                    List<String> slotSkillIds = new ArrayList<>();
                    slotSkillIds.add(slot.getInitial());
                    if (slot.getBreakSkills() != null) {
                        slotSkillIds.addAll(slot.getBreakSkills());
                    }
                    slotSkillIds.add(slot.getY());
                    slotSkillIds.add(slot.getL());
                    for (String skillId : slotSkillIds) {
                        CodexDataset.CodexSkill skill = skillId == null ? null : skillDict.get(skillId);
                        if (skill == null) {
                            continue;
                        }
                        List<String> list = skill.getIconFallbacks() == null
                                ? new ArrayList<>() : new ArrayList<>(skill.getIconFallbacks());
                        for (String icon : formIcons) {
                            if (list.size() >= 3) {
                                break;
                            }
                            if (!icon.equals(skill.getIcon()) && !list.contains(icon)) {
                                list.add(icon);
                            }
                        }
                        if (!list.isEmpty()) {
                            skill.setIconFallbacks(list);
                        }
                    }
                }
            }
        }
    }


    private String registerSkill(String id, String role) {
        if (id == null || !skills.containsKey(id)) {
            return null;
        }
        Map<String, Object> skill = skills.get(id);
        String existingRole = skillRoles.get(id);
        if (existingRole == null || "其他".equals(existingRole)) {
            skillRoles.put(id, role);
        }
        CodexDataset.CodexSkill exists = skillDict.get(id);
        if (exists != null) {
            String overrideName = nameOverrides.get(id);
            String currentName = exists.getName();
            if (overrideName != null && !overrideName.isEmpty() && !overrideName.equals(currentName)) {
                exists.setName(overrideName);
            } else if (currentName == null || currentName.isEmpty()
                    || currentName.equals(exists.getRole()) || !isGoodName(currentName)) {
                String name = resolveName(id);
                if (name.isEmpty()) {
                    name = shortLabel(id);
                }
                if (!name.isEmpty()) {
                    exists.setName(name);
                }
            }
            String override = iconOverrides.get(id);
            if (override != null && !override.isEmpty()) {
                exists.setIcon(override);
            }
            return id;
        }
        String name = nameOverrides.get(id);
        if (name == null || name.isEmpty()) {
            name = resolveName(id);
        }
        if (name.isEmpty()) {
            name = shortLabel(id);
        }
        // 普攻在客户端里经常只写描述（「攻击正面敌人…」），统一按游戏内面板叫「体术攻击」
        if (name.isEmpty() && "普攻".equals(role)) {
            name = "体术攻击";
        }
        String icon = iconOverrides.get(id);
        if (icon == null || icon.isEmpty()) {
            icon = iconOfSkill(id);
        }
        skillDict.put(id, CodexDataset.CodexSkill.builder()
                .id(id)
                .name(name.isEmpty() ? role : name)
                .role(skillRoles.getOrDefault(id, role))
                .desc(resolveDesc(id, 0))
                .cd((int) asLong(skill.get("cd")))
                .enterCd((int) asLong(skill.get("enterCd")))
                .mp((int) asLong(skill.get("mp")))
                .icon(icon)
                .immediately(asLong(skill.get("isImmediately")) != 0)
                .states(statesOf(id))
                .build());
        return id;
    }

    private String roleOf(Map<String, Object> skill) {
        int type = (int) asLong(skill.get("type"));
        String role = ROLE_BY_TYPE.get(type);
        if (role != null) {
            return role;
        }
        // 普攻/追打无法靠 type 区分时，命中状态非空视为追打
        if (type == 0 && !isZeroList(skill.get("beHitState1"))) {
            return "追打";
        }
        return "其他";
    }

    private static int roleOrder(String role) {
        int index = ROLE_ORDER.indexOf(role);
        return index < 0 ? ROLE_ORDER.size() : index;
    }

    private boolean isHidden(String id) {
        Map<String, Object> skill = skills.get(id);
        if (skill == null) {
            return true;
        }
        String desc = plain(asStr(skill.get("desc")));
        return desc.contains("不显示在UI") || desc.contains("空技能占位")
                || "0".equals(desc) || "无".equals(desc);
    }

    /**
     * 基础槽位候选的额外过滤：名字里带「闪避」的通常是空技能 / 附加效果记录
     * （如 21006056 闪避占位、21006066 神威·虚空转移的附加触发），不单独占一行。
     * 注意：变体技能（212..217）的 name 经常是整段描述文本，也会包含「闪避」，
     * 所以这个判断只用于槽位候选，不能用在 variantsOf 里。
     */
    private boolean isHiddenSlotCandidate(String id) {
        if (isHidden(id)) {
            return true;
        }
        String name = asStr(skills.get(id).get("name")).trim();
        return name.contains("闪避");
    }

    private String resolveName(String id) {
        return resolveName(id, 0);
    }

    private String resolveName(String id, int depth) {
        Map<String, Object> skill = skills.get(id);
        if (skill == null || depth > 2) {
            return "";
        }
        String raw = sanitize(asStr(skill.get("name")));
        if (isSkillId(raw)) {
            String referenced = resolveName(raw, depth + 1);
            if (isGoodName(referenced)) {
                return referenced;
            }
        }
        if (isGoodName(raw) && !isDescriptiveName(raw)) {
            return raw;
        }
        // 变身后技能的 name 经常直接复制变身前技能的 desc；
        // 先按「名字文本」查一次描述→技能名，能解析出「神威·虚空转移」「神威·暴走」这类真实名字。
        String rawPlain = plain(raw);
        if (!rawPlain.isEmpty()) {
            String hint = nameHints.get(rawPlain);
            if (isGoodName(hint)) {
                return hint;
            }
        }
        // 有些技能把 name 写成描述、desc 才是指向另一个技能的数字引用（如 21006053 → 21006051）
        String rawDesc = sanitize(asStr(skill.get("desc")));
        if (isSkillId(rawDesc)) {
            String referenced = resolveName(rawDesc.trim(), depth + 1);
            if (isGoodName(referenced)) {
                return referenced;
            }
        }
        String fromLegacy = legacy.skillNames().get(id);
        if (isGoodName(fromLegacy) && !isDescriptiveName(fromLegacy)) {
            return fromLegacy;
        }
        // 客户端把同一个技能的多个形态写成不同键、但共用同一段 desc（如 21001331 / 21001333
        // 都是「攻击正面敌人…」）。同 desc 的任一候选有正式名就直接复用。
        String rawDescKey = plain(asStr(skill.get("desc")));
        String resolvedDescKey = plain(resolveDesc(id, 0));
        for (String key : List.of(resolvedDescKey, rawDescKey)) {
            if (key.length() < 4) {
                continue;
            }
            for (String sibling : idsByDesc().getOrDefault(key, List.of())) {
                // 同一段通用描述（「攻击正面敌人…」）被上百个技能共用，
                // 只允许同一技能家族（前 5 位相同）互相借名，避免把别人的名字抄过来。
                if (sibling.equals(id) || !sameFamilyPrefix(sibling, id)) {
                    continue;
                }
                String siblingName = formalNameOf(sibling);
                if (!siblingName.isEmpty()) {
                    return siblingName;
                }
            }
        }
        // 基础技能本身没有好名字时，借用同一 4 位序号的其他变体名（如 21002816 ← 21202816 双魔之攻）
        String tail4 = id.length() >= 4 ? id.substring(id.length() - 4) : id;
        for (String variantId : idsByTail4().getOrDefault(tail4, List.of())) {
            if (variantId.equals(id)) {
                continue;
            }
            String variantName = formalNameOf(variantId);
            if (!variantName.isEmpty()) {
                return variantName;
            }
        }
        String desc = plain(resolveDesc(id, 0));
        if (!desc.isEmpty()) {
            String hint = nameHints.get(desc);
            if (isGoodName(hint)) {
                return hint;
            }
        }
        return "";
    }

    /** 同一技能家族：技能键前 7 位相同（如 21001331 / 21001333，只差最后一位） */
    private static boolean sameFamilyPrefix(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        int len = Math.min(7, Math.min(a.length(), b.length()));
        return len > 0 && a.regionMatches(0, b, 0, len);
    }

    /** 该技能键的「正式名」：legacy 策划命名优先，其次是客户端 name（排除描述型长文本） */
    private String formalNameOf(String id) {
        String curated = legacy.skillNames().get(id);
        if (isGoodName(curated) && !isDescriptiveName(curated)) {
            return curated;
        }
        Map<String, Object> skill = skills.get(id);
        if (skill == null) {
            return "";
        }
        String name = sanitize(asStr(skill.get("name")));
        if (isGoodName(name) && !isDescriptiveName(name) && !name.matches("\\d+")) {
            return name;
        }
        return "";
    }

    /** 名字缺失时的兜底：取 name/desc 的第一小段，避免出现「触发 / 被动 / 追打」这种占位名 */
    private String shortLabel(String id) {
        Map<String, Object> skill = skills.get(id);
        if (skill == null) {
            return "";
        }
        String text = plain(asStr(skill.get("name")));
        if (text.isEmpty() || text.matches("\\d+")) {
            text = plain(asStr(skill.get("desc")));
        }
        if (text.isEmpty() || text.matches("\\d+")) {
            return "";
        }
        int cut = text.length();
        for (String sep : List.of("，", "。", "；", "：")) {
            int index = text.indexOf(sep);
            if (index > 0 && index < cut) {
                cut = index;
            }
        }
        String label = text.substring(0, Math.min(cut, 18)).trim();
        if (!label.matches(".*[\\u4e00-\\u9fa5].*") || isDescriptiveName(label)) {
            return "";
        }
        return label;
    }

    /** 基础形态「描述 → 技能名」，供变身后新增技能补齐名字 */
    private Map<String, String> hintsFrom(List<Slot> slots) {
        Map<String, String> hints = new HashMap<>();
        for (Slot slot : slots) {
            String name = resolveName(slot.initial());
            String desc = plain(resolveDesc(slot.initial(), 0));
            if (!name.isEmpty() && !desc.isEmpty()) {
                hints.putIfAbsent(desc, name);
            }
        }
        return hints;
    }

    private String resolveDesc(String id, int depth) {
        Map<String, Object> skill = skills.get(id);
        if (skill == null) {
            return "";
        }
        String desc = sanitize(asStr(skill.get("desc")));
        if (isSkillId(desc) && depth < 2) {
            String next = resolveDesc(desc, depth + 1);
            if (!next.isEmpty()) {
                return next;
            }
        }
        return desc;
    }

    // ---------------------------------------------------------------- 词条

    private List<String> statesOf(String id) {
        Map<String, Object> skill = skills.get(id);
        if (skill == null) {
            return List.of();
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        long skillId = asLong(id);

        for (Map<String, Object> row : keyDescs) {
            if (asLong(row.get("skillId")) == skillId) {
                addKeywordName(names, asStr(row.get("keys")));
            }
        }
        Set<Long> buffIds = new HashSet<>();
        for (Object raw : asList(skill.get("buffIds"))) {
            buffIds.add(asLong(raw));
        }
        if (!buffIds.isEmpty()) {
            for (Map<String, Object> row : keyDescs) {
                long buffId = asLong(row.get("buffId"));
                if (buffId != 0 && buffIds.contains(buffId)) {
                    addKeywordName(names, asStr(row.get("keys")));
                }
            }
        }
        for (String tag : fontTags(asStr(skill.get("desc")))) {
            if (keywordKeys.contains(tag)) {
                addKeywordName(names, tag);
            }
        }
        for (Object raw : asList(skill.get("exStates"))) {
            String name = exStateNameById.get(asStr(raw));
            if (name != null && keywordKeys.contains(name)) {
                addKeywordName(names, name);
            }
        }
        return names.stream().limit(6).toList();
    }

    private void addKeywordName(Set<String> names, String raw) {
        String name = sanitize(raw);
        if (name.isEmpty() || GENERIC_KEYWORDS.contains(name) || !keywordKeys.contains(name)) {
            return;
        }
        names.add(name);
    }

    private Map<String, List<String>> buildKeywords() {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        for (Map<String, Object> row : keyDescs) {
            String keys = sanitize(asStr(row.get("keys")));
            String desc = plain(asStr(row.get("desc")));
            if (keys.isEmpty() || desc.isEmpty() || GENERIC_KEYWORDS.contains(keys)) {
                continue;
            }
            List<String> descs = keywords.computeIfAbsent(keys, k -> new ArrayList<>());
            if (!descs.contains(desc) && descs.size() < 4) {
                descs.add(desc);
            }
        }
        return keywords;
    }

    private static List<String> fontTags(String html) {
        List<String> result = new ArrayList<>();
        Matcher matcher = FONT_TAG.matcher(html == null ? "" : html);
        while (matcher.find()) {
            String text = sanitize(matcher.group(1)).trim();
            if (!text.isEmpty() && text.length() <= 12) {
                result.add(text);
            }
        }
        return result;
    }

    // ---------------------------------------------------------------- 统计

    private static CodexDataset.Stats stats(List<CodexDataset.CodexNinja> ninjas, int skillCount) {
        int forms = 0;
        int slots = 0;
        int breakCount = 0;
        int y = 0;
        int l = 0;
        int extra = 0;
        for (CodexDataset.CodexNinja ninja : ninjas) {
            List<CodexDataset.CodexForm> all = new ArrayList<>(ninja.getForms());
            if (ninja.getExtraForms() != null) {
                all.addAll(ninja.getExtraForms());
                extra += ninja.getExtraForms().size();
            }
            forms += all.size();
            for (CodexDataset.CodexForm form : all) {
                slots += form.getSlots().size();
                for (CodexDataset.CodexSlot slot : form.getSlots()) {
                    if (slot.getBreakSkills() != null && !slot.getBreakSkills().isEmpty()) {
                        breakCount++;
                    }
                    if (slot.getY() != null) {
                        y++;
                    }
                    if (slot.getL() != null) {
                        l++;
                    }
                }
            }
        }
        return CodexDataset.Stats.builder()
                .ninjas(ninjas.size())
                .skills(skillCount)
                .forms(forms)
                .slots(slots)
                .breakCount(breakCount)
                .y(y)
                .l(l)
                .extra(extra)
                .build();
    }

    // ---------------------------------------------------------------- 工具

    private static boolean isSkillId(String value) {
        return value != null && value.matches("\\d{8}");
    }

    private static boolean isZeroList(Object value) {
        List<?> list = asList(value);
        if (list.isEmpty()) {
            return true;
        }
        for (Object item : list) {
            if (asLong(item) != 0) {
                return false;
            }
        }
        return true;
    }

    private static List<?> asList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

    private static String asStr(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Double d) {
            return d == Math.rint(d) ? String.valueOf(d.longValue()) : String.valueOf(d);
        }
        return String.valueOf(value).trim();
    }

    private static boolean asBool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.longValue() != 0;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text);
    }

    private static long asLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static boolean isGoodName(String name) {
        if (name == null) {
            return false;
        }
        String text = plain(name);
        if (text.isEmpty()) {
            return false;
        }
        if (text.matches("\\d{8}")) {
            return false;
        }
        if (text.matches("[\\d;|]+")) {
            return false;
        }
        return !text.contains("。") && text.length() <= 18;
    }

    /** 名字像一整段技能描述（客户端里大量 name 字段直接是描述文本），需要优先用同名/同键技能名替换 */
    private static boolean isDescriptiveName(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (name.contains("。") || name.contains("，") || name.contains(",")) {
            return true;
        }
        if (name.length() >= 12) {
            return true;
        }
        return name.matches("^(至少|每次|每回合|战斗开始|此忍者|己方|敌方|对敌|攻击正面|攻击随机|造成|消耗自身|触发).*");
    }

    private static String plain(String html) {
        return sanitize(html).replaceAll("\\s+", " ").trim();
    }

    private static String sanitize(String html) {
        if (html == null) {
            return "";
        }
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&amp;", "&");
        s = s.replaceAll("<[^>]*>", "");
        return s.trim();
    }
}
