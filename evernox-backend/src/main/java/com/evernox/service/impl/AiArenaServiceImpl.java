package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.common.ResultCode;
import com.evernox.dto.ArenaCategoryOption;
import com.evernox.dto.ArenaFilterOptions;
import com.evernox.dto.ArenaRankDto;
import com.evernox.dto.ArenaRankItem;
import com.evernox.dto.ArenaSyncStatus;
import com.evernox.entity.AiArenaRank;
import com.evernox.exception.BusinessException;
import com.evernox.repository.AiArenaRankRepository;
import com.evernox.service.AiArenaCrawler;
import com.evernox.service.AiArenaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Code Arena 模型排行榜服务实现。
 *
 * 同步采用「逐语句提交」而非一个大事务：全量同步跨越 12 次网络请求，
 * 长事务会长时间占用连接与锁；各步骤幂等，中途中断下次同步会自动校正。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AiArenaServiceImpl implements AiArenaService {

    private static final String PRICE_TYPE_OUTPUT = "output";

    private final AiArenaRankRepository repository;
    private final AiArenaCrawler crawler;

    private final AtomicBoolean syncing = new AtomicBoolean(false);

    private volatile ArenaSyncStatus status = ArenaSyncStatus.builder()
            .running(false)
            .phase("空闲")
            .lastSuccess(false)
            .build();

    // ===== 查询 =====

    @Override
    public List<ArenaCategoryOption> listCategories() {
        return AiArenaCrawler.CATEGORIES.stream()
                .map(c -> ArenaCategoryOption.builder()
                        .slug(c.slug())
                        .label(c.labelZh())
                        .labelEn(c.labelEn())
                        .group(c.group())
                        .modelCount(repository.selectCount(new LambdaQueryWrapper<AiArenaRank>()
                                .eq(AiArenaRank::getCategory, c.slug())))
                        .build())
                .toList();
    }

    @Override
    public List<ArenaRankItem> listItems(String category, String org, String priceType,
                                         Double minPrice, Double maxPrice, String keyword) {
        if (!StringUtils.hasText(category)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类不能为空");
        }
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            double tmp = minPrice;
            minPrice = maxPrice;
            maxPrice = tmp;
        }

        LambdaQueryWrapper<AiArenaRank> w = new LambdaQueryWrapper<AiArenaRank>()
                .eq(AiArenaRank::getCategory, category.trim());
        if (StringUtils.hasText(org)) {
            w.eq(AiArenaRank::getOrg, org.trim());
        }
        if (StringUtils.hasText(keyword)) {
            w.like(AiArenaRank::getModelName, keyword.trim());
        }
        if (PRICE_TYPE_OUTPUT.equalsIgnoreCase(priceType == null ? "" : priceType.trim())) {
            if (minPrice != null) {
                w.ge(AiArenaRank::getOutputPrice, BigDecimal.valueOf(minPrice));
            }
            if (maxPrice != null) {
                w.le(AiArenaRank::getOutputPrice, BigDecimal.valueOf(maxPrice));
            }
        } else {
            if (minPrice != null) {
                w.ge(AiArenaRank::getInputPrice, BigDecimal.valueOf(minPrice));
            }
            if (maxPrice != null) {
                w.le(AiArenaRank::getInputPrice, BigDecimal.valueOf(maxPrice));
            }
        }
        w.orderByAsc(AiArenaRank::getRank).orderByAsc(AiArenaRank::getId);

        return repository.selectList(w).stream().map(ArenaRankItem::from).toList();
    }

    @Override
    public ArenaFilterOptions getFilterOptions(String category) {
        if (!StringUtils.hasText(category)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分类不能为空");
        }
        List<AiArenaRank> rows = repository.selectList(new LambdaQueryWrapper<AiArenaRank>()
                .select(AiArenaRank::getOrg, AiArenaRank::getInputPrice, AiArenaRank::getOutputPrice)
                .eq(AiArenaRank::getCategory, category.trim()));

        Map<String, Long> counts = new HashMap<>();
        BigDecimal minInput = null;
        BigDecimal maxInput = null;
        BigDecimal minOutput = null;
        BigDecimal maxOutput = null;
        for (AiArenaRank row : rows) {
            if (StringUtils.hasText(row.getOrg())) {
                counts.merge(row.getOrg(), 1L, Long::sum);
            }
            minInput = min(minInput, row.getInputPrice());
            maxInput = max(maxInput, row.getInputPrice());
            minOutput = min(minOutput, row.getOutputPrice());
            maxOutput = max(maxOutput, row.getOutputPrice());
        }

        List<ArenaFilterOptions.OrgOption> orgs = counts.entrySet().stream()
                .map(e -> ArenaFilterOptions.OrgOption.builder()
                        .key(e.getKey())
                        .label(e.getKey())
                        .count(e.getValue())
                        .build())
                .sorted(Comparator.comparing(ArenaFilterOptions.OrgOption::getCount).reversed()
                        .thenComparing(ArenaFilterOptions.OrgOption::getKey))
                .toList();

        return ArenaFilterOptions.builder()
                .orgs(orgs)
                .minInput(minInput)
                .maxInput(maxInput)
                .minOutput(minOutput)
                .maxOutput(maxOutput)
                .build();
    }

    @Override
    public ArenaSyncStatus getSyncStatus() {
        ArenaSyncStatus s = status;
        return ArenaSyncStatus.builder()
                .running(s.isRunning())
                .phase(s.getPhase())
                .currentCategory(s.getCurrentCategory())
                .categoryTotal(s.getCategoryTotal())
                .categoryDone(s.getCategoryDone())
                .rowTotal(repository.selectCount(null))
                .added(s.getAdded())
                .updated(s.getUpdated())
                .removed(s.getRemoved())
                .lastRunAt(s.getLastRunAt())
                .lastSuccess(s.isLastSuccess())
                .durationMs(s.getDurationMs())
                .message(s.getMessage())
                .build();
    }

    @Override
    public boolean isEmpty() {
        Long count = repository.selectCount(null);
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
    public ArenaSyncStatus syncAll() {
        return doSync("抓取榜单", category -> {
            try {
                return crawler.fetchCategory(category);
            } finally {
                crawler.throttle();
            }
        });
    }

    @Override
    public ArenaSyncStatus importHtml(Map<String, String> htmlByCategory) {
        if (htmlByCategory == null || htmlByCategory.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "数据包里没有任何可识别的分类页面");
        }
        return doSync("解析数据包", category -> {
            String html = htmlByCategory.get(category.slug());
            if (html == null || html.isBlank()) {
                throw new AiArenaCrawler.ArenaFetchException("数据包里没有该分类的页面文件");
            }
            return crawler.parseCategoryHtml(html, category);
        });
    }

    /** 每个分类的数据来源：在线抓取，或本地抓取后上传的页面 HTML */
    @FunctionalInterface
    private interface CategorySource {
        List<ArenaRankDto> load(AiArenaCrawler.Category category);
    }

    /**
     * 同步主流程：逐分类取数 → 逐分类 upsert + 差异删除。
     *
     * 逐语句提交、不用 @Transactional：整个流程可能跨越十余次网络请求，
     * 长事务会长时间占用连接与锁；各步骤幂等，中途中断下次同步会自动校正。
     */
    private ArenaSyncStatus doSync(String loadPhase, CategorySource source) {
        if (!syncing.compareAndSet(false, true)) {
            // 已有同步在跑，直接返回当前状态
            return getSyncStatus();
        }
        long start = System.currentTimeMillis();
        ArenaSyncStatus st = ArenaSyncStatus.builder()
                .running(true)
                .phase(loadPhase)
                .categoryTotal(AiArenaCrawler.CATEGORIES.size())
                .categoryDone(0)
                .lastRunAt(LocalDateTime.now())
                .lastSuccess(false)
                .build();
        status = st;

        int added = 0;
        int updated = 0;
        int removed = 0;
        int loadedCategories = 0;
        int failedCategories = 0;
        String firstError = null;
        try {
            LocalDateTime now = LocalDateTime.now();
            for (AiArenaCrawler.Category category : AiArenaCrawler.CATEGORIES) {
                st.setCurrentCategory(category.labelZh());
                List<ArenaRankDto> scraped;
                try {
                    scraped = source.load(category);
                } catch (AiArenaCrawler.ArenaFetchException e) {
                    // 安全闸：该分类没拿到有效数据，完全跳过其插入与删除，保留旧数据
                    failedCategories++;
                    if (firstError == null) {
                        firstError = e.getMessage();
                    }
                    log.warn("Code Arena 跳过分类 {}：{}", category.labelZh(), e.getMessage());
                    st.setCategoryDone(st.getCategoryDone() + 1);
                    continue;
                }
                st.setPhase("写入数据库");
                int[] diff = syncCategory(category, scraped, now);
                added += diff[0];
                updated += diff[1];
                removed += diff[2];
                loadedCategories++;
                st.setCategoryDone(st.getCategoryDone() + 1);
                st.setPhase(loadPhase);
            }

            if (loadedCategories == 0) {
                st.setLastSuccess(false);
                st.setMessage("未获得任何分类数据，已跳过全部写入（原因：" + firstError + "）");
                log.warn("Code Arena 同步：{}", st.getMessage());
                return st;
            }

            st.setLastSuccess(true);
            st.setAdded(added);
            st.setUpdated(updated);
            st.setRemoved(removed);
            String summary = "完成：" + loadedCategories + "/" + st.getCategoryTotal() + " 分类，新增 " + added
                    + " 更新 " + updated + " 删除 " + removed + "，共 " + repository.selectCount(null) + " 行"
                    + failureSuffix(failedCategories, firstError);
            st.setMessage(summary);
            log.info("Code Arena {}", summary);
            return st;
        } catch (Exception e) {
            st.setLastSuccess(false);
            st.setMessage("同步失败：" + e.getMessage());
            log.error("Code Arena 同步失败", e);
            return st;
        } finally {
            st.setRunning(false);
            st.setPhase("空闲");
            st.setCurrentCategory(null);
            st.setDurationMs(System.currentTimeMillis() - start);
            syncing.set(false);
        }
    }

    /**
     * 用一个分类的采集结果刷新库中该分类的数据。
     * 返回 [新增数, 更新数, 删除数]。
     */
    private int[] syncCategory(AiArenaCrawler.Category category, List<ArenaRankDto> scraped, LocalDateTime now) {
        Map<String, AiArenaRank> existing = repository.selectList(new LambdaQueryWrapper<AiArenaRank>()
                        .eq(AiArenaRank::getCategory, category.slug()))
                .stream()
                .collect(Collectors.toMap(AiArenaRank::getModelName, Function.identity(), (a, b) -> a));

        int added = 0;
        int updated = 0;
        Set<String> seen = new HashSet<>();
        for (ArenaRankDto dto : scraped) {
            seen.add(dto.getModelName());
            AiArenaRank old = existing.get(dto.getModelName());
            if (old == null) {
                repository.insert(toEntity(dto, now));
                added++;
            } else if (applyChanges(old, dto, now)) {
                repository.updateById(old);
                updated++;
            }
        }

        int removed = 0;
        for (AiArenaRank old : existing.values()) {
            if (!seen.contains(old.getModelName())) {
                repository.deleteById(old.getId());
                removed++;
            }
        }
        return new int[]{added, updated, removed};
    }

    private AiArenaRank toEntity(ArenaRankDto dto, LocalDateTime now) {
        return AiArenaRank.builder()
                .category(dto.getCategory())
                .categoryGroup(dto.getCategoryGroup())
                .rank(dto.getRank())
                .rankSpreadMin(dto.getRankSpreadMin())
                .rankSpreadMax(dto.getRankSpreadMax())
                .modelName(dto.getModelName())
                .modelUrl(dto.getModelUrl())
                .org(dto.getOrg())
                .license(dto.getLicense())
                .score(dto.getScore())
                .scoreCiPlus(dto.getScoreCiPlus())
                .scoreCiMinus(dto.getScoreCiMinus())
                .votes(dto.getVotes())
                .inputPrice(dto.getInputPrice())
                .outputPrice(dto.getOutputPrice())
                .contextText(dto.getContextText())
                .contextTokens(dto.getContextTokens())
                .isPreliminary(dto.getIsPreliminary())
                .sourceUrl(dto.getSourceUrl())
                .syncedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /** 用采集结果刷新已有行；返回是否发生了「实质变化」（不含同步时间） */
    private boolean applyChanges(AiArenaRank old, ArenaRankDto dto, LocalDateTime now) {
        boolean changed = false;
        changed |= differ(old.getCategoryGroup(), dto.getCategoryGroup(), old::setCategoryGroup);
        changed |= differ(old.getRank(), dto.getRank(), old::setRank);
        changed |= differ(old.getRankSpreadMin(), dto.getRankSpreadMin(), old::setRankSpreadMin);
        changed |= differ(old.getRankSpreadMax(), dto.getRankSpreadMax(), old::setRankSpreadMax);
        changed |= differ(old.getModelUrl(), dto.getModelUrl(), old::setModelUrl);
        changed |= differ(old.getOrg(), dto.getOrg(), old::setOrg);
        changed |= differ(old.getLicense(), dto.getLicense(), old::setLicense);
        changed |= differ(old.getScore(), dto.getScore(), old::setScore);
        changed |= differ(old.getScoreCiPlus(), dto.getScoreCiPlus(), old::setScoreCiPlus);
        changed |= differ(old.getScoreCiMinus(), dto.getScoreCiMinus(), old::setScoreCiMinus);
        changed |= differ(old.getVotes(), dto.getVotes(), old::setVotes);
        changed |= differDecimal(old.getInputPrice(), dto.getInputPrice(), old::setInputPrice);
        changed |= differDecimal(old.getOutputPrice(), dto.getOutputPrice(), old::setOutputPrice);
        changed |= differ(old.getContextText(), dto.getContextText(), old::setContextText);
        changed |= differ(old.getContextTokens(), dto.getContextTokens(), old::setContextTokens);
        changed |= differ(old.getIsPreliminary(), dto.getIsPreliminary(), old::setIsPreliminary);
        changed |= differ(old.getSourceUrl(), dto.getSourceUrl(), old::setSourceUrl);
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

    /**
     * 金额比较：DECIMAL 读回的标度（10.0000）与解析出的标度（10）不同，
     * 用 equals 会把每次同步都判成「有变化」，因此按数值比较。
     */
    private boolean differDecimal(BigDecimal current, BigDecimal incoming, Consumer<BigDecimal> setter) {
        if (current == null && incoming == null) {
            return false;
        }
        if (current != null && incoming != null && current.compareTo(incoming) == 0) {
            return false;
        }
        setter.accept(incoming);
        return true;
    }

    private String failureSuffix(int failedCategories, String firstError) {
        if (failedCategories == 0) {
            return "";
        }
        return "；" + failedCategories + " 个分类抓取失败已保留旧数据（原因：" + firstError + "）";
    }

    private static BigDecimal min(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.compareTo(current) < 0 ? candidate : current;
    }

    private static BigDecimal max(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.compareTo(current) > 0 ? candidate : current;
    }
}
