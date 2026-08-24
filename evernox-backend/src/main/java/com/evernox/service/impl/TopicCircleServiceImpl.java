package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.UserRole;
import com.evernox.dto.TopicCircleRequest;
import com.evernox.dto.TopicCircleResponse;
import com.evernox.dto.TopicMemberResponse;
import com.evernox.entity.TopicCircle;
import com.evernox.entity.TopicCircleMember;
import com.evernox.entity.TopicPost;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.TopicCircleMemberRepository;
import com.evernox.repository.TopicCircleRepository;
import com.evernox.repository.TopicPostRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.TopicCircleService;
import com.evernox.service.TopicPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 话题圈服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TopicCircleServiceImpl implements TopicCircleService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TopicCircleRepository circleRepository;
    private final TopicCircleMemberRepository memberRepository;
    private final TopicPostRepository postRepository;
    private final UserRepository userRepository;
    private final TopicPostService postService;

    @Override
    @Transactional
    public TopicCircleResponse create(TopicCircleRequest request, Long userId) {
        String name = request.getName().trim();
        ensureNameUnique(name, null);

        TopicCircle circle = TopicCircle.builder()
                .name(name)
                .description(trimToNull(request.getDescription()))
                .ownerId(userId)
                .postCount(0)
                .memberCount(1)
                .deleted(0)
                .build();
        circleRepository.insert(circle);

        // 创建者自动加入
        memberRepository.insert(TopicCircleMember.builder()
                .circleId(circle.getId())
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build());

        log.info("话题圈创建: id={}, owner={}", circle.getId(), userId);
        TopicCircleResponse resp = TopicCircleResponse.from(circle);
        resp.setOwnerName(username(userId));
        resp.setFollowed(true);
        return resp;
    }

    @Override
    @Transactional
    public TopicCircleResponse update(Long id, TopicCircleRequest request, Long userId) {
        TopicCircle circle = requireCircle(id);
        requireOwnerOrAdmin(circle, userId);

        String name = request.getName().trim();
        ensureNameUnique(name, id);
        circle.setName(name);
        circle.setDescription(trimToNull(request.getDescription()));
        circleRepository.updateById(circle);

        TopicCircleResponse resp = TopicCircleResponse.from(circle);
        resp.setOwnerName(username(circle.getOwnerId()));
        resp.setFollowed(isFollowed(id, userId));
        return resp;
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        TopicCircle circle = requireCircle(id);
        requireOwnerOrAdmin(circle, userId);

        // 级联删除该圈帖子（含图片与点赞/收藏/评论/关联清理）
        List<TopicPost> posts = postRepository.selectList(new LambdaQueryWrapper<TopicPost>()
                .eq(TopicPost::getCircleId, id));
        for (TopicPost post : posts) {
            postService.deleteBySystem(post.getId());
        }

        // 删除关注关系
        memberRepository.delete(new LambdaQueryWrapper<TopicCircleMember>()
                .eq(TopicCircleMember::getCircleId, id));

        circleRepository.deleteById(id);
        log.info("话题圈删除: id={}, operator={}", id, userId);
    }

    @Override
    public IPage<TopicCircleResponse> list(int page, int size, String keyword, boolean onlyMine, Long userId) {
        LambdaQueryWrapper<TopicCircle> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(TopicCircle::getName, keyword.trim());
        }
        if (onlyMine) {
            List<Long> circleIds = memberRepository.selectList(new LambdaQueryWrapper<TopicCircleMember>()
                            .eq(TopicCircleMember::getUserId, userId)).stream()
                    .map(TopicCircleMember::getCircleId)
                    .toList();
            if (circleIds.isEmpty()) {
                return emptyPage(page, size);
            }
            wrapper.in(TopicCircle::getId, circleIds);
        }
        wrapper.orderByDesc(TopicCircle::getId);

        IPage<TopicCircle> raw = circleRepository.selectPage(newPage(page, size), wrapper);
        Set<Long> followedIds = followedCircleIds(userId);

        Page<TopicCircleResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream().map(c -> {
            TopicCircleResponse resp = TopicCircleResponse.from(c);
            resp.setOwnerName(username(c.getOwnerId()));
            resp.setFollowed(followedIds.contains(c.getId()));
            return resp;
        }).toList());
        return result;
    }

    @Override
    public TopicCircleResponse getDetail(Long id, Long userId) {
        TopicCircle circle = requireCircle(id);
        TopicCircleResponse resp = TopicCircleResponse.from(circle);
        resp.setOwnerName(username(circle.getOwnerId()));
        resp.setFollowed(isFollowed(id, userId));
        return resp;
    }

    @Override
    @Transactional
    public void follow(Long id, Long userId) {
        requireCircle(id);
        Long count = memberRepository.selectCount(new LambdaQueryWrapper<TopicCircleMember>()
                .eq(TopicCircleMember::getCircleId, id)
                .eq(TopicCircleMember::getUserId, userId));
        if (count != null && count > 0) {
            return;
        }
        memberRepository.insert(TopicCircleMember.builder()
                .circleId(id)
                .userId(userId)
                .createdAt(LocalDateTime.now())
                .build());
        circleRepository.update(null, new LambdaUpdateWrapper<TopicCircle>()
                .eq(TopicCircle::getId, id)
                .setSql("member_count = member_count + 1"));
    }

    @Override
    @Transactional
    public void unfollow(Long id, Long userId) {
        requireCircle(id);
        int removed = memberRepository.delete(new LambdaQueryWrapper<TopicCircleMember>()
                .eq(TopicCircleMember::getCircleId, id)
                .eq(TopicCircleMember::getUserId, userId));
        if (removed > 0) {
            circleRepository.update(null, new LambdaUpdateWrapper<TopicCircle>()
                    .eq(TopicCircle::getId, id)
                    .setSql("member_count = GREATEST(member_count - 1, 0)"));
        }
    }

    @Override
    @Transactional
    public void transfer(Long id, Long newOwnerId, Long operatorId) {
        TopicCircle circle = requireCircle(id);
        requireOwnerOrAdmin(circle, operatorId);
        if (circle.getOwnerId().equals(newOwnerId)) {
            return;
        }
        Long memberCount = memberRepository.selectCount(new LambdaQueryWrapper<TopicCircleMember>()
                .eq(TopicCircleMember::getCircleId, id)
                .eq(TopicCircleMember::getUserId, newOwnerId));
        if (memberCount == null || memberCount == 0) {
            throw new BusinessException("该用户尚未关注此圈子，无法转让");
        }
        circle.setOwnerId(newOwnerId);
        circleRepository.updateById(circle);
        log.info("话题圈转让: id={}, old={}, new={}", id, operatorId, newOwnerId);
    }

    @Override
    public List<TopicMemberResponse> listMembers(Long id, Long userId) {
        TopicCircle circle = requireCircle(id);
        List<TopicCircleMember> members = memberRepository.selectList(new LambdaQueryWrapper<TopicCircleMember>()
                .eq(TopicCircleMember::getCircleId, id)
                .orderByAsc(TopicCircleMember::getCreatedAt));
        if (members.isEmpty()) {
            return List.of();
        }
        List<Long> userIds = members.stream().map(TopicCircleMember::getUserId).toList();
        Map<Long, String> names = userRepository.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
        return members.stream().map(m -> TopicMemberResponse.builder()
                .userId(m.getUserId())
                .username(names.get(m.getUserId()))
                .isOwner(m.getUserId().equals(circle.getOwnerId()))
                .createdAt(m.getCreatedAt())
                .build()).toList();
    }

    // ==================== 内部方法 ====================

    private TopicCircle requireCircle(Long id) {
        TopicCircle circle = circleRepository.selectById(id);
        if (circle == null) {
            throw new BusinessException("话题圈不存在");
        }
        return circle;
    }

    private void requireOwnerOrAdmin(TopicCircle circle, Long userId) {
        if (circle.getOwnerId().equals(userId) || isAdmin(userId)) {
            return;
        }
        throw new BusinessException(403, "无权操作该话题圈");
    }

    private void ensureNameUnique(String name, Long excludeId) {
        Long count = circleRepository.selectCount(new LambdaQueryWrapper<TopicCircle>()
                .eq(TopicCircle::getName, name)
                .ne(excludeId != null, TopicCircle::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("圈子已存在");
        }
    }

    private boolean isFollowed(Long circleId, Long userId) {
        Long count = memberRepository.selectCount(new LambdaQueryWrapper<TopicCircleMember>()
                .eq(TopicCircleMember::getCircleId, circleId)
                .eq(TopicCircleMember::getUserId, userId));
        return count != null && count > 0;
    }

    private Set<Long> followedCircleIds(Long userId) {
        return memberRepository.selectList(new LambdaQueryWrapper<TopicCircleMember>()
                        .eq(TopicCircleMember::getUserId, userId)).stream()
                .map(TopicCircleMember::getCircleId)
                .collect(Collectors.toSet());
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

    private String trimToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private Page<TopicCircle> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private IPage<TopicCircleResponse> emptyPage(int page, int size) {
        Page<TopicCircleResponse> result = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), 0);
        result.setRecords(List.of());
        return result;
    }
}
