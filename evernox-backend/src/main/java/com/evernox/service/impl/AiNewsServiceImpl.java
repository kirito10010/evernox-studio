package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.AiNewsItemResponse;
import com.evernox.entity.AiNewsFavorite;
import com.evernox.entity.AiNewsItem;
import com.evernox.repository.AiNewsFavoriteRepository;
import com.evernox.repository.AiNewsItemRepository;
import com.evernox.service.AiNewsCrawler;
import com.evernox.service.AiNewsService;
import com.evernox.service.AiNewsTranslator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 编程资讯服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AiNewsServiceImpl implements AiNewsService {

    private static final int MAX_PAGE_SIZE = 50;

    private static final List<String> TAGS = List.of("model", "agent", "tool", "ide", "desktop", "benchmark", "cost", "other");

    private final AiNewsItemRepository itemRepository;
    private final AiNewsFavoriteRepository favoriteRepository;
    private final AiNewsCrawler crawler;
    private final AiNewsTranslator translator;

    @Override
    public int crawlAll() {
        List<AiNewsItem> items = crawler.crawlAll();
        // 已存在的 (source, externalId) → 条目，用于去重与热度更新
        Map<String, AiNewsItem> existingMap = itemRepository.selectList(null).stream()
                .collect(Collectors.toMap(i -> i.getSource() + ":" + i.getExternalId(), Function.identity(), (a, b) -> a));
        Set<String> seen = new HashSet<>();
        List<AiNewsItem> newItems = new ArrayList<>();
        int scoreUpdated = 0;
        for (AiNewsItem item : items) {
            String key = item.getSource() + ":" + item.getExternalId();
            AiNewsItem exist = existingMap.get(key);
            if (exist != null) {
                // 已存在：更新热度（HN 分数会变化）
                if (item.getScore() != null && !item.getScore().equals(exist.getScore())) {
                    exist.setScore(item.getScore());
                    itemRepository.updateById(exist);
                    scoreUpdated++;
                }
            } else if (seen.add(key)) {
                item.setCreatedAt(LocalDateTime.now());
                itemRepository.insert(item);
                newItems.add(item);
            }
        }
        // 翻译（在事务外逐条处理，避免长时间占用数据库连接）
        for (AiNewsItem item : newItems) {
            translateAndUpdate(item);
        }
        // 补齐历史未翻译的英文条目（每轮最多 50 条，避免过量 API 调用）
        backfillTranslations();
        // 按最新规则重分类历史数据：删掉误判的非 AI 条目、修正标签
        reclassifyExisting();
        if (!newItems.isEmpty() || scoreUpdated > 0) {
            log.info("AI 资讯采集：入库 {} 条，更新热度 {} 条", newItems.size(), scoreUpdated);
        }
        return newItems.size();
    }

    private void reclassifyExisting() {
        List<AiNewsItem> all = itemRepository.selectList(null);
        int deleted = 0;
        int retagged = 0;
        for (AiNewsItem item : all) {
            if (!isActiveSource(item.getSource())) {
                // 已下线来源（AIHOT）的历史条目直接清理，避免页面上再出现该来源的数据
                itemRepository.deleteById(item.getId());
                deleted++;
                continue;
            }
            if (!crawler.isRelevant(item.getTitle())) {
                itemRepository.deleteById(item.getId());
                deleted++;
                continue;
            }
            String newTag = crawler.classifyTag(item.getTitle());
            if (!newTag.equals(item.getTag())) {
                item.setTag(newTag);
                itemRepository.updateById(item);
                retagged++;
            }
        }
        if (deleted > 0 || retagged > 0) {
            log.info("AI 资讯重分类：删除无效 {} 条，修正标签 {} 条", deleted, retagged);
        }
    }

    private void backfillTranslations() {
        if (!translator.enabled()) {
            return;
        }
        List<AiNewsItem> untranslated = itemRepository.selectList(new LambdaQueryWrapper<AiNewsItem>()
                .in(AiNewsItem::getSource, "hackernews", "reddit")
                .isNull(AiNewsItem::getTitleZh)
                .last("LIMIT 50"));
        for (AiNewsItem item : untranslated) {
            translateAndUpdate(item);
        }
    }

    private void translateAndUpdate(AiNewsItem item) {
        if (!isActiveSource(item.getSource())) {
            return;
        }
        boolean changed = false;
        String zh = translator.translateToZh(item.getTitle());
        if (zh != null && !zh.isBlank()) {
            item.setTitleZh(zh);
            changed = true;
        }
        if (item.getSummary() != null && !item.getSummary().isBlank()) {
            String zhSummary = translator.translateToZh(item.getSummary());
            if (zhSummary != null && !zhSummary.isBlank()) {
                item.setSummaryZh(zhSummary);
                changed = true;
            }
        }
        if (changed) {
            itemRepository.updateById(item);
        }
        // 轻量限流，避免触发翻译 API 的 QPS 限制
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 当前仍在采集的来源（均为英文，需翻译）；AIHOT 已下线 */
    private boolean isActiveSource(String source) {
        return "hackernews".equals(source);
    }

    @Override
    public IPage<AiNewsItemResponse> listItems(String source, String tag, String keyword, String sort, int page, int size, Long userId) {
        Page<AiNewsItem> p = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        LambdaQueryWrapper<AiNewsItem> wrapper = new LambdaQueryWrapper<>();
        if (source != null && !source.isBlank()) {
            wrapper.eq(AiNewsItem::getSource, source);
        }
        if (tag != null && !tag.isBlank()) {
            wrapper.eq(AiNewsItem::getTag, tag);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(AiNewsItem::getTitle, keyword.trim());
        }
        if ("score".equals(sort)) {
            wrapper.orderByDesc(AiNewsItem::getScore).orderByDesc(AiNewsItem::getPublishedAt);
        } else {
            wrapper.orderByDesc(AiNewsItem::getPublishedAt);
        }
        IPage<AiNewsItem> raw = itemRepository.selectPage(p, wrapper);
        Set<Long> favIds = favoriteIds(userId);
        Page<AiNewsItemResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream()
                .map(i -> AiNewsItemResponse.from(i, favIds.contains(i.getId())))
                .toList());
        return result;
    }

    @Override
    public List<String> tags() {
        return TAGS;
    }

    @Override
    @Transactional
    public void favorite(Long userId, Long itemId) {
        if (userId == null || itemId == null) {
            return;
        }
        Long count = favoriteRepository.selectCount(new LambdaQueryWrapper<AiNewsFavorite>()
                .eq(AiNewsFavorite::getUserId, userId)
                .eq(AiNewsFavorite::getItemId, itemId));
        if (count != null && count > 0) {
            return; // 幂等
        }
        favoriteRepository.insert(AiNewsFavorite.builder()
                .userId(userId)
                .itemId(itemId)
                .createdAt(LocalDateTime.now())
                .build());
    }

    @Override
    @Transactional
    public void unfavorite(Long userId, Long itemId) {
        if (userId == null || itemId == null) {
            return;
        }
        favoriteRepository.delete(new LambdaQueryWrapper<AiNewsFavorite>()
                .eq(AiNewsFavorite::getUserId, userId)
                .eq(AiNewsFavorite::getItemId, itemId));
    }

    @Override
    public IPage<AiNewsItemResponse> myFavorites(Long userId, int page, int size) {
        Page<AiNewsFavorite> fp = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        IPage<AiNewsFavorite> favRaw = favoriteRepository.selectPage(fp, new LambdaQueryWrapper<AiNewsFavorite>()
                .eq(AiNewsFavorite::getUserId, userId)
                .orderByDesc(AiNewsFavorite::getCreatedAt));

        Page<AiNewsItemResponse> result = new Page<>(favRaw.getCurrent(), favRaw.getSize(), favRaw.getTotal());
        List<Long> itemIds = favRaw.getRecords().stream().map(AiNewsFavorite::getItemId).toList();
        if (itemIds.isEmpty()) {
            result.setRecords(Collections.emptyList());
            return result;
        }
        Map<Long, AiNewsItem> map = itemRepository.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(AiNewsItem::getId, Function.identity(), (a, b) -> a));
        List<AiNewsItemResponse> records = new ArrayList<>(itemIds.size());
        for (Long id : itemIds) {
            AiNewsItem it = map.get(id);
            if (it != null) {
                records.add(AiNewsItemResponse.from(it, true));
            }
        }
        result.setRecords(records);
        return result;
    }

    private Set<Long> favoriteIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return favoriteRepository.selectList(new LambdaQueryWrapper<AiNewsFavorite>()
                        .eq(AiNewsFavorite::getUserId, userId))
                .stream()
                .map(AiNewsFavorite::getItemId)
                .collect(Collectors.toSet());
    }
}
