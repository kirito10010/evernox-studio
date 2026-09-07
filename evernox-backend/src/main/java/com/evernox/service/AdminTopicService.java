package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.TopicCircleRequest;
import com.evernox.dto.TopicCircleResponse;
import com.evernox.dto.TopicCommentResponse;
import com.evernox.dto.TopicPostResponse;

import java.util.List;

/**
 * 话题集中营管理服务接口（仅管理员）
 */
public interface AdminTopicService {

    // 帖子
    IPage<TopicPostResponse> listPosts(int page, int size, String keyword, String username, Long circleId,
                                       String startDate, String endDate, String sortField, String sortOrder);
    void deletePost(Long id);
    void batchDeletePosts(List<Long> ids);

    // 评论
    IPage<TopicCommentResponse> listComments(int page, int size, String keyword, String username,
                                             String startDate, String endDate, String sortField, String sortOrder);
    void deleteComment(Long id);
    void batchDeleteComments(List<Long> ids);

    // 圈子
    IPage<TopicCircleResponse> listCircles(int page, int size, String keyword, String username,
                                           String startDate, String endDate, String sortField, String sortOrder);
    TopicCircleResponse createCircle(TopicCircleRequest request, Long adminId);
    TopicCircleResponse updateCircle(Long id, TopicCircleRequest request);
    void deleteCircle(Long id);
    void batchDeleteCircles(List<Long> ids);
}
