package com.evernox.service;

import com.evernox.dto.ArenaCategoryOption;
import com.evernox.dto.ArenaFilterOptions;
import com.evernox.dto.ArenaRankItem;
import com.evernox.dto.ArenaSyncStatus;

import java.util.List;
import java.util.Map;

/**
 * Code Arena 模型排行榜（数据源 arena.ai）
 */
public interface AiArenaService {

    /** 分类列表（含每类模型数量），驱动前端类别切换按钮组 */
    List<ArenaCategoryOption> listCategories();

    /** 榜单行：按分类 + 开发方 + 价格区间 + 关键词筛选 */
    List<ArenaRankItem> listItems(String category, String org, String priceType,
                                  Double minPrice, Double maxPrice, String keyword);

    /** 筛选项：当前分类的厂商选项（带数量）与价格上下限 */
    ArenaFilterOptions getFilterOptions(String category);

    /** 同步状态 */
    ArenaSyncStatus getSyncStatus();

    /** 库中是否没有任何数据 */
    boolean isEmpty();

    /** 异步触发同步（管理员手动触发用） */
    void triggerSyncAsync();

    /** 全量同步（阻塞执行，返回最终状态） */
    ArenaSyncStatus syncAll();

    /**
     * 导入「本地抓取后上传」的榜单页面并入库（阻塞执行，返回最终状态）。
     * key = 分类 slug，value = 该分类榜单页的 HTML 原文。
     */
    ArenaSyncStatus importHtml(Map<String, String> htmlByCategory);
}
