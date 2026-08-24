package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.UserRole;
import com.evernox.config.StorageConfig;
import com.evernox.dto.TopicInteractionResponse;
import com.evernox.dto.TopicPostRequest;
import com.evernox.dto.TopicPostResponse;
import com.evernox.dto.TopicCircleResponse;
import com.evernox.dto.TopicRankResponse;
import com.evernox.entity.Image;
import com.evernox.entity.TopicCircle;
import com.evernox.entity.TopicComment;
import com.evernox.entity.TopicPost;
import com.evernox.entity.TopicPostFavorite;
import com.evernox.entity.TopicPostImage;
import com.evernox.entity.TopicPostLike;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.ImageRepository;
import com.evernox.repository.TopicCircleRepository;
import com.evernox.repository.TopicCommentRepository;
import com.evernox.repository.TopicPostFavoriteRepository;
import com.evernox.repository.TopicPostImageRepository;
import com.evernox.repository.TopicPostLikeRepository;
import com.evernox.repository.TopicPostRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.ImageService;
import com.evernox.service.TopicPostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 话题帖子服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TopicPostServiceImpl implements TopicPostService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TopicPostRepository postRepository;
    private final TopicPostImageRepository postImageRepository;
    private final TopicPostLikeRepository likeRepository;
    private final TopicPostFavoriteRepository favoriteRepository;
    private final TopicCommentRepository commentRepository;
    private final TopicCircleRepository circleRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final NoteHtmlSanitizer sanitizer;

    // ==================== 写操作 ====================

    @Override
    @Transactional
    public TopicPostResponse create(TopicPostRequest request, Long userId) {
        TopicCircle circle = circleRepository.selectById(request.getCircleId());
        if (circle == null) {
            throw new BusinessException("话题圈不存在");
        }
        String safeHtml = sanitizer.sanitize(request.getContent());
        if (safeHtml == null || safeHtml.isBlank()) {
            throw new BusinessException("正文不能为空");
        }
        List<Long> imageIds = new ArrayList<>(sanitizer.extractImageIds(safeHtml));
        validateImages(imageIds, userId);

        TopicPost post = TopicPost.builder()
                .circleId(request.getCircleId())
                .userId(userId)
                .title(request.getTitle().trim())
                .content(safeHtml)
                .likeCount(0)
                .commentCount(0)
                .favoriteCount(0)
                .deleted(0)
                .build();
        postRepository.insert(post);
        linkImages(post.getId(), imageIds);

        circleRepository.update(null, new LambdaUpdateWrapper<TopicCircle>()
                .eq(TopicCircle::getId, request.getCircleId())
                .setSql("post_count = post_count + 1"));

        log.info("话题发帖: id={}, circle={}, user={}", post.getId(), request.getCircleId(), userId);
        return toResponse(post, userId);
    }

    @Override
    @Transactional
    public TopicPostResponse update(Long id, TopicPostRequest request, Long userId) {
        TopicPost post = requirePost(id);
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权编辑该帖子");
        }
        TopicCircle circle = circleRepository.selectById(request.getCircleId());
        if (circle == null) {
            throw new BusinessException("话题圈不存在");
        }
        String safeHtml = sanitizer.sanitize(request.getContent());
        if (safeHtml == null || safeHtml.isBlank()) {
            throw new BusinessException("正文不能为空");
        }
        List<Long> newIds = new ArrayList<>(sanitizer.extractImageIds(safeHtml));
        validateImages(newIds, userId);

        // 换圈子时调整两边的帖子计数
        if (!post.getCircleId().equals(request.getCircleId())) {
            circleRepository.update(null, new LambdaUpdateWrapper<TopicCircle>()
                    .eq(TopicCircle::getId, post.getCircleId())
                    .setSql("post_count = GREATEST(post_count - 1, 0)"));
            circleRepository.update(null, new LambdaUpdateWrapper<TopicCircle>()
                    .eq(TopicCircle::getId, request.getCircleId())
                    .setSql("post_count = post_count + 1"));
        }

        post.setCircleId(request.getCircleId());
        post.setTitle(request.getTitle().trim());
        post.setContent(safeHtml);
        postRepository.updateById(post);

        syncImages(id, newIds);
        return toResponse(post, userId);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        TopicPost post = requirePost(id);
        if (!post.getUserId().equals(userId) && !isAdmin(userId)) {
            throw new BusinessException(403, "无权删除该帖子");
        }
        doDeleteCleanup(post);
        log.info("话题帖子删除: id={}, operator={}", id, userId);
    }

    @Override
    @Transactional
    public void deleteBySystem(Long id) {
        TopicPost post = postRepository.selectById(id);
        if (post == null) {
            return;
        }
        doDeleteCleanup(post);
    }

    // ==================== 读操作 ====================

    @Override
    public IPage<TopicPostResponse> listSquare(int page, int size, String sort, Long userId) {
        QueryWrapper<TopicPost> wrapper = new QueryWrapper<>();
        switch (sort == null ? "hot" : sort) {
            case "like" -> wrapper.orderByDesc("like_count").orderByDesc("created_at");
            case "favorite" -> wrapper.orderByDesc("favorite_count").orderByDesc("created_at");
            case "comment" -> wrapper.orderByDesc("comment_count").orderByDesc("created_at");
            case "hot" -> wrapper.orderByDesc(
                    "(like_count*3 + comment_count*2 + favorite_count*2 + 1) / POWER(TIMESTAMPDIFF(HOUR, created_at, NOW()) + 2, 1.2)")
                    .orderByDesc("id");
            default -> wrapper.orderByDesc("created_at").orderByDesc("id");
        }
        return toPage(postRepository.selectPage(newPage(page, size), wrapper), userId);
    }

    @Override
    public IPage<TopicPostResponse> listByCircle(int page, int size, Long circleId, Long userId) {
        LambdaQueryWrapper<TopicPost> wrapper = new LambdaQueryWrapper<TopicPost>()
                .eq(TopicPost::getCircleId, circleId)
                .orderByDesc(TopicPost::getCreatedAt)
                .orderByDesc(TopicPost::getId);
        return toPage(postRepository.selectPage(newPage(page, size), wrapper), userId);
    }

    @Override
    public IPage<TopicPostResponse> listMine(int page, int size, Long userId) {
        LambdaQueryWrapper<TopicPost> wrapper = new LambdaQueryWrapper<TopicPost>()
                .eq(TopicPost::getUserId, userId)
                .orderByDesc(TopicPost::getCreatedAt)
                .orderByDesc(TopicPost::getId);
        return toPage(postRepository.selectPage(newPage(page, size), wrapper), userId);
    }

    @Override
    public IPage<TopicPostResponse> listFavorites(int page, int size, Long userId) {
        List<Long> postIds = favoriteRepository.selectList(new LambdaQueryWrapper<TopicPostFavorite>()
                        .eq(TopicPostFavorite::getUserId, userId)).stream()
                .map(TopicPostFavorite::getPostId)
                .toList();
        if (postIds.isEmpty()) {
            return emptyPage(page, size);
        }
        LambdaQueryWrapper<TopicPost> wrapper = new LambdaQueryWrapper<TopicPost>()
                .in(TopicPost::getId, postIds)
                .orderByDesc(TopicPost::getCreatedAt)
                .orderByDesc(TopicPost::getId);
        return toPage(postRepository.selectPage(newPage(page, size), wrapper), userId);
    }

    @Override
    public TopicPostResponse getDetail(Long id, Long userId) {
        return toResponse(requirePost(id), userId);
    }

    // ==================== 互动 ====================

    @Override
    @Transactional
    public TopicInteractionResponse toggleLike(Long id, Long userId) {
        requirePost(id);
        boolean liked = toggleRelation(likeRepository, id, userId, "like_count");
        return interactionState(id, userId, liked, null);
    }

    @Override
    @Transactional
    public TopicInteractionResponse toggleFavorite(Long id, Long userId) {
        requirePost(id);
        boolean favorited = toggleRelation(favoriteRepository, id, userId, "favorite_count");
        return interactionState(id, userId, null, favorited);
    }

    @Override
    public TopicRankResponse getRank() {
        List<TopicCircle> circles = circleRepository.selectList(new LambdaQueryWrapper<TopicCircle>()
                .orderByDesc(TopicCircle::getPostCount)
                .orderByDesc(TopicCircle::getId)
                .last("LIMIT 10"));
        List<TopicCircleResponse> circleRanks = circles.stream()
                .map(TopicCircleResponse::from)
                .toList();

        List<Map<String, Object>> rows = postRepository.selectTopPosters(10);
        List<Long> userIds = rows.stream().map(r -> ((Number) r.get("user_id")).longValue()).toList();
        Map<Long, String> names = userIds.isEmpty() ? Map.of()
                : userRepository.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername));
        List<TopicRankResponse.TopicUserRank> userRanks = rows.stream().map(r -> {
            Long userId = ((Number) r.get("user_id")).longValue();
            return TopicRankResponse.TopicUserRank.builder()
                    .userId(userId)
                    .username(names.get(userId))
                    .postCount(((Number) r.get("cnt")).intValue())
                    .build();
        }).toList();

        return TopicRankResponse.builder().circles(circleRanks).users(userRanks).build();
    }

    // ==================== 内部方法 ====================

    /** 点赞/收藏切换：返回切换后是否处于「已互动」状态 */
    private boolean toggleRelation(
            com.baomidou.mybatisplus.core.mapper.BaseMapper<?> relationRepository,
            Long postId, Long userId, String countColumn) {
        boolean exists;
        if (relationRepository == likeRepository) {
            exists = likeRepository.selectCount(new LambdaQueryWrapper<TopicPostLike>()
                    .eq(TopicPostLike::getPostId, postId).eq(TopicPostLike::getUserId, userId)) > 0;
            if (exists) {
                likeRepository.delete(new LambdaQueryWrapper<TopicPostLike>()
                        .eq(TopicPostLike::getPostId, postId).eq(TopicPostLike::getUserId, userId));
            } else {
                likeRepository.insert(TopicPostLike.builder().postId(postId).userId(userId)
                        .createdAt(LocalDateTime.now()).build());
            }
        } else {
            exists = favoriteRepository.selectCount(new LambdaQueryWrapper<TopicPostFavorite>()
                    .eq(TopicPostFavorite::getPostId, postId).eq(TopicPostFavorite::getUserId, userId)) > 0;
            if (exists) {
                favoriteRepository.delete(new LambdaQueryWrapper<TopicPostFavorite>()
                        .eq(TopicPostFavorite::getPostId, postId).eq(TopicPostFavorite::getUserId, userId));
            } else {
                favoriteRepository.insert(TopicPostFavorite.builder().postId(postId).userId(userId)
                        .createdAt(LocalDateTime.now()).build());
            }
        }
        postRepository.update(null, new LambdaUpdateWrapper<TopicPost>()
                .eq(TopicPost::getId, postId)
                .setSql(exists
                        ? countColumn + " = GREATEST(" + countColumn + " - 1, 0)"
                        : countColumn + " = " + countColumn + " + 1"));
        return !exists;
    }

    private TopicInteractionResponse interactionState(Long postId, Long userId, Boolean liked, Boolean favorited) {
        TopicPost post = requirePost(postId);
        return TopicInteractionResponse.builder()
                .liked(liked != null ? liked : isLiked(postId, userId))
                .likeCount(post.getLikeCount())
                .favorited(favorited != null ? favorited : isFavorited(postId, userId))
                .favoriteCount(post.getFavoriteCount())
                .build();
    }

    private void doDeleteCleanup(TopicPost post) {
        Long postId = post.getId();
        postRepository.deleteById(postId);

        likeRepository.delete(new LambdaQueryWrapper<TopicPostLike>().eq(TopicPostLike::getPostId, postId));
        favoriteRepository.delete(new LambdaQueryWrapper<TopicPostFavorite>().eq(TopicPostFavorite::getPostId, postId));
        commentRepository.delete(new LambdaQueryWrapper<TopicComment>().eq(TopicComment::getPostId, postId));

        cleanImages(postId);

        circleRepository.update(null, new LambdaUpdateWrapper<TopicCircle>()
                .eq(TopicCircle::getId, post.getCircleId())
                .setSql("post_count = GREATEST(post_count - 1, 0)"));
    }

    private void cleanImages(Long postId) {
        List<Long> imageIds = orderedImageIds(postId);
        postImageRepository.delete(new LambdaQueryWrapper<TopicPostImage>().eq(TopicPostImage::getPostId, postId));
        for (Long imageId : imageIds) {
            deleteImagePhysical(imageId);
        }
    }

    private void deleteImagePhysical(Long imageId) {
        Image img = imageRepository.selectById(imageId);
        if (img == null) {
            return;
        }
        try {
            imageService.deleteImage(imageId, img.getUserId());
        } catch (RuntimeException e) {
            log.warn("清理话题帖子图片失败: imageId={}, err={}", imageId, e.getMessage());
        }
    }

    private void syncImages(Long postId, List<Long> newIds) {
        Set<Long> oldIds = new HashSet<>(orderedImageIds(postId));
        Set<Long> newSet = new HashSet<>(newIds);

        Set<Long> removed = new HashSet<>(oldIds);
        removed.removeAll(newSet);
        for (Long imageId : removed) {
            postImageRepository.delete(new LambdaQueryWrapper<TopicPostImage>()
                    .eq(TopicPostImage::getPostId, postId)
                    .eq(TopicPostImage::getImageId, imageId));
            deleteImagePhysical(imageId);
        }

        // 重建全部关联，保证 sort 顺序
        postImageRepository.delete(new LambdaQueryWrapper<TopicPostImage>().eq(TopicPostImage::getPostId, postId));
        linkImages(postId, newIds);
    }

    private void linkImages(Long postId, List<Long> imageIds) {
        int sort = 0;
        for (Long imageId : imageIds) {
            postImageRepository.insert(TopicPostImage.builder()
                    .postId(postId)
                    .imageId(imageId)
                    .sort(sort++)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    private List<Long> orderedImageIds(Long postId) {
        return postImageRepository.selectList(new LambdaQueryWrapper<TopicPostImage>()
                        .eq(TopicPostImage::getPostId, postId)
                        .orderByAsc(TopicPostImage::getSort)).stream()
                .map(TopicPostImage::getImageId)
                .toList();
    }

    private void validateImages(List<Long> imageIds, Long userId) {
        if (imageIds.isEmpty()) {
            return;
        }
        Set<Long> valid = imageRepository.selectBatchIds(imageIds).stream()
                .filter(img -> img.getPurpose() != null
                        && img.getPurpose() == StorageConfig.PURPOSE_TOPIC_IMAGE)
                .filter(img -> img.getUserId().equals(userId))
                .map(Image::getId)
                .collect(Collectors.toSet());
        if (valid.size() != imageIds.size()) {
            throw new BusinessException(403, "正文引用了非本人上传的话题图片");
        }
    }

    private IPage<TopicPostResponse> toPage(IPage<TopicPost> raw, Long userId) {
        List<TopicPost> posts = raw.getRecords();
        if (posts.isEmpty()) {
            Page<TopicPostResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
            result.setRecords(List.of());
            return result;
        }

        List<Long> postIds = posts.stream().map(TopicPost::getId).toList();
        List<Long> circleIds = posts.stream().map(TopicPost::getCircleId).distinct().toList();
        List<Long> authorIds = posts.stream().map(TopicPost::getUserId).distinct().toList();

        Map<Long, String> circleNames = circleRepository.selectBatchIds(circleIds).stream()
                .collect(Collectors.toMap(TopicCircle::getId, TopicCircle::getName));
        Map<Long, String> authorNames = userRepository.selectBatchIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        Set<Long> likedIds = relationIds(likeRepository, userId, postIds);
        Set<Long> favoritedIds = relationIds(favoriteRepository, userId, postIds);

        Page<TopicPostResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(posts.stream().map(p -> {
            TopicPostResponse resp = TopicPostResponse.base(p);
            resp.setCircleName(circleNames.get(p.getCircleId()));
            resp.setAuthorName(authorNames.get(p.getUserId()));
            resp.setLiked(likedIds.contains(p.getId()));
            resp.setFavorited(favoritedIds.contains(p.getId()));
            return resp;
        }).toList());
        return result;
    }

    private Set<Long> relationIds(
            com.baomidou.mybatisplus.core.mapper.BaseMapper<?> relationRepository,
            Long userId, List<Long> postIds) {
        if (relationRepository == likeRepository) {
            return likeRepository.selectList(new LambdaQueryWrapper<TopicPostLike>()
                            .eq(TopicPostLike::getUserId, userId)
                            .in(TopicPostLike::getPostId, postIds)).stream()
                    .map(TopicPostLike::getPostId)
                    .collect(Collectors.toSet());
        }
        return favoriteRepository.selectList(new LambdaQueryWrapper<TopicPostFavorite>()
                        .eq(TopicPostFavorite::getUserId, userId)
                        .in(TopicPostFavorite::getPostId, postIds)).stream()
                .map(TopicPostFavorite::getPostId)
                .collect(Collectors.toSet());
    }

    private TopicPostResponse toResponse(TopicPost post, Long userId) {
        TopicPostResponse resp = TopicPostResponse.base(post);
        TopicCircle circle = circleRepository.selectById(post.getCircleId());
        resp.setCircleName(circle != null ? circle.getName() : null);
        resp.setAuthorName(username(post.getUserId()));
        resp.setLiked(isLiked(post.getId(), userId));
        resp.setFavorited(isFavorited(post.getId(), userId));
        return resp;
    }

    private TopicPost requirePost(Long id) {
        TopicPost post = postRepository.selectById(id);
        if (post == null) {
            throw new BusinessException("帖子不存在");
        }
        return post;
    }

    private boolean isLiked(Long postId, Long userId) {
        return likeRepository.selectCount(new LambdaQueryWrapper<TopicPostLike>()
                .eq(TopicPostLike::getPostId, postId)
                .eq(TopicPostLike::getUserId, userId)) > 0;
    }

    private boolean isFavorited(Long postId, Long userId) {
        return favoriteRepository.selectCount(new LambdaQueryWrapper<TopicPostFavorite>()
                .eq(TopicPostFavorite::getPostId, postId)
                .eq(TopicPostFavorite::getUserId, userId)) > 0;
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

    private Page<TopicPost> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private IPage<TopicPostResponse> emptyPage(int page, int size) {
        Page<TopicPostResponse> result = new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), 0);
        result.setRecords(List.of());
        return result;
    }
}
