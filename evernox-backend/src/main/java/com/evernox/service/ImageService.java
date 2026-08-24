package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.ImageResponse;
import com.evernox.dto.ImageUploadRequest;
import com.evernox.dto.StorageStatsResponse;
import com.evernox.entity.Image;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片服务接口
 */
public interface ImageService {

    /**
     * 上传加密图片
     * @param file 已加密的文件（密文）
     * @param request 上传元数据
     * @param userId 当前用户ID
     * @return 图片响应
     */
    ImageResponse uploadImage(MultipartFile file, ImageUploadRequest request, Long userId);

    /**
     * 获取当前用户图片列表（分页）
     */
    IPage<ImageResponse> getUserImages(Long userId, Page<Image> page);

    /**
     * 获取公开图片列表（分页）
     */
    IPage<ImageResponse> getPublicImages(Page<Image> page);

    /**
     * 获取图片详情
     */
    ImageResponse getImageById(Long id, Long userId);

    /**
     * 获取加密图片文件资源（供前端下载后解密）
     */
    Resource getImageFile(Long id, Long userId);

    /**
     * 获取缩略图文件资源；无缩略图时回退返回原图
     */
    Resource getThumbnailFile(Long id, Long userId);

    /**
     * 获取公告插图文件资源（任何登录用户可访问，仅放行 purpose=公告插图）
     */
    Resource getAnnouncementImageFile(Long id, Long userId);

    /**
     * 获取公告插图 MIME 类型（仅放行 purpose=公告插图）
     */
    String getAnnouncementImageMimeType(Long id);

    /**
     * 获取话题帖子插图文件资源（任何登录用户可访问，仅放行 purpose=话题帖子图）
     */
    Resource getTopicImageFile(Long id, Long userId);

    /**
     * 获取话题帖子插图 MIME 类型（仅放行 purpose=话题帖子图）
     */
    String getTopicImageMimeType(Long id);

    /**
     * 删除图片
     */
    void deleteImage(Long id, Long userId);

    /**
     * 统计用户图片数量
     */
    long countUserImages(Long userId);

    /**
     * 统计公开图片数量
     */
    long countPublicImages();

    /**
     * 更新图片可见性
     * @param id 图片ID
     * @param userId 当前用户ID
     * @param visibility 0私密/1公开
     * @return 更新后的图片响应
     */
    ImageResponse updateVisibility(Long id, Long userId, Integer visibility);

    /**
     * 系统级调整图片可见性（不校验所有者）
     *
     * 仅供网站分享审批流程调用：审批人不是封面图片的所有者，无法走 updateVisibility。
     * 图片不存在时静默返回，避免站点没有封面或封面已被删除时中断审批。
     *
     * @param imageId 图片ID，可为 null
     * @param visibility 0私密/1公开
     */
    void setVisibilityBySystem(Long imageId, Integer visibility);

    /**
     * 校正图片的真实像素尺寸
     *
     * 仅资源所有者可调用。这两个字段只用于前端占位比例，不参与鉴权与配额。
     *
     * @param id 图片ID
     * @param userId 当前用户ID
     * @param width 真实宽度
     * @param height 真实高度
     */
    void updateDimensions(Long id, Long userId, Integer width, Integer height);


    /**
     * 获取图片所属的相册ID列表
     * @param imageId 图片ID
     * @return 相册ID列表
     */
    java.util.List<Long> getImageAlbumIds(Long imageId);

    /**
     * 获取存储空间统计：数据目录所在磁盘容量 + 当前用户照片占用
     * @param userId 当前用户ID
     * @return 存储统计
     */
    StorageStatsResponse getStorageStats(Long userId);
}
