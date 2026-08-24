package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.SiteLinkResponse;
import com.evernox.dto.SiteStatsResponse;
import com.evernox.dto.SiteTagRequest;
import com.evernox.dto.SiteTagResponse;

import java.util.List;

/**
 * 网站分享审批服务接口（管理员侧）
 */
public interface AdminSiteService {

    /** 全量站点列表 */
    IPage<SiteLinkResponse> listSites(int page, int size, Integer status, Long userId,
                                      String keyword, String sortField, String sortOrder);

    /** 审批通过：打标签 + 转公开 */
    void approve(Long id, List<Long> tagIds, Long adminId);

    /** 审批驳回：必须写原因 */
    void reject(Long id, String reason, Long adminId);

    /** 撤下已公开站点 */
    void offline(Long id, Long adminId);

    /** 调整已公开站点的标签 */
    void updateTags(Long id, List<Long> tagIds);

    /** 标签库列表（带关联站点数） */
    List<SiteTagResponse> listTags();

    /** 新建标签 */
    SiteTagResponse createTag(SiteTagRequest request);

    /** 更新标签 */
    SiteTagResponse updateTag(Long id, SiteTagRequest request);

    /** 删除标签（级联清理关联） */
    void deleteTag(Long id);

    /** 全站审批统计 */
    SiteStatsResponse getStats();
}
