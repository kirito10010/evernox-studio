package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.TopicCommentRequest;
import com.evernox.dto.TopicCommentResponse;

/**
 * 话题评论服务接口
 */
public interface TopicCommentService {

    TopicCommentResponse create(TopicCommentRequest request, Long userId);

    /** 作者或管理员删除 */
    void delete(Long id, Long userId);

    IPage<TopicCommentResponse> list(Long postId, int page, int size);
}
