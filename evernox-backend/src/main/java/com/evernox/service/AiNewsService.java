package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.AiNewsItemResponse;

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

    /** 收藏（幂等） */
    void favorite(Long userId, Long itemId);

    /** 取消收藏 */
    void unfavorite(Long userId, Long itemId);

    /** 我的收藏 */
    IPage<AiNewsItemResponse> myFavorites(Long userId, int page, int size);
}
