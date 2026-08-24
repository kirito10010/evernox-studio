package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.TopicInteractionResponse;
import com.evernox.dto.TopicPostRequest;
import com.evernox.dto.TopicPostResponse;
import com.evernox.dto.TopicRankResponse;

/**
 * 话题帖子服务接口
 */
public interface TopicPostService {

    TopicPostResponse create(TopicPostRequest request, Long userId);

    TopicPostResponse update(Long id, TopicPostRequest request, Long userId);

    /** 作者或管理员删除 */
    void delete(Long id, Long userId);

    /** 系统级删除（圈子级联删除时调用，不做归属校验） */
    void deleteBySystem(Long id);

    IPage<TopicPostResponse> listSquare(int page, int size, String sort, Long userId);

    IPage<TopicPostResponse> listByCircle(int page, int size, Long circleId, Long userId);

    IPage<TopicPostResponse> listMine(int page, int size, Long userId);

    IPage<TopicPostResponse> listFavorites(int page, int size, Long userId);

    TopicPostResponse getDetail(Long id, Long userId);

    TopicInteractionResponse toggleLike(Long id, Long userId);

    TopicInteractionResponse toggleFavorite(Long id, Long userId);

    /** 广场排行榜（圈子热度 + 用户发帖数） */
    TopicRankResponse getRank();
}
