package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.TopicCircleRequest;
import com.evernox.dto.TopicCircleResponse;
import com.evernox.dto.TopicMemberResponse;

import java.util.List;

/**
 * 话题圈服务接口
 */
public interface TopicCircleService {

    TopicCircleResponse create(TopicCircleRequest request, Long userId);

    TopicCircleResponse update(Long id, TopicCircleRequest request, Long userId);

    void delete(Long id, Long userId);

    IPage<TopicCircleResponse> list(int page, int size, String keyword, boolean onlyMine, Long userId);

    TopicCircleResponse getDetail(Long id, Long userId);

    void follow(Long id, Long userId);

    void unfollow(Long id, Long userId);

    /** 转让圈子（仅圈主或管理员） */
    void transfer(Long id, Long newOwnerId, Long operatorId);

    /** 圈子成员列表 */
    List<TopicMemberResponse> listMembers(Long id, Long userId);
}
