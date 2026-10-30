package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.ReleaseDetailResponse;
import com.evernox.dto.ReleaseRequest;
import com.evernox.dto.ReleaseSummaryResponse;
import com.evernox.entity.ReleaseAsset;

/**
 * 版本发布服务
 */
public interface ReleaseService {

    IPage<ReleaseSummaryResponse> list(Long projectId, int page, int size,
                                       String keyword, Integer status, Boolean latest, String sort,
                                       Long ownerId);

    ReleaseDetailResponse get(Long id, Long ownerId);

    ReleaseDetailResponse create(ReleaseRequest request, Long ownerId);

    ReleaseDetailResponse update(Long id, ReleaseRequest request, Long ownerId);

    void delete(Long id, Long ownerId);

    ReleaseDetailResponse publish(Long id, boolean latest, Long ownerId);

    ReleaseDetailResponse setLatest(Long id, boolean latest, Long ownerId);

    /** 管理端预览 latest.yml（仅已发布版本） */
    String previewLatestYml(Long id, Long ownerId);


    /** 公开接口：按项目 slug 生成最新版本清单，返回 null 表示不存在 */
    String buildPublicLatestYml(String slug);

    /** 公开接口：按 slug + 文件名找最新已发布版本的附件 */
    ReleaseAsset findPublicAsset(String slug, String fileName);

    void incrementDownload(Long assetId);
}
