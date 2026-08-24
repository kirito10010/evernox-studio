package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.AlbumRequest;
import com.evernox.dto.AlbumResponse;
import com.evernox.dto.ImageResponse;
import com.evernox.entity.Album;

/**
 * 相册服务接口
 */
public interface AlbumService {

    /**
     * 创建相册
     */
    AlbumResponse createAlbum(AlbumRequest request, Long userId);

    /**
     * 更新相册
     */
    AlbumResponse updateAlbum(Long id, AlbumRequest request, Long userId);

    /**
     * 删除相册
     */
    void deleteAlbum(Long id, Long userId);

    /**
     * 获取当前用户相册列表
     */
    IPage<AlbumResponse> getUserAlbums(Long userId, Page<Album> page);

    /**
     * 获取公开相册列表
     */
    IPage<AlbumResponse> getPublicAlbums(Page<Album> page);

    /**
     * 获取相册详情（含图片列表）
     */
    AlbumResponse getAlbumById(Long id, Long userId);

    /**
     * 获取相册内的图片列表
     *
     * 可见性规则：userId 为相册所有者时返回全部图片；否则只返回公开图片。
     * 管理员侧以相册所有者身份调用，因此仍可看到全部。
     */
    IPage<ImageResponse> getAlbumImages(Long albumId, Long userId, Page<?> page);

    /**
     * 向相册添加图片
     *
     * 图片与相册必须同属 userId，禁止把他人图片加入自己的相册。
     */
    void addImageToAlbum(Long albumId, Long imageId, Long userId);

    /**
     * 从相册移除图片
     */
    void removeImageFromAlbum(Long albumId, Long imageId, Long userId);

    /**
     * 统计用户相册数量
     */
    long countUserAlbums(Long userId);
}
