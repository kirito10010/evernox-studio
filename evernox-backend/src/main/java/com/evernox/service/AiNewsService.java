package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.AiModelRankItem;
import com.evernox.dto.AiNewsItemResponse;
import com.evernox.entity.AiZhizhiRank;

import java.util.List;

/**
 * AI 编程资讯服务
 */
public interface AiNewsService {

    /** 采集一次并入库，返回新增条数 */
    int crawlAll();

    /** 分页查询列表，支持来源/标签/关键词过滤，sort=time|score */
    IPage<AiNewsItemResponse> listItems(String source, String tag, String keyword, String sort, int page, int size, Long userId);

    /** 标签列表 */
    List<String> tags();

    /** AI 模型排行榜（category: overall/coding/reasoning/knowledge/professional） */
    List<AiModelRankItem> getLeaderboard(String category);

    /** 抓取 AI 模型排行榜并入库 */
    void crawlLeaderboard();

    /** 「致知」模型排行榜（category: logic/code_v3/vision；month 为空取最新月） */
    List<AiZhizhiRank> getZhizhiRank(String category, String month);

    /** 「致知」模型排行榜可用月份列表（降序） */
    List<String> getZhizhiMonths(String category);

    /** 抓取「致知」模型排行榜并入库 */
    void crawlZhizhiRank();

    /** 收藏（幂等） */
    void favorite(Long userId, Long itemId);

    /** 取消收藏 */
    void unfavorite(Long userId, Long itemId);

    /** 我的收藏 */
    IPage<AiNewsItemResponse> myFavorites(Long userId, int page, int size);
}
