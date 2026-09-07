package com.evernox.service;

import com.evernox.dto.ApprovalSummaryResponse;

/**
 * 管理员待审批服务
 */
public interface AdminApprovalService {

    /** 查询三类待审批条数 */
    ApprovalSummaryResponse getSummary();

    /** 通知管理员有新的待审批内容 */
    void notifyPending(String type);
}
