package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.ResultCode;
import com.evernox.dto.OllamaFilterOptions;
import com.evernox.dto.OllamaModelDto;
import com.evernox.dto.OllamaModelResponse;
import com.evernox.dto.OllamaSyncStatus;
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
    public IPage<OllamaModelResponse> listModels(String keyword, String vendor, String size, Boolean abliterated,
                                                 String capability, String sort, int page, int pageSize) {
        Page<AiOllamaModel> p = new Page<>(Math.max(page, 1), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE));
        LambdaQueryWrapper<AiOllamaModel> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            w.and(q -> q.like(AiOllamaModel::getName, kw).or().like(AiOllamaModel::getDescription, kw));
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
                .select(AiOllamaModel::getVendor, AiOllamaModel::getVendorLabel,
                        AiOllamaModel::getSizes, AiOllamaModel::getCapabilities));

        Map<String, String> labels = new HashMap<>();
        Map<String, Long> counts = new HashMap<>();
        Set<String> sizes = new TreeSet<>(sizeComparator());
        Set<String> capabilities = new LinkedHashSet<>();
        for (AiOllamaModel m : all) {
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

        return OllamaFilterOptions.builder()
                .vendors(vendors)
                .sizes(new ArrayList<>(sizes))
                .capabilities(new ArrayList<>(capabilities))
                .build();
    }

    @Override
    public OllamaSyncStatus getSyncStatus() {
        OllamaSyncStatus s = status;
        return OllamaSyncStatus.builder()
                .running(s.isRunning())
                .lastRunAt(s.getLastRunAt())
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
                    .collect(Collectors.toMap(AiOllamaModel::getName, Function.identity(), (a, b) -> a));
            Map<Long, Long> tagCounts = countTagsByModel();

            LocalDateTime now = LocalDateTime.now();
            Set<Long> needsTags = new LinkedHashSet<>();
            int modelAdded = 0;
            int modelUpdated = 0;
            for (OllamaModelDto dto : scraped) {
                AiOllamaModel old = existing.get(dto.getName());
                if (old == null) {
                    AiOllamaModel entity = toModelEntity(dto, now);
                    modelRepository.insert(entity);
                    modelAdded++;
                    needsTags.add(entity.getId());
                } else {
                    // 注意：tagStale 必须在 applyModelChanges 之前判断（后者会覆盖 tagCount）
                    boolean tagStale = !Objects.equals(old.getTagCount(), dto.getTagCount())
                            || !Objects.equals(old.getSourceUpdatedAt(), dto.getSourceUpdatedAt())
                            || tagCounts.getOrDefault(old.getId(), 0L) == 0L;
                    boolean changed = applyModelChanges(old, dto, now);
                    if (changed) {
                        modelUpdated++;
                    }
                    modelRepository.updateById(old);
                    if (tagStale) {
                        needsTags.add(old.getId());
                    }
                }
            }

            // 删除阶段：仅全量同步执行（deleteMissing=false 时整体跳过，连比例安全闸都不计算）
            int modelRemoved = 0;
            List<String> notes = new ArrayList<>();
            if (deleteMissing) {
                boolean scrapedFull = outcome.complete();
                boolean ratioOk = scraped.size() >= existing.size() * DELETE_SAFETY_RATIO;
                if (scrapedFull && ratioOk) {
                    Set<String> scrapedNames = scraped.stream().map(OllamaModelDto::getName).collect(Collectors.toSet());
                    for (AiOllamaModel old : existing.values()) {
                        if (!scrapedNames.contains(old.getName())) {
                            tagRepository.delete(new LambdaQueryWrapper<AiOllamaTag>().eq(AiOllamaTag::getModelId, old.getId()));
                            modelRepository.deleteById(old.getId());
                            modelRemoved++;
                        }
                    }
                } else {
                    notes.add("本次抓取不完整（complete=" + scrapedFull + "，抓取 " + scraped.size()
                            + " / 库中 " + existing.size() + "），已跳过删除阶段");
                    log.warn("Ollama 模型同步：{}", notes.get(notes.size() - 1));
                }
            } else if (!outcome.complete()) {
                // 只增不删的路径（按需补全）：如实告知可能没抓全，避免用户误以为已收录完整
                notes.add("本次未抓完（已达页数上限或抓取中断），官网可能还有更多匹配未收录");
            }

            // 变体同步：只处理有变化的模型
            int tagAdded = 0;
            int tagRemoved = 0;
            for (Long modelId : needsTags) {
                AiOllamaModel model = modelRepository.selectById(modelId);
                if (model == null) {
                    continue;
                }
                List<OllamaTagDto> tagDtos = crawler.fetchTags(model.getUrl());
                if (tagDtos.isEmpty()) {
                    // 抓取失败或无变体：保留旧数据，下轮再试
                    continue;
                }
                int[] diff = replaceTags(model, tagDtos, now);
                tagAdded += diff[0];
                tagRemoved += diff[1];
                sleepQuietly();
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

        // 参数量集合以变体解析结果为准（比列表页徽标更完整）
        List<String> sizeList = new ArrayList<>(paramSizes);
        model.setSizes(sizeList.isEmpty() ? null : String.join(",", sizeList));
        List<Double> numeric = numericSizes(sizeList);
        model.setMinSizeB(numeric.isEmpty() ? null : numeric.get(0));
        model.setMaxSizeB(numeric.isEmpty() ? null : numeric.get(numeric.size() - 1));
        model.setTagCount(dtos.size());
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
        changed |= differ(old.getPulls(), dto.getPulls(), old::setPulls);
        changed |= differ(old.getPullsText(), dto.getPullsText(), old::setPullsText);
        changed |= differ(old.getTagCount(), dto.getTagCount(), old::setTagCount);
        changed |= differ(old.getCapabilities(), joinOrNull(capabilities), old::setCapabilities);
        changed |= differ(old.getIsAbliterated(), dto.getIsAbliterated(), old::setIsAbliterated);
        changed |= differ(old.getSourceUpdatedAt(), dto.getSourceUpdatedAt(), old::setSourceUpdatedAt);
        changed |= differ(old.getIsVision(), capabilities.contains("vision") ? 1 : 0, old::setIsVision);
        changed |= differ(old.getIsTools(), capabilities.contains("tools") ? 1 : 0, old::setIsTools);
        changed |= differ(old.getIsThinking(), capabilities.contains("thinking") ? 1 : 0, old::setIsThinking);
        changed |= differ(old.getIsEmbedding(), capabilities.contains("embedding") ? 1 : 0, old::setIsEmbedding);

        // 参数量先用列表页徽标兜底，随后若抓到变体会以变体解析结果覆盖
        String sizesText = sortedSizes.isEmpty() ? null : String.join(",", sortedSizes);
        changed |= differ(old.getSizes(), sizesText, old::setSizes);
        Double minSize = numeric.isEmpty() ? null : numeric.get(0);
        Double maxSize = numeric.isEmpty() ? null : numeric.get(numeric.size() - 1);
        changed |= differ(old.getMinSizeB(), minSize, old::setMinSizeB);
        changed |= differ(old.getMaxSizeB(), maxSize, old::setMaxSizeB);

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
        return tagRepository.selectList(null).stream()
                .collect(Collectors.groupingBy(AiOllamaTag::getModelId, Collectors.counting()));
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
