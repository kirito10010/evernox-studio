package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.QuizImportItem;
import com.evernox.dto.QuizImportResponse;
import com.evernox.dto.QuizPendingResponse;
import com.evernox.dto.QuizQuestionRequest;
import com.evernox.dto.QuizQuestionResponse;
import com.evernox.dto.QuizVoteRequest;
import com.evernox.dto.QuizVoteResponse;


import java.util.List;

/**
 * 火影忍者OL测验题目服务接口
 */
public interface QuizQuestionService {

    // ==================== 用户侧 ====================

    /** 模糊搜索已通过题目；userId 用于标记当前用户已投的答案 */
    List<QuizQuestionResponse> search(String keyword, Long userId);

    /** 待确认（无答案）题目面板：总数量 + 当前用户还能作答的题目 */
    QuizPendingResponse unanswered(int limit, Long userId);


    /** 对无答案题目投票；同一答案达到 2 票自动写入答案并奖励积分 */
    QuizVoteResponse vote(QuizVoteRequest request, Long userId);

    /** 用户提交题目（待审批） */
    QuizQuestionResponse submit(QuizQuestionRequest request, Long userId);


    // ==================== 管理员侧 ====================

    IPage<QuizQuestionResponse> list(int page, int size, Integer status, String keyword,
                                     String startDate, String endDate, String sortField, String sortOrder);

    QuizQuestionResponse create(QuizQuestionRequest request);

    QuizQuestionResponse update(Long id, QuizQuestionRequest request);

    void delete(Long id);

    void batchDelete(List<Long> ids);

    void approve(Long id);

    void reject(Long id);

    QuizImportResponse importJson(List<QuizImportItem> items);


    // ==================== 我的提交（用户侧） ====================

    List<QuizQuestionResponse> listMine(Long userId);

    QuizQuestionResponse updateMine(Long id, QuizQuestionRequest request, Long userId);

    void deleteMine(Long id, Long userId);

    void resubmit(Long id, Long userId);
}
