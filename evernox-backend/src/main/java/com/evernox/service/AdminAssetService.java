package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.AdminAssetStatsResponse;
import com.evernox.dto.AlbumRequest;
import com.evernox.dto.AlbumResponse;
import com.evernox.dto.ImageResponse;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * 管理员资产（图片 / 相册）管理服务
 *
 * 权限模型：本服务不做归属校验，唯一防线是 AdminAssetController 的类级
 * @PreAuthorize("hasRole('admin')")。所有写操作都先查出资源所有者，
 * 再以「所有者身份」委托给 ImageService / AlbumService 执行，从而复用
 * 既有的关联清理、物理文件删除与解密逻辑，避免在此处重写一遍。
 */
public interface AdminAssetService {

    /** 管理员取图片文件（含他人私密图），同时带出 mimeType 供控制器设置响应头 */
    record ImageFile(Resource resource, String mimeType) {
    }

    /**
     * 分页查询全平台图片
     *
     * @param userId     归属用户过滤，可空
     * @param visibility 可见性过滤，可空
     * @param keyword    原始文件名 / MIME 模糊匹配，可空
     * @param albumId    限定属于某相册，可空
     * @param startDate  上传时间下界 yyyy-MM-dd，可空
     * @param endDate    上传时间上界 yyyy-MM-dd（含当天），可空
     * @param sortField  排序字段，白名单外回退为 createdAt
     * @param sortOrder  asc / desc，默认 desc
     */
    IPage<ImageResponse> listImages(int page, int size, Long userId, Integer visibility, String keyword,
                                    Long albumId, String startDate, String endDate,
                                    String sortField, String sortOrder);

    /** 图片详情 */
    ImageResponse getImage(Long id);

    /** 取图片文件（含私密） */
    ImageFile getImageFile(Long id);

    /** 图片所属相册 ID 列表 */
    List<Long> getImageAlbumIds(Long id);

    /** 修改图片可见性 */
    ImageResponse updateImageVisibility(Long id, Integer visibility);

    /** 批量修改图片可见性；先整批校验，任一不存在则整批拒绝 */
    void updateImageVisibilityBatch(List<Long> ids, Integer visibility);

    /** 删除图片（含物理文件与相册关联） */
    void deleteImage(Long id);

    /** 批量删除图片 */
    void deleteImages(List<Long> ids);

    /**
     * 分页查询全平台相册
     *
     * @param keyword 相册名 / 描述模糊匹配，可空
     */
    IPage<AlbumResponse> listAlbums(int page, int size, Long userId, Integer visibility, String keyword,
                                   String startDate, String endDate, String sortField, String sortOrder);

    /** 相册详情 */
    AlbumResponse getAlbum(Long id);

    /** 相册内图片分页 */
    IPage<ImageResponse> listAlbumImages(Long albumId, int page, int size);

    /** 更新相册（名称 / 描述 / 可见性 / 封面） */
    AlbumResponse updateAlbum(Long id, AlbumRequest request);

    /** 批量加入图片；图片必须与相册同属一个用户 */
    void addImagesToAlbum(Long albumId, List<Long> imageIds);

    /** 从相册移出图片（不删除图片本体） */
    void removeImageFromAlbum(Long albumId, Long imageId);

    /** 删除相册（不删除图片本体） */
    void deleteAlbum(Long id);

    /** 批量删除相册 */
    void deleteAlbums(List<Long> ids);

    /** 全平台统计 */
    AdminAssetStatsResponse getStats();
}
