package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.UserRole;
import com.evernox.dto.TopicCommentRequest;
import com.evernox.dto.TopicCommentResponse;
import com.evernox.entity.TopicComment;
import com.evernox.entity.TopicPost;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.TopicCommentRepository;
import com.evernox.repository.TopicPostRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.TopicCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 话题评论服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TopicCommentServiceImpl implements TopicCommentService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TopicCommentRepository commentRepository;
    private final TopicPostRepository postRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public TopicCommentResponse create(TopicCommentRequest request, Long userId) {
        TopicPost post = postRepository.selectById(request.getPostId());
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        TopicComment comment = TopicComment.builder()
                .postId(request.getPostId())
                .userId(userId)
                .content(request.getContent().trim())
                .deleted(0)
                .build();
        commentRepository.insert(comment);

        postRepository.update(null, new LambdaUpdateWrapper<TopicPost>()
                .eq(TopicPost::getId, request.getPostId())
                .setSql("comment_count = comment_count + 1"));

        TopicCommentResponse resp = TopicCommentResponse.from(comment);
        resp.setAuthorName(username(userId));
        return resp;
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        TopicComment comment = requireComment(id);
        if (!comment.getUserId().equals(userId) && !isAdmin(userId)) {
            throw new BusinessException(403, "无权删除该评论");
        }
        commentRepository.deleteById(id);

        postRepository.update(null, new LambdaUpdateWrapper<TopicPost>()
                .eq(TopicPost::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(comment_count - 1, 0)"));
        log.info("话题评论删除: id={}, operator={}", id, userId);
    }

    @Override
    public IPage<TopicCommentResponse> list(Long postId, int page, int size) {
        IPage<TopicComment> raw = commentRepository.selectPage(newPage(page, size),
                new LambdaQueryWrapper<TopicComment>()
                        .eq(TopicComment::getPostId, postId)
                        .orderByAsc(TopicComment::getCreatedAt)
                        .orderByAsc(TopicComment::getId));

        List<Long> userIds = raw.getRecords().stream().map(TopicComment::getUserId).distinct().toList();
        Map<Long, String> names = userIds.isEmpty() ? Map.of()
                : userRepository.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));

        Page<TopicCommentResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(c -> {
            TopicCommentResponse resp = TopicCommentResponse.from(c);
            resp.setAuthorName(names.get(c.getUserId()));
            return resp;
        }).toList());
        return result;
    }

    private TopicComment requireComment(Long id) {
        TopicComment comment = commentRepository.selectById(id);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        return comment;
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userRepository.selectById(userId);
        return user != null && UserRole.ADMIN.equals(user.getRole());
    }

    private String username(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.selectById(userId);
        return user != null ? user.getUsername() : "未知用户";
    }

    private Page<TopicComment> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
