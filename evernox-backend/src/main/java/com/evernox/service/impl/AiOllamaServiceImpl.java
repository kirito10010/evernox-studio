package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.ResultCode;
import com.evernox.dto.OllamaFilterOptions;
import com.evernox.dto.OllamaModelDto;
import com.evernox.dto.OllamaModelResponse;
import com.evernox.dto.OllamaSyncStatus;
import com.evernox.dto.OllamaTagCount;
import com.evernox.dto.OllamaTagDto;
import com.evernox.dto.OllamaTagResponse;
import com.evernox.entity.AiOllamaModel;
import com.evernox.entity.AiOllamaTag;
import com.evernox.exception.BusinessException;
import com.evernox.repository.AiOllamaModelRepository;
import com.evernox.repository.AiOllamaTagRepository;
import com.evernox.service.AiOllamaCrawler;
import com.evernox.service.AiOllamaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ollama 模型库服务实现
 *
 * 同步采用「逐语句提交」而非一个大事务：全量抓取可能持续数分钟，
 * 长事务会长时间占用连接与锁；各步骤幂等，中途中断下次同步会自动校正。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AiOllamaServiceImpl implements AiOllamaService {

    private static final int MAX_PAGE_SIZE = 100;

    /** 疑似官网改版/抓取失败的安全闸阈值：抓到的模型数不足库中一半时不做删除 */
    private static final double DELETE_SAFETY_RATIO = 0.5;

    /** 全量同步时每轮最多重新校验多少个模型的变体 */
    private static final int TAG_REVALIDATE_BATCH = 100;

    /** 变体校验周期：超过这个天数未校验的模型进入轮询 */
    private static final int TAG_REVALIDATE_DAYS = 7;

    /** 按关键词补全时允许的关键词最大长度 */
    private static final int MAX_KEYWORD_LENGTH = 100;

    private static final Pattern LEADING_NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private final AiOllamaModelRepository modelRepository;
    private final AiOllamaTagRepository tagRepository;
    private final AiOllamaCrawler crawler;

    private final AtomicBoolean syncing = new AtomicBoolean(false);

    private volatile OllamaSyncStatus status = OllamaSyncStatus.builder()
            .running(false)
            .lastSuccess(false)
            .build();

    // ===== 查询 =====

    @Override
    public IPage<OllamaModelResponse> listModels(String keyword, String category, String vendor, String size,
                                                 Boolean abliterated, String capability, String sort,
                                                 int page, int pageSize) {
        Page<AiOllamaModel> p = new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE));
        LambdaQueryWrapper<AiOllamaModel> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(q -> q.like(AiOllamaModel::getName, kw).or().like(AiOllamaModel::getDescription, kw));
        }
        if (StringUtils.hasText(category)) {
            w.eq(AiOllamaModel::getCategory, category.trim());
        }
        if (StringUtils.hasText(vendor)) {
            w.eq(AiOllamaModel::getVendor, vendor.trim());
        }
        if (abliterated != null) {
            // true = 只看 abliterated；false = 只看标准版本（排除 abliterated）
            w.eq(AiOllamaModel::getIsAbliterated, abliterated ? 1 : 0);
        }
        if (StringUtils.hasText(capability)) {
            w.like(AiOllamaModel::getCapabilities, capability.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(size)) {
            w.apply("FIND_IN_SET({0}, sizes)", size.trim().toLowerCase(Locale.ROOT));
        }
        String sortKey = StringUtils.hasText(sort) ? sort.trim() : "popular";
        switch (sortKey) {
            case "new" -> w.orderByDesc(AiOllamaModel::getSourceUpdatedAt);
            case "size" -> w.orderByAsc(AiOllamaModel::getMinSizeB);
            default -> w.orderByDesc(AiOllamaModel::getPulls);
        }
        w.orderByAsc(AiOllamaModel::getId);

        IPage<AiOllamaModel> raw = modelRepository.selectPage(p, w);
        Page<OllamaModelResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(OllamaModelResponse::from).toList());
        return result;
    }

    @Override
    public OllamaModelResponse getModelDetail(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "模型ID不能为空");
        }
        AiOllamaModel model = modelRepository.selectById(id);
        if (model == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "模型不存在");
        }
        OllamaModelResponse response = OllamaModelResponse.from(model);
        // latest 置顶，其余按体积升序
        List<OllamaTagResponse> tags = tagRepository.selectList(new LambdaQueryWrapper<AiOllamaTag>()
                        .eq(AiOllamaTag::getModelId, id)
                        .orderByDesc(AiOllamaTag::getIsLatest)
                        .orderByAsc(AiOllamaTag::getSizeGb)
                        .orderByAsc(AiOllamaTag::getId))
                .stream()
                .map(OllamaTagResponse::from)
                .toList();
        response.setTags(tags);
        return response;
    }

    @Override
    public OllamaFilterOptions getFilterOptions() {
        List<AiOllamaModel> all = modelRepository.selectList(new LambdaQueryWrapper<AiOllamaModel>()
                .select(AiOllamaModel::getCategory, AiOllamaModel::getVendor,
                        AiOllamaModel::getVendorLabel, AiOllamaModel::getSizes,
                        AiOllamaModel::getCapabilities));

        Map<String, String> labels = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        Map<String, Long> categoryCounts = new HashMap<>();
        Set<String> sizes = new TreeSet<>(sizeComparator());
        Set<String> capabilities = new LinkedHashSet<>();
        for (AiOllamaModel m : all) {
            String category = StringUtils.hasText(m.getCategory()) ? m.getCategory() : "official";
            categoryCounts.merge(category, 1L, Long::sum);
            if (StringUtils.hasText(m.getVendor())) {
                labels.putIfAbsent(m.getVendor(), StringUtils.hasText(m.getVendorLabel()) ? m.getVendorLabel() : m.getVendor());
                counts.merge(m.getVendor(), 1L, Long::sum);
            }
            if (StringUtils.hasText(m.getSizes())) {
                for (String s : m.getSizes().split(",")) {
                    if (StringUtils.hasText(s)) {
                        sizes.add(s.trim());
                    }
                }
            }
            if (StringUtils.hasText(m.getCapabilities())) {
                for (String c : m.getCapabilities().split(",")) {
                    if (StringUtils.hasText(c)) {
                        capabilities.add(c.trim());
                    }
                }
            }
        }

        List<OllamaFilterOptions.VendorOption> vendors = counts.entrySet().stream()
                .map(e -> OllamaFilterOptions.VendorOption.builder()
                        .key(e.getKey())
                        .label(labels.get(e.getKey()))
                        .count(e.getValue())
                        .build())
                .sorted(Comparator.comparing(OllamaFilterOptions.VendorOption::getCount).reversed()
                        .thenComparing(OllamaFilterOptions.VendorOption::getKey))
                .toList();

        List<OllamaFilterOptions.VendorOption> categories = List.of(
                categoryOption("official", "官方", categoryCounts),
                categoryOption("third_party", "第三方", categoryCounts),
                categoryOption("community", "社区", categoryCounts));

        return OllamaFilterOptions.builder()
                .categories(categories)
                .vendors(vendors)
                .sizes(new ArrayList<>(sizes))
                .capabilities(new ArrayList<>(capabilities))
                .build();
    }

    private OllamaFilterOptions.VendorOption categoryOption(String key, String label,
                                                            Map<String, Long> counts) {
        return OllamaFilterOptions.VendorOption.builder()
                .key(key)
                .label(label)
                .count(counts.getOrDefault(key, 0L))
                .build();
    }

    @Override
    public OllamaSyncStatus getSyncStatus() {
        OllamaSyncStatus s = status;
        // memory 里的 lastRunAt 只反映当前进程；服务重启后为 null。
        // 此时回退到库里的 MAX(synced_at)，保证「最近同步」仍显示最后一次落库时间。
        LocalDateTime lastRunAt = s.getLastRunAt() != null
                ? s.getLastRunAt()
                : modelRepository.selectLastSyncedAt();
        return OllamaSyncStatus.builder()
                .running(s.isRunning())
                .lastRunAt(lastRunAt)
                .lastSuccess(s.isLastSuccess())
                .durationMs(s.getDurationMs())
                .modelTotal(modelRepository.selectCount(null))
                .tagTotal(tagRepository.selectCount(null))
                .modelAdded(s.getModelAdded())
                .modelUpdated(s.getModelUpdated())
                .modelRemoved(s.getModelRemoved())
                .tagAdded(s.getTagAdded())
                .tagRemoved(s.getTagRemoved())
                .message(s.getMessage())
                .build();
    }

    @Override
    public boolean isEmpty() {
        Long count = modelRepository.selectCount(null);
        return count == null || count == 0;
    }

    @Override
    public void triggerSyncAsync() {
        if (syncing.get()) {
            return;
        }
        CompletableFuture.runAsync(this::syncAll);
    }

    // ===== 同步 =====

    @Override
    public OllamaSyncStatus syncAll() {
        return runSync("全量同步",
                "未抓取到任何模型（官网不可达或页面结构变化），已跳过全部删除",
                crawler::fetchAllModels,
                true);
    }

    @Override
    public void triggerBackfillAsync(String keyword) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "关键词不能为空");
        }
        if (kw.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "关键词过长（最多 " + MAX_KEYWORD_LENGTH + " 个字符）");
        }
        if (syncing.get()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "已有同步任务在执行，请稍后再试");
        }
        CompletableFuture.runAsync(() -> backfillByKeyword(kw));
    }

    /**
     * 按关键词从官网补全（只增不删）。
     * 与全量同步共用 {@link #runSync}，差异只有两处：数据源是单个查询、且不做任何删除。
     */
    private OllamaSyncStatus backfillByKeyword(String keyword) {
        return runSync("按关键词「" + keyword + "」补全",
                "官网也没有匹配「" + keyword + "」的结果",
                () -> crawler.fetchByKeyword(keyword, AiOllamaCrawler.ADHOC_PAGE_LIMIT),
                false);
    }

    /**
     * 同步主流程：取数 → 逐模型 upsert → （可选）删除不在本次结果里的模型 → 同步变体。
     *
     * 逐语句提交、不用 @Transactional：整个流程可能跨越数十次网络请求，
     * 长事务会长时间占用连接与锁；各步骤幂等，中途中断下次同步会自动校正。
     *
     * @param scopeLabel   状态摘要前缀
     * @param emptyMessage 抓取结果为空时的提示
     * @param loader       数据来源（全量抓取 / 按关键词抓取）
     * @param deleteMissing true = 删除不在本次结果里的模型（仅全量同步）；
     *                      false = 只增不删。按关键词补全**必须**传 false——
     *                      一个窄关键词只回几条结果，若走进删除阶段，会把库中其它模型整批删掉。
     */
    private OllamaSyncStatus runSync(String scopeLabel,
                                     String emptyMessage,
                                     Supplier<AiOllamaCrawler.ScrapeOutcome> loader,
                                     boolean deleteMissing) {
        if (!syncing.compareAndSet(false, true)) {
            // 已有同步在跑，直接返回当前状态
            return getSyncStatus();
        }
        long start = System.currentTimeMillis();
        OllamaSyncStatus st = OllamaSyncStatus.builder()
                .running(true)
                .lastRunAt(LocalDateTime.now())
                .lastSuccess(false)
                .build();
        status = st;
        try {
            AiOllamaCrawler.ScrapeOutcome outcome = loader.get();
            List<OllamaModelDto> scraped = outcome.models();
            if (scraped.isEmpty()) {
                st.setMessage(emptyMessage);
                log.warn("Ollama 模型同步：抓取结果为空（{}），跳过本次写入", scopeLabel);
                return st;
            }

            Map<String, AiOllamaModel> existing = modelRepository.selectList(null).stream()
                    .collect(Collectors.toMap(m -> modelKey(m.getName()), Function.identity(), (a, b) -> a));
            // 兼容旧库：schema 回填可能未执行，按名称重新推导一次分类
            for (AiOllamaModel m : existing.values()) {
                String expected = crawler.classifyCategory(m.getName(), null);
                if (!expected.equals(m.getCategory())) {
                    m.setCategory(expected);
                    modelRepository.updateById(m);
                }
            }
            Map<Long, AiOllamaModel> modelById = existing.values().stream()
                    .collect(Collectors.toMap(AiOllamaModel::getId, Function.identity(), (a, b) -> a));
            Map<Long, Long> tagCounts = countTagsByModel();

            LocalDateTime now = LocalDateTime.now();
            Set<Long> needsTags = new LinkedHashSet<>();
            int modelAdded = 0;
            int modelUpdated = 0;
            for (OllamaModelDto dto : scraped) {
                String key = modelKey(dto.getName());
                if (key.isEmpty()) {
                    continue;
                }
                AiOllamaModel old = existing.get(key);
                if (old == null) {
                    AiOllamaModel entity = toModelEntity(dto, now);
                    modelRepository.insert(entity);
                    existing.put(key, entity);
                    modelById.put(entity.getId(), entity);
                    modelAdded++;
                    needsTags.add(entity.getId());
                } else {
                    // 列表页已不再提供变体数与更新时间：这里只兜底「库中完全没有变体」的模型，
                    // 其余模型由全量同步的分批轮询（每轮 100 个 / 7 天）负责校验。
                    long actualTags = tagCounts.getOrDefault(old.getId(), 0L);
                    boolean changed = applyModelChanges(old, dto, now);
                    if (changed) {
                        modelRepository.updateById(old);
                        modelUpdated++;
                    }
                    if (actualTags == 0L) {
                        needsTags.add(old.getId());
                    }
                }
            }

            // 删除阶段：仅全量同步执行；只处理官方模型，社区/第三方模型永远不删
            int modelRemoved = 0;
            List<String> notes = new ArrayList<>();
            long officialExisting = existing.values().stream()
                    .filter(m -> isOfficialModel(m.getName()))
                    .count();
            if (deleteMissing) {
                boolean officialComplete = outcome.officialComplete();
                long officialScraped = scraped.stream()
                        .filter(d -> "official".equals(d.getCategory()))
                        .count();
                boolean ratioOk = officialScraped >= officialExisting * DELETE_SAFETY_RATIO;
                if (officialComplete && ratioOk) {
                    Set<String> scrapedOfficialKeys = scraped.stream()
                            .filter(d -> "official".equals(d.getCategory()))
                            .map(d -> modelKey(d.getName()))
                            .collect(Collectors.toSet());
                    for (AiOllamaModel old : existing.values()) {
                        // 社区/第三方模型无法通过官网完整枚举，绝不能按本次结果删除
                        if (!isOfficialModel(old.getName())) {
                            continue;
                        }
                        if (!scrapedOfficialKeys.contains(modelKey(old.getName()))) {
                            tagRepository.delete(new LambdaQueryWrapper<AiOllamaTag>().eq(AiOllamaTag::getModelId, old.getId()));
                            modelRepository.deleteById(old.getId());
                            modelById.remove(old.getId());
                            modelRemoved++;
                        }
                    }
                } else {
                    notes.add("官方库抓取不完整（complete=" + officialComplete + "，抓取 " + officialScraped
                            + " / 库中 " + officialExisting + "），已跳过删除阶段");
                    log.warn("Ollama 模型同步：{}", notes.get(notes.size() - 1));
                }
            } else if (!outcome.complete()) {
                // 只增不删的路径（按需补全）：如实告知可能没抓全，避免用户误以为已收录完整
                notes.add("本次未抓完（已达页数上限或抓取中断），官网可能还有更多匹配未收录");
            }

            // 新版列表页不再提供「变体数 / 更新时间」，改为分批轮询校验：
            // 优先校验从未抓过变体、或超过 7 天未校验的模型，每轮最多 100 个。
            // 放在删除之后，避免为刚被删除的模型浪费一次请求。
            if (deleteMissing) {
                LocalDateTime revalidateBefore = now.minusDays(TAG_REVALIDATE_DAYS);
                existing.values().stream()
                        .filter(m -> m.getId() != null)
                        .filter(m -> modelById.containsKey(m.getId()))
                        .filter(m -> m.getTagsSyncedAt() == null
                                || m.getTagsSyncedAt().isBefore(revalidateBefore))
                        .sorted(Comparator.comparing(AiOllamaModel::getTagsSyncedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                        .limit(TAG_REVALIDATE_BATCH)
                        .forEach(m -> needsTags.add(m.getId()));
            }

            // 变体同步：只处理官网更新时间变化、或库中完全没有变体的模型
            int tagAdded = 0;
            int tagRemoved = 0;
            int tagFetchFailed = 0;
            for (Long modelId : needsTags) {
                AiOllamaModel model = modelById.get(modelId);
                if (model == null) {
                    continue;
                }
                List<OllamaTagDto> tagDtos = crawler.fetchTags(model.getUrl());
                if (tagDtos.isEmpty()) {
                    // 抓取失败或无变体：保留旧数据，下轮再试
                    tagFetchFailed++;
                    continue;
                }
                int[] diff = replaceTags(model, tagDtos, now);
                tagAdded += diff[0];
                tagRemoved += diff[1];
                sleepQuietly();
            }

            // 单条 SQL 刷新全部模型的最近同步时间，避免为「无变化」的模型执行 2000+ 次单行 UPDATE
            modelRepository.touchAll(now);

            if (tagFetchFailed > 0) {
                notes.add(tagFetchFailed + " 个模型的变体抓取失败，已保留旧数据，下轮重试");
            }

            st.setLastSuccess(true);
            st.setModelAdded(modelAdded);
            st.setModelUpdated(modelUpdated);
            st.setModelRemoved(modelRemoved);
            st.setTagAdded(tagAdded);
            st.setTagRemoved(tagRemoved);
            String summary = scopeLabel + "完成：模型 +" + modelAdded + " ~" + modelUpdated
                    + (deleteMissing ? " -" + modelRemoved : "")
                    + "，变体 +" + tagAdded + " -" + tagRemoved
                    + "，本轮校验变体 " + needsTags.size() + " 个"
                    + "，库中共 " + modelRepository.selectCount(null) + " 行";
            if (!notes.isEmpty()) {
                summary = summary + "；" + String.join("；", notes);
            }
            st.setMessage(summary);
            log.info("Ollama 模型库{}", summary);
            return st;
        } catch (Exception e) {
            st.setLastSuccess(false);
            st.setMessage("同步失败：" + e.getMessage());
            log.error("Ollama 模型库同步失败", e);
            return st;
        } finally {
            st.setRunning(false);
            st.setDurationMs(System.currentTimeMillis() - start);
            syncing.set(false);
        }
    }

    /**
     * 用采集结果替换某模型的变体集合。
     * 返回 [新增数, 删除数]；并按变体名解析结果回写模型的参数量集合。
     */
    private int[] replaceTags(AiOllamaModel model, List<OllamaTagDto> dtos, LocalDateTime now) {
        Map<String, AiOllamaTag> existingTags = tagRepository.selectList(
                        new LambdaQueryWrapper<AiOllamaTag>().eq(AiOllamaTag::getModelId, model.getId()))
                .stream()
                .collect(Collectors.toMap(AiOllamaTag::getName, Function.identity(), (a, b) -> a));

        int added = 0;
        int removed = 0;
        Set<String> seen = new HashSet<>();
        Set<String> paramSizes = new TreeSet<>(sizeComparator());
        for (OllamaTagDto dto : dtos) {
            seen.add(dto.getName());
            AiOllamaTag exist = existingTags.get(dto.getName());
            if (exist == null) {
                tagRepository.insert(toTagEntity(model.getId(), dto, now));
                added++;
            } else if (tagChanged(exist, dto)) {
                copyTagFields(exist, dto);
                tagRepository.updateById(exist);
            }
            if (StringUtils.hasText(dto.getParamSize())) {
                paramSizes.add(dto.getParamSize());
            }
        }
        for (AiOllamaTag exist : existingTags.values()) {
            if (!seen.contains(exist.getName())) {
                tagRepository.deleteById(exist.getId());
                removed++;
            }
        }

        // 参数量集合以变体解析结果为准；新版变体名通常不再带参数量（如 q4_K_M），
        // 解析为空时保留列表页徽标得到的 sizes，避免把已有参数量清空。
        if (!paramSizes.isEmpty()) {
            List<String> sizeList = new ArrayList<>(paramSizes);
            model.setSizes(String.join(",", sizeList));
            List<Double> numeric = numericSizes(sizeList);
            model.setMinSizeB(numeric.isEmpty() ? null : numeric.get(0));
            model.setMaxSizeB(numeric.isEmpty() ? null : numeric.get(numeric.size() - 1));
        }
        // 新版列表页不再提供变体数：以本次实际抓取结果为准
        model.setTagCount(dtos.size());
        model.setTagsSyncedAt(now);
        model.setSyncedAt(now);
        modelRepository.updateById(model);
        return new int[]{added, removed};
    }

    private boolean tagChanged(AiOllamaTag exist, OllamaTagDto dto) {
        return !Objects.equals(exist.getShortName(), dto.getShortName())
                || !Objects.equals(exist.getDigest(), dto.getDigest())
                || !Objects.equals(exist.getSizeText(), dto.getSizeText())
                || !Objects.equals(exist.getSizeGb(), dto.getSizeGb())
                || !Objects.equals(exist.getContextText(), dto.getContextText())
                || !Objects.equals(exist.getContextTokens(), dto.getContextTokens())
                || !Objects.equals(exist.getInputs(), dto.getInputs())
                || !Objects.equals(exist.getIsLatest(), dto.getIsLatest())
                || !Objects.equals(exist.getIsMlx(), dto.getIsMlx())
                || !Objects.equals(exist.getIsAbliterated(), dto.getIsAbliterated())
                || !Objects.equals(exist.getParamSize(), dto.getParamSize())
                || !Objects.equals(exist.getCommand(), dto.getCommand())
                || !Objects.equals(exist.getSourceUpdatedText(), dto.getSourceUpdatedText());
    }

    private void copyTagFields(AiOllamaTag exist, OllamaTagDto dto) {
        exist.setShortName(dto.getShortName());
        exist.setDigest(dto.getDigest());
        exist.setSizeText(dto.getSizeText());
        exist.setSizeGb(dto.getSizeGb());
        exist.setContextText(dto.getContextText());
        exist.setContextTokens(dto.getContextTokens());
        exist.setInputs(dto.getInputs());
        exist.setIsLatest(dto.getIsLatest());
        exist.setIsMlx(dto.getIsMlx());
        exist.setIsAbliterated(dto.getIsAbliterated());
        exist.setParamSize(dto.getParamSize());
        exist.setCommand(dto.getCommand());
        exist.setSourceUpdatedText(dto.getSourceUpdatedText());
    }

    private AiOllamaTag toTagEntity(Long modelId, OllamaTagDto dto, LocalDateTime now) {
        return AiOllamaTag.builder()
                .modelId(modelId)
                .name(dto.getName())
                .shortName(dto.getShortName())
                .digest(dto.getDigest())
                .sizeText(dto.getSizeText())
                .sizeGb(dto.getSizeGb())
                .contextText(dto.getContextText())
                .contextTokens(dto.getContextTokens())
                .inputs(dto.getInputs())
                .isLatest(dto.getIsLatest())
                .isMlx(dto.getIsMlx())
                .isAbliterated(dto.getIsAbliterated())
                .paramSize(dto.getParamSize())
                .command(dto.getCommand())
                .sourceUpdatedText(dto.getSourceUpdatedText())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private AiOllamaModel toModelEntity(OllamaModelDto dto, LocalDateTime now) {
        List<String> sizes = dto.getSizes() == null ? List.of() : dto.getSizes();
        List<String> sortedSizes = sizes.stream()
                .sorted(Comparator.comparingDouble(AiOllamaServiceImpl::sizeValue))
                .toList();
        List<Double> numeric = numericSizes(sortedSizes);
        List<String> capabilities = dto.getCapabilities() == null ? List.of() : dto.getCapabilities();
        return AiOllamaModel.builder()
                .name(dto.getName())
                .namespace(dto.getNamespace())
                .baseName(dto.getBaseName())
                .url(dto.getUrl())
                .description(dto.getDescription())
                .vendor(dto.getVendor())
                .vendorLabel(dto.getVendorLabel())
                .category(dto.getCategory())
                .pulls(dto.getPulls() == null ? 0L : dto.getPulls())
                .pullsText(dto.getPullsText())
                .tagCount(dto.getTagCount() == null ? 0 : dto.getTagCount())
                .capabilities(joinOrNull(capabilities))
                .isVision(capabilities.contains("vision") ? 1 : 0)
                .isTools(capabilities.contains("tools") ? 1 : 0)
                .isThinking(capabilities.contains("thinking") ? 1 : 0)
                .isEmbedding(capabilities.contains("embedding") ? 1 : 0)
                .isAbliterated(dto.getIsAbliterated() == null ? 0 : dto.getIsAbliterated())
                .sizes(sortedSizes.isEmpty() ? null : String.join(",", sortedSizes))
                .minSizeB(numeric.isEmpty() ? null : numeric.get(0))
                .maxSizeB(numeric.isEmpty() ? null : numeric.get(numeric.size() - 1))
                .defaultCommand("ollama run " + dto.getName())
                .sourceUpdatedAt(dto.getSourceUpdatedAt())
                .syncedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** 用采集结果刷新模型字段；返回是否发生了「实质变化」（不含同步时间） */
    private boolean applyModelChanges(AiOllamaModel old, OllamaModelDto dto, LocalDateTime now) {
        boolean changed = false;
        List<String> capabilities = dto.getCapabilities() == null ? List.of() : dto.getCapabilities();
        List<String> sizes = dto.getSizes() == null ? List.of() : dto.getSizes();
        List<String> sortedSizes = sizes.stream()
                .sorted(Comparator.comparingDouble(AiOllamaServiceImpl::sizeValue))
                .toList();
        List<Double> numeric = numericSizes(sortedSizes);

        changed |= differ(old.getNamespace(), dto.getNamespace(), old::setNamespace);
        changed |= differ(old.getBaseName(), dto.getBaseName(), old::setBaseName);
        changed |= differ(old.getUrl(), dto.getUrl(), old::setUrl);
        changed |= differ(old.getDescription(), dto.getDescription(), old::setDescription);
        changed |= differ(old.getVendor(), dto.getVendor(), old::setVendor);
        changed |= differ(old.getVendorLabel(), dto.getVendorLabel(), old::setVendorLabel);
        if (dto.getCategory() != null) {
            changed |= differ(old.getCategory(), dto.getCategory(), old::setCategory);
        }
        changed |= differ(old.getPulls(), dto.getPulls(), old::setPulls);
        changed |= differ(old.getPullsText(), dto.getPullsText(), old::setPullsText);
        // 列表页已不再提供变体数与更新时间：null 表示「未解析到」，不能覆盖库中旧值
        if (dto.getTagCount() != null) {
            changed |= differ(old.getTagCount(), dto.getTagCount(), old::setTagCount);
        }
        if (dto.getSourceUpdatedAt() != null) {
            changed |= differ(old.getSourceUpdatedAt(), dto.getSourceUpdatedAt(), old::setSourceUpdatedAt);
        }
        changed |= differ(old.getIsAbliterated(), dto.getIsAbliterated(), old::setIsAbliterated);

        // 能力/尺寸解析为空时保留旧值，避免官网再次改版把已有数据清空
        if (!capabilities.isEmpty()) {
            changed |= differ(old.getCapabilities(), joinOrNull(capabilities), old::setCapabilities);
            changed |= differ(old.getIsVision(), capabilities.contains("vision") ? 1 : 0, old::setIsVision);
            changed |= differ(old.getIsTools(), capabilities.contains("tools") ? 1 : 0, old::setIsTools);
            changed |= differ(old.getIsThinking(), capabilities.contains("thinking") ? 1 : 0, old::setIsThinking);
            changed |= differ(old.getIsEmbedding(), capabilities.contains("embedding") ? 1 : 0, old::setIsEmbedding);
        }

        // 参数量先用列表页徽标兜底，随后若抓到变体会以变体解析结果覆盖
        if (!sortedSizes.isEmpty()) {
            String sizesText = String.join(",", sortedSizes);
            changed |= differ(old.getSizes(), sizesText, old::setSizes);
            Double minSize = numeric.isEmpty() ? null : numeric.get(0);
            Double maxSize = numeric.isEmpty() ? null : numeric.get(numeric.size() - 1);
            changed |= differ(old.getMinSizeB(), minSize, old::setMinSizeB);
            changed |= differ(old.getMaxSizeB(), maxSize, old::setMaxSizeB);
        }

        old.setSyncedAt(now);
        return changed;
    }

    private <T> boolean differ(T current, T incoming, Consumer<T> setter) {
        if (Objects.equals(current, incoming)) {
            return false;
        }
        setter.accept(incoming);
        return true;
    }

    /** 各模型已入库的变体数量 */
    private Map<Long, Long> countTagsByModel() {
        Map<Long, Long> counts = new HashMap<>();
        for (OllamaTagCount row : tagRepository.countByModel()) {
            if (row.getModelId() != null) {
                counts.put(row.getModelId(), row.getTotal() == null ? 0L : row.getTotal());
            }
        }
        return counts;
    }

    /**
     * 模型名比对 key。
     * MySQL 唯一索引是 utf8mb4_unicode_ci（不区分大小写、忽略尾部空格），
     * Java 侧必须用同样的规则，否则官网大小写变化会被误判为新模型并触发唯一键冲突。
     */
    private static String modelKey(String name) {
        return name == null ? "" : name.strip().toLowerCase(Locale.ROOT);
    }

    /** 官方库模型没有命名空间（名称不含 /） */
    private static boolean isOfficialModel(String name) {
        return name != null && name.indexOf('/') < 0;
    }

    private String joinOrNull(List<String> values) {
        List<String> nonBlank = values.stream().filter(StringUtils::hasText).toList();
        return nonBlank.isEmpty() ? null : String.join(",", nonBlank);
    }

    /** 参数量排序：按数值升序，同数值时按字面量兜底，避免 TreeSet 误判重复 */
    private static Comparator<String> sizeComparator() {
        return Comparator.comparingDouble(AiOllamaServiceImpl::sizeValue)
                .thenComparing(Comparator.naturalOrder());
    }

    /** 解析形如 27b / 128b 的参数量为数值，用于排序与最大最小值 */
    private static double sizeValue(String size) {
        if (size == null) {
            return Double.MAX_VALUE;
        }
        Matcher m = LEADING_NUMBER.matcher(size);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group());
            } catch (NumberFormatException ignored) {
                // 落到兜底值
            }
        }
        return Double.MAX_VALUE;
    }

    /** 从参数量字符串列表解析出数值列表（保持传入顺序） */
    private static List<Double> numericSizes(List<String> sizes) {
        List<Double> result = new ArrayList<>();
        for (String s : sizes) {
            Matcher m = LEADING_NUMBER.matcher(s == null ? "" : s);
            if (m.find()) {
                try {
                    result.add(Double.parseDouble(m.group()));
                } catch (NumberFormatException ignored) {
                    // 跳过不可解析项
                }
            }
        }
        return result;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(300L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
