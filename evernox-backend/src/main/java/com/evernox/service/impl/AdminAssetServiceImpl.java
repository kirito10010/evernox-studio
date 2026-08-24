package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.ResultCode;
import com.evernox.config.StorageConfig;
import com.evernox.dto.AdminAssetStatsResponse;
import com.evernox.dto.AlbumRequest;
import com.evernox.dto.AlbumResponse;
import com.evernox.dto.ImageResponse;
import com.evernox.entity.Album;
import com.evernox.entity.Image;
import com.evernox.entity.ImageAlbum;
import com.evernox.exception.BusinessException;
import com.evernox.repository.AlbumRepository;
import com.evernox.repository.ImageAlbumRepository;
import com.evernox.repository.ImageRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.AdminAssetService;
import com.evernox.service.AlbumService;
import com.evernox.service.ImageService;
import com.evernox.util.SortColumnResolver;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理员资产管理服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAssetServiceImpl implements AdminAssetService {

    private final ImageRepository imageRepository;
    private final AlbumRepository albumRepository;
    private final ImageAlbumRepository imageAlbumRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final AlbumService albumService;

    /** 排序字段白名单：前端传入的字符串绝不能直接拼进 SQL */
    private static final Map<String, String> IMAGE_SORT_COLUMNS = Map.of(
            "createdAt", "created_at",
            "fileSize", "file_size",
            "originalName", "original_name"
    );

    private static final Map<String, String> ALBUM_SORT_COLUMNS = Map.of(
            "createdAt", "created_at",
            "updatedAt", "updated_at",
            "name", "name"
    );

    private static final int MAX_PAGE_SIZE = 100;

    /** 批量操作的 ID 上限，防止一次请求拖垮服务 */
    private static final int MAX_BATCH_SIZE = 200;

    // ==================== 图片 ====================

    @Override
    public IPage<ImageResponse> listImages(int page, int size, Long userId, Integer visibility, String keyword,
                                           Long albumId, String startDate, String endDate,
                                           String sortField, String sortOrder) {
        QueryWrapper<Image> wrapper = new QueryWrapper<>();
        // 封面不属于图床内容，后台图片列表同样不展示
        wrapper.eq("purpose", StorageConfig.PURPOSE_PHOTO);
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (visibility != null) {
            wrapper.eq("visibility", visibility);
        }
        // 嵌套 and(...) 不可省：平铺的 or 会把上面的 user_id / visibility 条件短路掉
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("original_name", kw).or().like("mime_type", kw));
        }
        if (albumId != null) {
            // albumId 是 Long，不存在注入面；排序字段则必须走白名单
            wrapper.inSql("id", "SELECT image_id FROM image_album WHERE album_id = " + albumId);
        }
        applyDateRange(wrapper, startDate, endDate);

        String column = SortColumnResolver.resolve(IMAGE_SORT_COLUMNS, sortField, "created_at");
        wrapper.orderBy(true, "asc".equalsIgnoreCase(sortOrder), column);

        IPage<Image> result = imageRepository.selectPage(newPage(page, size), wrapper);
        Map<Long, String> nameMap = resolveUsernames(
                result.getRecords().stream().map(image -> image.getUserId()).collect(Collectors.toSet()));

        return result.convert(image -> toImageResponse(image, nameMap));
    }

    @Override
    public ImageResponse getImage(Long id) {
        Image image = requireImage(id);
        return toImageResponse(image, resolveUsernames(Set.of(image.getUserId())));
    }

    @Override
    public ImageFile getImageFile(Long id) {
        Image image = requireImage(id);
        // 以所有者身份代取，复用 ImageService 内的解密逻辑；越权由控制器的 @PreAuthorize 兜住
        log.warn("管理员读取图片文件: imageId={}, owner={}, visibility={}",
                id, image.getUserId(), image.getVisibility());
        return new ImageFile(imageService.getImageFile(id, image.getUserId()), image.getMimeType());
    }

    @Override
    public List<Long> getImageAlbumIds(Long id) {
        requireImage(id);
        return imageService.getImageAlbumIds(id);
    }

    @Override
    @Transactional
    public ImageResponse updateImageVisibility(Long id, Integer visibility) {
        Image image = requireImage(id);
        ImageResponse resp = imageService.updateVisibility(id, image.getUserId(), normalizeVisibility(visibility));
        resp.setUploaderName(resolveUsernames(Set.of(image.getUserId())).get(image.getUserId()));
        return resp;
    }

    @Override
    @Transactional
    public void updateImageVisibilityBatch(List<Long> ids, Integer visibility) {
        List<Image> images = requireImages(ids);
        int target = normalizeVisibility(visibility);
        for (Image image : images) {
            imageService.updateVisibility(image.getId(), image.getUserId(), target);
        }
        log.info("管理员批量修改图片可见性: count={}, visibility={}", images.size(), target);
    }

    @Override
    @Transactional
    public void deleteImage(Long id) {
        Image image = requireImage(id);
        imageService.deleteImage(id, image.getUserId());
    }

    @Override
    @Transactional
    public void deleteImages(List<Long> ids) {
        // 先整批校验再执行，避免删一半失败留下半成功状态
        List<Image> images = requireImages(ids);
        for (Image image : images) {
            imageService.deleteImage(image.getId(), image.getUserId());
        }
        log.info("管理员批量删除图片: count={}", images.size());
    }

    // ==================== 相册 ====================

    @Override
    public IPage<AlbumResponse> listAlbums(int page, int size, Long userId, Integer visibility, String keyword,
                                           String startDate, String endDate, String sortField, String sortOrder) {
        QueryWrapper<Album> wrapper = new QueryWrapper<>();
        if (userId != null) {
            wrapper.eq("user_id", userId);
        }
        if (visibility != null) {
            wrapper.eq("visibility", visibility);
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("name", kw).or().like("description", kw));
        }
        applyDateRange(wrapper, startDate, endDate);

        String column = SortColumnResolver.resolve(ALBUM_SORT_COLUMNS, sortField, "created_at");
        wrapper.orderBy(true, "asc".equalsIgnoreCase(sortOrder), column);

        IPage<Album> result = albumRepository.selectPage(newPage(page, size), wrapper);
        List<Album> records = result.getRecords();
        Map<Long, String> nameMap = resolveUsernames(
                records.stream().map(album -> album.getUserId()).collect(Collectors.toSet()));
        Map<Long, Integer> countMap = countAlbumImages(
                records.stream().map(album -> album.getId()).collect(Collectors.toSet()));

        return result.convert(album -> {
            AlbumResponse resp = AlbumResponse.from(album);
            resp.setCreatorName(nameMap.getOrDefault(album.getUserId(), "已注销"));
            resp.setImageCount(countMap.getOrDefault(album.getId(), 0));
            return resp;
        });
    }

    @Override
    public AlbumResponse getAlbum(Long id) {
        Album album = requireAlbum(id);
        AlbumResponse resp = AlbumResponse.from(album);
        resp.setCreatorName(resolveUsernames(Set.of(album.getUserId())).getOrDefault(album.getUserId(), "已注销"));
        resp.setImageCount(countAlbumImages(Set.of(id)).getOrDefault(id, 0));
        return resp;
    }

    @Override
    public IPage<ImageResponse> listAlbumImages(Long albumId, int page, int size) {
        Album album = requireAlbum(albumId);
        IPage<ImageResponse> result = albumService.getAlbumImages(albumId, album.getUserId(), newPage(page, size));
        String owner = resolveUsernames(Set.of(album.getUserId())).getOrDefault(album.getUserId(), "已注销");
        result.getRecords().forEach(resp -> resp.setUploaderName(owner));
        return result;
    }

    @Override
    @Transactional
    public AlbumResponse updateAlbum(Long id, AlbumRequest request) {
        Album album = requireAlbum(id);
        if (request.getCoverImageId() != null) {
            requireImageInAlbum(id, request.getCoverImageId());
        }
        AlbumResponse resp = albumService.updateAlbum(id, request, album.getUserId());
        resp.setCreatorName(resolveUsernames(Set.of(album.getUserId())).getOrDefault(album.getUserId(), "已注销"));
        resp.setImageCount(countAlbumImages(Set.of(id)).getOrDefault(id, 0));
        return resp;
    }

    @Override
    @Transactional
    public void addImagesToAlbum(Long albumId, List<Long> imageIds) {
        Album album = requireAlbum(albumId);
        List<Image> images = requireImages(imageIds);
        for (Image image : images) {
            // 既有 addImageToAlbum 只校验相册归属，跨用户加图会造出「A 的相册里挂着 B 的私密图」
            if (!image.getUserId().equals(album.getUserId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "图片与相册不属于同一用户，不能跨用户加入");
            }
        }
        for (Image image : images) {
            albumService.addImageToAlbum(albumId, image.getId(), album.getUserId());
        }
        log.info("管理员向相册加入图片: albumId={}, count={}", albumId, images.size());
    }

    @Override
    @Transactional
    public void removeImageFromAlbum(Long albumId, Long imageId) {
        Album album = requireAlbum(albumId);
        albumService.removeImageFromAlbum(albumId, imageId, album.getUserId());
    }

    @Override
    @Transactional
    public void deleteAlbum(Long id) {
        Album album = requireAlbum(id);
        albumService.deleteAlbum(id, album.getUserId());
    }

    @Override
    @Transactional
    public void deleteAlbums(List<Long> ids) {
        List<Album> albums = requireAlbums(ids);
        for (Album album : albums) {
            albumService.deleteAlbum(album.getId(), album.getUserId());
        }
        log.info("管理员批量删除相册: count={}", albums.size());
    }

    // ==================== 统计 ====================

    @Override
    public AdminAssetStatsResponse getStats() {
        // 张数与列表口径保持一致：只算图床照片。容量则含封面，见 sumImageSize
        return AdminAssetStatsResponse.builder()
                .totalImages(imageRepository.selectCount(photoOnly()))
                .privateImages(imageRepository.selectCount(photoOnly().eq("visibility", 0)))
                .totalAlbums(albumRepository.selectCount(null))
                .privateAlbums(albumRepository.selectCount(new QueryWrapper<Album>().eq("visibility", 0)))
                .imagesUsedBytes(sumImageSize())
                .build();
    }

    /** 只取图床照片（排除相册封面与网站封面） */
    private QueryWrapper<Image> photoOnly() {
        return new QueryWrapper<Image>().eq("purpose", StorageConfig.PURPOSE_PHOTO);
    }

    // ==================== 私有方法 ====================

    private Image requireImage(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "图片ID不能为空");
        }
        Image image = imageRepository.selectById(id);
        if (image == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "图片不存在");
        }
        return image;
    }

    private Album requireAlbum(Long id) {
        if (id == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "相册ID不能为空");
        }
        Album album = albumRepository.selectById(id);
        if (album == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "相册不存在");
        }
        return album;
    }

    /** 整批校验：任一 ID 不存在则整批拒绝，避免半成功 */
    private List<Image> requireImages(List<Long> ids) {
        checkBatchSize(ids);
        return ids.stream().distinct().map(this::requireImage).toList();
    }

    private List<Album> requireAlbums(List<Long> ids) {
        checkBatchSize(ids);
        return ids.stream().distinct().map(this::requireAlbum).toList();
    }

    private void checkBatchSize(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择至少一项");
        }
        if (ids.size() > MAX_BATCH_SIZE) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "单次最多操作 " + MAX_BATCH_SIZE + " 项");
        }
    }

    private void requireImageInAlbum(Long albumId, Long imageId) {
        QueryWrapper<ImageAlbum> wrapper = new QueryWrapper<>();
        wrapper.eq("album_id", albumId).eq("image_id", imageId);
        if (imageAlbumRepository.selectCount(wrapper) == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "封面必须是相册内的图片");
        }
    }

    private int normalizeVisibility(Integer visibility) {
        return visibility != null && visibility == 1 ? 1 : 0;
    }

    private <T> Page<T> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private void applyDateRange(QueryWrapper<?> wrapper, String startDate, String endDate) {
        LocalDate start = parseDate(startDate);
        if (start != null) {
            wrapper.ge("created_at", start.atStartOfDay());
        }
        LocalDate end = parseDate(endDate);
        if (end != null) {
            // 上界取次日 00:00 的开区间，避免 created_at 带时分秒时漏掉当天记录
            wrapper.lt("created_at", end.plusDays(1).atStartOfDay());
        }
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
    }

    private ImageResponse toImageResponse(Image image, Map<Long, String> nameMap) {
        ImageResponse resp = ImageResponse.from(image);
        resp.setUploaderName(nameMap.getOrDefault(image.getUserId(), "已注销"));
        return resp;
    }

    /** 一次查完本页涉及的用户名，避免逐行查库的 N+1 */
    private Map<Long, String> resolveUsernames(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(user -> user.getId(), user -> user.getUsername()));
    }

    /** 一条 GROUP BY 聚合出本页相册的图片数 */
    private Map<Long, Integer> countAlbumImages(Collection<Long> albumIds) {
        if (albumIds == null || albumIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<ImageAlbum> wrapper = new QueryWrapper<>();
        wrapper.select("album_id AS albumId", "COUNT(*) AS cnt")
                .in("album_id", albumIds)
                .groupBy("album_id");

        Map<Long, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : imageAlbumRepository.selectMaps(wrapper)) {
            Object albumId = row.get("albumId");
            Object cnt = row.get("cnt");
            if (albumId instanceof Number && cnt instanceof Number) {
                counts.put(((Number) albumId).longValue(), ((Number) cnt).intValue());
            }
        }
        return counts;
    }

    private long sumImageSize() {
        QueryWrapper<Image> wrapper = new QueryWrapper<>();
        // 不按 purpose 过滤：封面同样占磁盘
        wrapper.select("IFNULL(SUM(file_size), 0) AS total");
        List<Map<String, Object>> rows = imageRepository.selectMaps(wrapper);
        if (rows.isEmpty()) {
            return 0L;
        }
        Object total = rows.get(0).get("total");
        return total instanceof Number ? ((Number) total).longValue() : 0L;
    }
}
