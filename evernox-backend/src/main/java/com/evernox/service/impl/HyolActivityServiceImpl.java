package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evernox.common.ResultCode;
import com.evernox.dto.HyolActivityItem;
import com.evernox.dto.HyolActivityNameCount;
import com.evernox.dto.HyolActivitySyncStatus;
import com.evernox.dto.HyolActivityTimelineResponse;
import com.evernox.entity.HyolActivity;
import com.evernox.exception.BusinessException;
import com.evernox.repository.HyolActivityRepository;
import com.evernox.service.HyolActivityCrawler;
import com.evernox.service.HyolActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 火影忍者OL 活动时间轴实现。
 *
 * 数据来自官网公告列表 + 「停机更新公告」正文【限时热点】段。
 * 列表只保留最近 3 页的 list_N 编号，第 4 页起是 list_n{N}.shtml，因此翻页跟随「下一页」链接。
 * 同步为异步任务：AtomicBoolean 互斥 + volatile 状态对象整体替换，前端轮询 /sync/status。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HyolActivityServiceImpl implements HyolActivityService {

    /** 列表最多翻多少页，防止官网改版后无限循环 */
    private static final int MAX_LIST_PAGES = 40;

    /** 详情抓取并发数 */
    private static final int DETAIL_THREADS = 4;

    /** 只处理停机更新类公告，活动都在其【限时热点】段里 */
    private static final Pattern UPDATE_TITLE = Pattern.compile("停机更新|停机维护|更新公告");


    private static final ExecutorService DETAIL_POOL = Executors.newFixedThreadPool(DETAIL_THREADS, r -> {
        Thread t = new Thread(r, "hyol-activity-fetch");
        t.setDaemon(true);
        return t;
    });

    private final HyolActivityRepository activityRepository;
    private final HyolActivityCrawler crawler;

    @Value("${evernox.hyol-activity.start-year:2024}")
    private int startYear;

    private final AtomicBoolean syncing = new AtomicBoolean(false);

    private volatile HyolActivitySyncStatus status = HyolActivitySyncStatus.builder().running(false).build();

    @Override
    public HyolActivityTimelineResponse timeline(List<String> names, String keyword) {
        // 用字符串列名的 QueryWrapper：LambdaQueryWrapper 只认方法引用，
        // 传普通 lambda 会在运行时解析不出字段（can not find lambda cache）
        List<HyolActivity> rows = activityRepository.selectList(
                new QueryWrapper<HyolActivity>().orderByDesc("start_time"));

        // 按活动全名统计次数（筛选器直接用全名，不做归类、不改名字）
        Map<String, Long> counts = new LinkedHashMap<>();
        LocalDateTime syncedAt = null;
        for (HyolActivity row : rows) {
            String name = row.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            counts.merge(name, 1L, (a, b) -> a + b);
            LocalDateTime updatedAt = row.getUpdatedAt();
            if (updatedAt != null && (syncedAt == null || updatedAt.isAfter(syncedAt))) {
                syncedAt = updatedAt;
            }
        }

        List<HyolActivityNameCount> nameCounts = new ArrayList<>();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            nameCounts.add(HyolActivityNameCount.builder()
                    .name(entry.getKey())
                    .count(entry.getValue())
                    .build());
        }
        nameCounts.sort((a, b) -> {
            int cmp = Long.compare(b.getCount(), a.getCount());
            return cmp != 0 ? cmp : a.getName().compareTo(b.getName());
        });

        String kw = keyword == null ? "" : keyword.trim();
        List<HyolActivityItem> items = new ArrayList<>();
        for (HyolActivity row : rows) {
            String name = row.getName();
            if (name == null || name.isBlank()) {
                continue;
            }
            if (names != null && !names.isEmpty() && !names.contains(name)) {
                continue;
            }
            if (!kw.isEmpty() && !name.contains(kw)) {
                continue;
            }
            items.add(HyolActivityItem.builder()
                    .id(row.getId())
                    .name(name)
                    .category(row.getCategory())
                    .startTime(row.getStartTime())
                    .endTime(row.getEndTime())
                    .announceDate(row.getAnnounceDate())
                    .sourceTitle(row.getSourceTitle())
                    .sourceUrl(row.getSourceUrl())
                    .build());
        }

        // 没有开始时间的按公告日期排在后面
        items.sort((a, b) -> {
            LocalDateTime left = a.getStartTime() != null
                    ? a.getStartTime()
                    : (a.getAnnounceDate() != null ? a.getAnnounceDate().atStartOfDay() : LocalDateTime.MIN);
            LocalDateTime right = b.getStartTime() != null
                    ? b.getStartTime()
                    : (b.getAnnounceDate() != null ? b.getAnnounceDate().atStartOfDay() : LocalDateTime.MIN);
            return right.compareTo(left);
        });

        return HyolActivityTimelineResponse.builder()
                .items(items)
                .names(nameCounts)
                .total(items.size())
                .syncedAt(syncedAt)
                .build();
    }

    @Override
    public HyolActivitySyncStatus startSync(boolean full) {
        if (!syncing.compareAndSet(false, true)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "已有同步任务在执行，请稍后再试");
        }
        LocalDateTime startedAt = LocalDateTime.now();
        status = HyolActivitySyncStatus.builder()
                .running(true)
                .full(full)
                .phase("准备同步")
                .startedAt(startedAt)
                .build();
        CompletableFuture.runAsync(() -> runSync(full, startedAt));
        return status;
    }

    @Override
    public HyolActivitySyncStatus getSyncStatus() {
        return status;
    }

    // ==================== 同步主流程 ====================

    private void runSync(boolean full, LocalDateTime startedAt) {
        AtomicInteger added = new AtomicInteger();
        AtomicInteger updated = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicInteger skipped = new AtomicInteger();
        try {
            List<HyolActivityCrawler.ListItem> announcements = collectAnnouncements(full, startedAt);
            List<HyolActivityCrawler.ListItem> targets = new ArrayList<>();
            for (HyolActivityCrawler.ListItem item : announcements) {
                if (UPDATE_TITLE.matcher(item.title()).find()) {
                    targets.add(item);
                }
            }
            publish(true, full, "解析活动 0/" + targets.size(), 0, targets.size(),
                    added.get(), updated.get(), failed.get(), skipped.get(), startedAt);

            AtomicInteger done = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (HyolActivityCrawler.ListItem item : targets) {
                futures.add(DETAIL_POOL.submit(() -> {
                    try {
                        processAnnouncement(item, full, added, updated, failed, skipped);
                    } catch (Exception e) {
                        failed.incrementAndGet();
                        log.warn("解析活动公告失败: url={}, err={}", item.url(), e.getMessage());
                    } finally {
                        int completed = done.incrementAndGet();
                        publish(true, full, "解析活动 " + completed + "/" + targets.size(),
                                completed, targets.size(), added.get(), updated.get(),
                                failed.get(), skipped.get(), startedAt);
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    log.warn("等待活动解析任务失败: {}", e.getMessage());
                }
            }

            String message = "同步完成：新增 " + added.get() + " 条，更新 " + updated.get()
                    + " 条，跳过 " + skipped.get() + " 篇，失败 " + failed.get() + " 篇";
            status = HyolActivitySyncStatus.builder()
                    .running(false)
                    .full(full)
                    .phase("完成")
                    .done(targets.size())
                    .total(targets.size())
                    .added(added.get())
                    .updated(updated.get())
                    .failed(failed.get())
                    .skipped(skipped.get())
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .message(message)
                    .build();
            log.info("活动时间轴同步完成: {}", message);
        } catch (Exception e) {
            log.warn("活动时间轴同步失败: {}", e.getMessage());
            status = HyolActivitySyncStatus.builder()
                    .running(false)
                    .full(full)
                    .phase("失败")
                    .added(added.get())
                    .updated(updated.get())
                    .failed(failed.get())
                    .skipped(skipped.get())
                    .startedAt(startedAt)
                    .finishedAt(LocalDateTime.now())
                    .message("同步失败：" + e.getMessage())
                    .build();
        } finally {
            syncing.set(false);
        }
    }

    /** 跟随「下一页」收集公告，遇到早于起始年的条目就停 */
    private List<HyolActivityCrawler.ListItem> collectAnnouncements(boolean full, LocalDateTime startedAt) {
        List<HyolActivityCrawler.ListItem> all = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        LocalDate limit = LocalDate.of(startYear, 1, 1);
        String url = HyolActivityCrawler.LIST_URL;
        int page = 0;
        while (url != null && !url.isBlank() && visited.add(url) && page < MAX_LIST_PAGES) {
            page++;
            HyolActivityCrawler.ListPage listPage = crawler.fetchListPage(url);
            if (listPage.items().isEmpty()) {
                log.warn("活动公告列表为空，停止翻页: page={}, url={}", page, url);
                break;
            }
            boolean reachedLimit = false;
            for (HyolActivityCrawler.ListItem item : listPage.items()) {
                if (item.date() != null && item.date().isBefore(limit)) {
                    reachedLimit = true;
                    continue;
                }
                all.add(item);
            }
            publish(true, full, "抓取公告列表 第 " + page + " 页（" + all.size() + " 条）",
                    0, 0, 0, 0, 0, 0, startedAt);
            if (reachedLimit) {
                break;
            }
            url = listPage.nextUrl();
        }
        return all;
    }

    /** 单篇公告：抓正文 → 解析活动 → 幂等写入 */
    private void processAnnouncement(HyolActivityCrawler.ListItem item, boolean full,
                                     AtomicInteger added, AtomicInteger updated,
                                     AtomicInteger failed, AtomicInteger skipped) {
        Long exists = activityRepository.selectCount(new QueryWrapper<HyolActivity>()
                .eq("source_url", item.url()));
        if (!full && exists != null && exists > 0) {
            skipped.incrementAndGet();
            return;
        }
        String text = crawler.fetchDetailText(item.url());
        if (text.isBlank()) {
            skipped.incrementAndGet();
            return;
        }
        List<HyolActivityCrawler.ParsedActivity> parsed = crawler.parseActivities(text, item.date());
        if (parsed.isEmpty()) {
            skipped.incrementAndGet();
            return;
        }
        for (HyolActivityCrawler.ParsedActivity activity : parsed) {
            HyolActivity existing = activityRepository.selectOne(new QueryWrapper<HyolActivity>()
                    .eq("source_url", item.url())
                    .eq("name", activity.name()));
            if (existing == null) {
                activityRepository.insert(HyolActivity.builder()
                        .name(activity.name())
                        .category(activity.category())
                        .startTime(activity.startTime())
                        .endTime(activity.endTime())
                        .announceDate(item.date())
                        .sourceTitle(item.title())
                        .sourceUrl(item.url())
                        .build());
                added.incrementAndGet();
            } else {
                existing.setCategory(activity.category());
                existing.setStartTime(activity.startTime());
                existing.setEndTime(activity.endTime());
                existing.setAnnounceDate(item.date());
                existing.setSourceTitle(item.title());
                activityRepository.updateById(existing);
                updated.incrementAndGet();
            }
        }
    }

    private void publish(boolean running, boolean full, String phase, long done, long total,
                         long added, long updated, long failed, long skipped, LocalDateTime startedAt) {
        status = HyolActivitySyncStatus.builder()
                .running(running)
                .full(full)
                .phase(phase)
                .done(done)
                .total(total)
                .added(added)
                .updated(updated)
                .failed(failed)
                .skipped(skipped)
                .startedAt(startedAt)
                .build();
    }
}
