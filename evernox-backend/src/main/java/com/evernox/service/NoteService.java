package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.NoteRequest;
import com.evernox.dto.NoteResponse;
import com.evernox.dto.NoteStatsResponse;

/**
 * 记事本服务（用户侧）
 *
 * 状态流转与网站分享一致，见 NoteStatus。
 */
public interface NoteService {

    NoteResponse create(NoteRequest request, Long userId);

    NoteResponse update(Long id, NoteRequest request, Long userId);

    void delete(Long id, Long userId);

    /** 置顶开关 */
    NoteResponse setPinned(Long id, boolean pinned, Long userId);

    /**
     * 我的笔记分页（置顶优先，其次按更新时间倒序）
     *
     * @param keyword 匹配标题与摘要
     * @param status  null 表示全部
     */
    IPage<NoteResponse> listMine(Long userId, String keyword, Integer status, int page, int size);

    /** 公开笔记分页 */
    IPage<NoteResponse> listPublic(String keyword, int page, int size);

    /** 详情（含正文），非公开笔记仅作者与管理员可见 */
    NoteResponse getById(Long id, Long userId);

    /** 申请公开：私有/已驳回 -> 待审批 */
    void submit(Long id, Long userId);

    /** 撤回：待审批/已公开 -> 私有 */
    void withdraw(Long id, Long userId);

    NoteStatsResponse getStats(Long userId);
}
