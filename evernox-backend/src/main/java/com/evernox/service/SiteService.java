package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.SiteLinkRequest;
import com.evernox.dto.SiteLinkResponse;
import com.evernox.dto.SiteStatsResponse;
import com.evernox.dto.SiteTagResponse;

import java.util.List;

/**
 * 网站分享服务接口（用户侧）
 */
public interface SiteService {

    /** 新建分享，强制落为私有状态 */
    SiteLinkResponse create(SiteLinkRequest request, Long userId);

    /** 编辑分享，仅私有/已驳回状态可改 */
    SiteLinkResponse update(Long id, SiteLinkRequest request, Long userId);

    /** 删除分享 */
    void delete(Long id, Long userId);

    /** 申请公开：私有/已驳回 -> 待审批 */
    void submit(Long id, Long userId);

    /** 撤回申请或自行撤下：待审批/已公开 -> 私有 */
    void withdraw(Long id, Long userId);

    /** 我的分享列表，status 为 null 时不过滤 */
    IPage<SiteLinkResponse> listMine(Long userId, Integer status, int page, int size);

    /** 公开导航列表，仅已公开站点 */
    IPage<SiteLinkResponse> listPublic(String keyword, List<Long> tagIds, int page, int size);

    /** 详情：公开站点所有人可见，非公开仅所有者与管理员可见 */
    SiteLinkResponse getById(Long id, Long userId);

    /** 标签库（只读，供公开页筛选） */
    List<SiteTagResponse> listTags();

    /** 当前用户的分享统计 */
    SiteStatsResponse getStats(Long userId);
}
