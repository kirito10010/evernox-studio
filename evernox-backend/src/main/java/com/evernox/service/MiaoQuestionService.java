package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.MiaoImportResponse;
import com.evernox.dto.MiaoPendingResponse;
import com.evernox.dto.MiaoQuestionRequest;
import com.evernox.dto.MiaoQuestionResponse;
import com.evernox.dto.QuizVoteRequest;
import com.evernox.dto.QuizVoteResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 火影忍者OL 喵问答题库服务
 */
public interface MiaoQuestionService {

    // ==================== 用户侧 ====================

    /** 模糊搜索已通过题目；userId 用于标记当前用户已投的答案 */
    List<MiaoQuestionResponse> search(String keyword, Long userId);

    /** 待确认（无答案）题目面板：总数量 + 当前用户还能作答的题目 */
    MiaoPendingResponse unanswered(int limit, Long userId);

    /** 对无答案题目投票；连续 3 位用户选择一致则写入答案并奖励积分 */
    QuizVoteResponse vote(QuizVoteRequest request, Long userId);

    /** 用户提交题目（待审批，不含图片） */
    MiaoQuestionResponse submit(MiaoQuestionRequest request, Long userId);

    // ==================== 管理员侧 ====================

    IPage<MiaoQuestionResponse> list(int page, int size, Integer status, String keyword,
                                     String startDate, String endDate, String sortField, String sortOrder);

    MiaoQuestionResponse create(MiaoQuestionRequest request);

    MiaoQuestionResponse update(Long id, MiaoQuestionRequest request);

    void delete(Long id);

    void batchDelete(List<Long> ids);

    void approve(Long id);

    void reject(Long id);

    /** 导入整个题库文件夹：questions.json + 图片 */
    MiaoImportResponse importFolder(MultipartFile manifest, List<MultipartFile> images);

    // ==================== 我的提交（用户侧） ====================

    List<MiaoQuestionResponse> listMine(Long userId);

    MiaoQuestionResponse updateMine(Long id, MiaoQuestionRequest request, Long userId);

    void deleteMine(Long id, Long userId);

    void resubmit(Long id, Long userId);
}
