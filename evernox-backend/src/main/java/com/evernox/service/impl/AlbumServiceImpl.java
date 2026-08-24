package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.AlbumRequest;
import com.evernox.dto.AlbumResponse;
import com.evernox.dto.ImageResponse;
import com.evernox.entity.Album;
import com.evernox.entity.Image;
import com.evernox.entity.ImageAlbum;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.AlbumRepository;
import com.evernox.repository.ImageAlbumRepository;
import com.evernox.repository.ImageRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.AlbumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 相册服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AlbumServiceImpl implements AlbumService {

    private final AlbumRepository albumRepository;
    private final ImageRepository imageRepository;
    private final ImageAlbumRepository imageAlbumRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public AlbumResponse createAlbum(AlbumRequest request, Long userId) {
        if (request.getCoverImageId() != null) {
            requireOwnedImage(request.getCoverImageId(), userId);
        }
        Album album = Album.builder()
                .userId(userId)
                .name(request.getName())
                .description(request.getDescription())
                .coverImageId(request.getCoverImageId())
                .visibility(request.getVisibility() != null ? request.getVisibility() : 0)
                .deleted(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        albumRepository.insert(album);
        log.info("相册创建成功: id={}, name={}, user={}", album.getId(), album.getName(), userId);

        AlbumResponse response = AlbumResponse.from(album);
        response.setCreatorName(getUsername(userId));
        response.setImageCount(0);
        return response;
    }

    @Override
    @Transactional
    public AlbumResponse updateAlbum(Long id, AlbumRequest request, Long userId) {
        Album album = albumRepository.selectById(id);
        if (album == null) {
            throw new BusinessException("相册不存在");
        }
        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权修改该相册");
        }

        album.setName(request.getName());
        album.setDescription(request.getDescription());
        if (request.getCoverImageId() != null) {
            // 封面必须是本人的图片：否则可拿他人图片 ID 当封面，构成越权引用
            requireOwnedImage(request.getCoverImageId(), album.getUserId());
            album.setCoverImageId(request.getCoverImageId());
        }
        if (request.getVisibility() != null) {
            album.setVisibility(request.getVisibility());
        }
        album.setUpdatedAt(LocalDateTime.now());

        albumRepository.updateById(album);

        AlbumResponse response = AlbumResponse.from(album);
        response.setCreatorName(getUsername(userId));
        response.setImageCount(countAlbumImages(id));
        return response;
    }

    @Override
    @Transactional
    public void deleteAlbum(Long id, Long userId) {
        Album album = albumRepository.selectById(id);
        if (album == null) {
            throw new BusinessException("相册不存在");
        }
        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除该相册");
        }

        // 逻辑删除相册
        albumRepository.deleteById(id);

        // 清理关联关系
        LambdaQueryWrapper<ImageAlbum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImageAlbum::getAlbumId, id);
        imageAlbumRepository.delete(wrapper);

        log.info("相册删除成功: id={}, user={}", id, userId);
    }

    @Override
    public IPage<AlbumResponse> getUserAlbums(Long userId, Page<Album> page) {
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Album::getUserId, userId)
               .orderByDesc(Album::getCreatedAt);

        IPage<Album> result = albumRepository.selectPage(page, wrapper);

        return result.convert(album -> {
            AlbumResponse resp = AlbumResponse.from(album);
            resp.setCreatorName(getUsername(userId));
            resp.setImageCount(countAlbumImages(album.getId()));
            return resp;
        });
    }

    @Override
    public IPage<AlbumResponse> getPublicAlbums(Page<Album> page) {
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Album::getVisibility, 1)
               .orderByDesc(Album::getCreatedAt);

        IPage<Album> result = albumRepository.selectPage(page, wrapper);

        return result.convert(album -> {
            AlbumResponse resp = AlbumResponse.from(album);
            resp.setCreatorName(getUsername(album.getUserId()));
            resp.setImageCount(countAlbumImages(album.getId()));
            return resp;
        });
    }

    @Override
    public AlbumResponse getAlbumById(Long id, Long userId) {
        Album album = albumRepository.selectById(id);
        if (album == null) {
            throw new BusinessException("相册不存在");
        }
        // 私密相册只有所有者能查看
        if (album.getVisibility() == 0 && (userId == null || !album.getUserId().equals(userId))) {
            throw new BusinessException(403, "无权访问该相册");
        }

        AlbumResponse resp = AlbumResponse.from(album);
        resp.setCreatorName(getUsername(album.getUserId()));
        resp.setImageCount(countAlbumImages(id));
        return resp;
    }

    @Override
    public IPage<ImageResponse> getAlbumImages(Long albumId, Long userId, Page<?> page) {
        Album album = albumRepository.selectById(albumId);
        if (album == null) {
            throw new BusinessException("相册不存在");
        }
        // 只有相册所有者能看到相册内的私密图片；其他人一律只看公开图。
        // 历史数据里可能已被越权塞入他人私密图，这道过滤同时兜住了那部分脏数据。
        boolean owner = userId != null && album.getUserId().equals(userId);

        LambdaQueryWrapper<ImageAlbum> iaWrapper = new LambdaQueryWrapper<>();
        iaWrapper.eq(ImageAlbum::getAlbumId, albumId);
        List<Long> imageIds = imageAlbumRepository.selectList(iaWrapper).stream()
                .map(ImageAlbum::getImageId)
                .toList();

        int current = (int) page.getCurrent();
        int size = (int) page.getSize();
        if (imageIds.isEmpty()) {
            return new Page<ImageResponse>(current, size).setTotal(0);
        }

        // 过滤与分页都交给数据库：手动 subList 分页会在过滤后出现 total 与记录数不一致
        LambdaQueryWrapper<Image> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Image::getId, imageIds);
        if (!owner) {
            wrapper.eq(Image::getVisibility, 1);
        }
        wrapper.orderByDesc(Image::getCreatedAt);

        IPage<Image> result = imageRepository.selectPage(new Page<>(current, size), wrapper);
        return result.convert(image -> {
            ImageResponse resp = ImageResponse.from(image);
            resp.setUploaderName(getUsername(image.getUserId()));
            return resp;
        });
    }

    @Override
    @Transactional
    public void addImageToAlbum(Long albumId, Long imageId, Long userId) {
        Album album = albumRepository.selectById(albumId);
        if (album == null) {
            throw new BusinessException("相册不存在");
        }
        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权修改该相册");
        }

        Image image = imageRepository.selectById(imageId);
        if (image == null) {
            throw new BusinessException("图片不存在");
        }
        // 必须校验图片归属：只校验相册归属的话，任何人都能把他人（含私密）图片 ID
        // 塞进自己的相册，再通过相册接口读到这些图片的元数据
        if (!image.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权将他人的图片加入相册");
        }

        // 检查是否已存在关联
        LambdaQueryWrapper<ImageAlbum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImageAlbum::getImageId, imageId)
               .eq(ImageAlbum::getAlbumId, albumId);
        if (imageAlbumRepository.selectCount(wrapper) > 0) {
            throw new BusinessException("图片已在该相册中");
        }

        ImageAlbum imageAlbum = ImageAlbum.builder()
                .imageId(imageId)
                .albumId(albumId)
                .createdAt(LocalDateTime.now())
                .build();
        imageAlbumRepository.insert(imageAlbum);
    }

    @Override
    @Transactional
    public void removeImageFromAlbum(Long albumId, Long imageId, Long userId) {
        Album album = albumRepository.selectById(albumId);
        if (album == null) {
            throw new BusinessException("相册不存在");
        }
        if (!album.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权修改该相册");
        }

        LambdaQueryWrapper<ImageAlbum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImageAlbum::getImageId, imageId)
               .eq(ImageAlbum::getAlbumId, albumId);
        imageAlbumRepository.delete(wrapper);
    }

    @Override
    public long countUserAlbums(Long userId) {
        LambdaQueryWrapper<Album> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Album::getUserId, userId);
        return albumRepository.selectCount(wrapper);
    }

    private int countAlbumImages(Long albumId) {
        LambdaQueryWrapper<ImageAlbum> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImageAlbum::getAlbumId, albumId);
        return imageAlbumRepository.selectCount(wrapper).intValue();
    }

    /** 校验图片存在且属于指定用户，用于封面等引用场景 */
    private void requireOwnedImage(Long imageId, Long ownerId) {
        Image image = imageRepository.selectById(imageId);
        if (image == null) {
            throw new BusinessException("封面图片不存在");
        }
        if (!image.getUserId().equals(ownerId)) {
            throw new BusinessException(403, "无权使用他人的图片作为封面");
        }
    }

    private String getUsername(Long userId) {
        User user = userRepository.selectById(userId);
        return user != null ? user.getUsername() : "未知用户";
    }
}
