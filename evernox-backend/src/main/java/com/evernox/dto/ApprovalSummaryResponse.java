package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员待审批汇总
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalSummaryResponse {

    /** 网站分享待审批条数 */
    private Long sitePending;

    /** 记事本待审批条数 */
    private Long notePending;

    /** 忍者测验题目待审批条数 */
    private Long quizPending;

    /** 待审批总数 */
    public long getTotal() {
        return (sitePending == null ? 0 : sitePending)
                + (notePending == null ? 0 : notePending)
                + (quizPending == null ? 0 : quizPending);
    }
}
