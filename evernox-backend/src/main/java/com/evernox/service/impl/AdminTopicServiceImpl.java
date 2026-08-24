package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.TopicCircleRequest;
import com.evernox.dto.TopicCircleResponse;
import com.evernox.dto.TopicCommentResponse;
import com.evernox.dto.TopicPostResponse;
import com.evernox.entity.TopicCircle;
import com.evernox.entity.TopicComment;
import com.evernox.entity.TopicPost;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.TopicCircleRepository;
import com.evernox.repository.TopicCommentRepository;
import com.evernox.repository.TopicPostRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.AdminTopicService;
import com.evernox.service.TopicCircleService;
import com.evernox.service.TopicPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 话题集中营管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminTopicServiceImpl implements AdminTopicService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 200;

    private final TopicCircleService circleService;
    private final TopicPostService postService;
    private final TopicCircleRepository circleRepository;
    private final TopicPostRepository postRepository;
    private final TopicCommentRepository commentRepository;
    private final UserRepository userRepository;

    // ==================== 帖子 ====================

    @Override
    public IPage<TopicPostResponse> listPosts(int page, int size, String keyword) {
        LambdaQueryWrapper<TopicPost> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(TopicPost::getTitle, keyword.trim());
        }
        wrapper.orderByDesc(TopicPost::getCreatedAt).orderByDesc(TopicPost::getId);

        IPage<TopicPost> raw = postRepository.selectPage(newPage(page, size), wrapper);
        Map<Long, String> circleNames = circleNameMap(raw.getRecords());
        Map<Long, String> authorNames = usernameMap(raw.getRecords().stream().map(TopicPost::getUserId).distinct().toList());

        Page<TopicPostResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(p -> {
            TopicPostResponse resp = TopicPostResponse.base(p);
            resp.setCircleName(circleNames.get(p.getCircleId()));
            resp.setAuthorName(authorNames.get(p.getUserId()));
            resp.setContent(null);
            return resp;
        }).toList());
        return result;
    }

    @Override
    @Transactional
    public void deletePost(Long id) {
        postService.deleteBySystem(id);
        log.info("管理员删除话题帖子: id={}", id);
    }

    @Override
    @Transactional
    public void batchDeletePosts(List<Long> ids) {
        List<Long> distinct = validateBatch(ids);
        for (Long id : distinct) {
            postService.deleteBySystem(id);
        }
        log.info("管理员批量删除话题帖子: ids={}", distinct);
    }

    // ==================== 评论 ====================

    @Override
    public IPage<TopicCommentResponse> listComments(int page, int size, String keyword) {
        LambdaQueryWrapper<TopicComment> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(TopicComment::getContent, keyword.trim());
        }
        wrapper.orderByDesc(TopicComment::getCreatedAt).orderByDesc(TopicComment::getId);

        IPage<TopicComment> raw = commentRepository.selectPage(newPage(page, size), wrapper);
        Map<Long, String> authorNames = usernameMap(raw.getRecords().stream().map(TopicComment::getUserId).distinct().toList());
        List<Long> postIds = raw.getRecords().stream().map(TopicComment::getPostId).distinct().toList();
        Map<Long, String> postTitles = postIds.isEmpty() ? Map.of()
                : postRepository.selectBatchIds(postIds).stream()
                        .collect(Collectors.toMap(TopicPost::getId, TopicPost::getTitle));

        Page<TopicCommentResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(c -> {
            TopicCommentResponse resp = TopicCommentResponse.from(c);
            resp.setAuthorName(authorNames.get(c.getUserId()));
            resp.setPostTitle(postTitles.get(c.getPostId()));
            return resp;
        }).toList());
        return result;
    }

    @Override
    @Transactional
    public void deleteComment(Long id) {
        TopicComment comment = commentRepository.selectById(id);
        if (comment == null) {
            throw new BusinessException("评论不存在");
        }
        // 管理员直接逻辑删除 + 计数回退
        commentRepository.deleteById(id);
        postRepository.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TopicPost>()
                .eq(TopicPost::getId, comment.getPostId())
                .setSql("comment_count = GREATEST(comment_count - 1, 0)"));
        log.info("管理员删除话题评论: id={}", id);
    }

    @Override
    @Transactional
    public void batchDeleteComments(List<Long> ids) {
        List<Long> distinct = validateBatch(ids);
        for (Long id : distinct) {
            deleteComment(id);
        }
        log.info("管理员批量删除话题评论: ids={}", distinct);
    }

    // ==================== 圈子 ====================

    @Override
    public IPage<TopicCircleResponse> listCircles(int page, int size, String keyword) {
        LambdaQueryWrapper<TopicCircle> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(TopicCircle::getName, keyword.trim());
        }
        wrapper.orderByDesc(TopicCircle::getId);

        IPage<TopicCircle> raw = circleRepository.selectPage(newPage(page, size), wrapper);
        Map<Long, String> ownerNames = usernameMap(raw.getRecords().stream().map(TopicCircle::getOwnerId).distinct().toList());

        Page<TopicCircleResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(c -> {
            TopicCircleResponse resp = TopicCircleResponse.from(c);
            resp.setOwnerName(ownerNames.get(c.getOwnerId()));
            return resp;
        }).toList());
        return result;
    }

    @Override
    public TopicCircleResponse createCircle(TopicCircleRequest request, Long adminId) {
        return circleService.create(request, adminId);
    }

    @Override
    public TopicCircleResponse updateCircle(Long id, TopicCircleRequest request) {
        // 管理员身份绕过 owner 校验
        TopicCircle circle = circleRepository.selectById(id);
        if (circle == null) {
            throw new BusinessException("话题圈不存在");
        }
        return circleService.update(id, request, circle.getOwnerId());
    }

    @Override
    @Transactional
    public void deleteCircle(Long id) {
        TopicCircle circle = circleRepository.selectById(id);
        if (circle == null) {
            throw new BusinessException("话题圈不存在");
        }
        circleService.delete(id, circle.getOwnerId());
        log.info("管理员删除话题圈: id={}", id);
    }

    @Override
    @Transactional
    public void batchDeleteCircles(List<Long> ids) {
        List<Long> distinct = validateBatch(ids);
        for (Long id : distinct) {
            deleteCircle(id);
        }
        log.info("管理员批量删除话题圈: ids={}", distinct);
    }

    // ==================== 内部方法 ====================

    private List<Long> validateBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的数据");
        }
        List<Long> distinct = ids.stream().distinct().toList();
        if (distinct.size() > MAX_BATCH_SIZE) {
            throw new BusinessException("单次最多删除 " + MAX_BATCH_SIZE + " 条");
        }
        return distinct;
    }

    private Map<Long, String> circleNameMap(List<TopicPost> posts) {
        List<Long> circleIds = posts.stream().map(TopicPost::getCircleId).distinct().toList();
        if (circleIds.isEmpty()) {
            return Map.of();
        }
        return circleRepository.selectBatchIds(circleIds).stream()
                .collect(Collectors.toMap(TopicCircle::getId, TopicCircle::getName));
    }

    private Map<Long, String> usernameMap(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private <T> Page<T> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
