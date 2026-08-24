package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.QuizImportResponse;
import com.evernox.dto.QuizQuestionRequest;
import com.evernox.dto.QuizQuestionResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 火影忍者OL测验题目服务接口
 */
public interface QuizQuestionService {

    // ==================== 用户侧 ====================

    /** 模糊搜索已通过题目 */
    List<QuizQuestionResponse> search(String keyword);

    /** 用户提交题目（待审批） */
    QuizQuestionResponse submit(QuizQuestionRequest request, Long userId);

    // ==================== 管理员侧 ====================

    IPage<QuizQuestionResponse> list(int page, int size, Integer status, String keyword);

    QuizQuestionResponse create(QuizQuestionRequest request);

    QuizQuestionResponse update(Long id, QuizQuestionRequest request);

    void delete(Long id);

    void batchDelete(List<Long> ids);

    void approve(Long id);

    void reject(Long id);

    QuizImportResponse importExcel(MultipartFile file);

    // ==================== 我的提交（用户侧） ====================

    List<QuizQuestionResponse> listMine(Long userId);

    QuizQuestionResponse updateMine(Long id, QuizQuestionRequest request, Long userId);

    void deleteMine(Long id, Long userId);

    void resubmit(Long id, Long userId);
}
