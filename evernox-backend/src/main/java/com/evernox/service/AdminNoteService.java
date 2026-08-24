package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.NoteResponse;
import com.evernox.dto.NoteStatsResponse;

/**
 * 记事本审批服务（管理员）
 */
public interface AdminNoteService {

    IPage<NoteResponse> listNotes(int page, int size, Integer status, String keyword);

    /** 详情：管理员可看任意笔记正文，用于审批前阅读 */
    NoteResponse getById(Long id);

    /** 通过：转为已公开，并把正文插图置为公开可见 */
    void approve(Long id, Long adminId);

    /** 驳回：转为已驳回并记录原因，插图收回私密 */
    void reject(Long id, String reason, Long adminId);

    /** 撤下：已公开转回私有，插图收回私密 */
    void offline(Long id, Long adminId);

    NoteStatsResponse getStats();
}
