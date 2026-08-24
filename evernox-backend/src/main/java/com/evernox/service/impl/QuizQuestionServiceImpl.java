package com.evernox.service.impl;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.QuizImportResponse;
import com.evernox.dto.QuizQuestionRequest;
import com.evernox.dto.QuizQuestionResponse;
import com.evernox.entity.QuizQuestion;
import com.evernox.exception.BusinessException;
import com.evernox.repository.QuizQuestionRepository;
import com.evernox.service.QuizQuestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 火影忍者OL测验题目服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class QuizQuestionServiceImpl implements QuizQuestionService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    private static final int MAX_PAGE_SIZE = 100;
    private static final double MIN_SCORE = 0.3;
    private static final int MAX_SEARCH_RESULTS = 20;

    private final QuizQuestionRepository questionRepository;

    // ==================== 用户侧 ====================

    @Override
    public List<QuizQuestionResponse> search(String keyword) {
        String query = normalize(keyword);
        if (query.isEmpty()) {
            return List.of();
        }
        List<QuizQuestion> all = questionRepository.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getStatus, STATUS_APPROVED));

        record Scored(QuizQuestion question, double score) {
        }

        return all.stream()
                .map(item -> new Scored(item, similarity(query, normalize(item.getQuestion()))))
                .filter(s -> s.score() >= MIN_SCORE)
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(MAX_SEARCH_RESULTS)
                .map(s -> {
                    QuizQuestionResponse resp = QuizQuestionResponse.from(s.question());
                    resp.setScore(s.score());
                    return resp;
                })
                .toList();
    }

    @Override
    @Transactional
    public QuizQuestionResponse submit(QuizQuestionRequest request, Long userId) {
        String normalized = normalize(request.getQuestion());
        if (normalized.isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        ensureNotDuplicate(normalized, null);
        QuizQuestion q = build(request, normalized, STATUS_PENDING);
        q.setCreatedBy(userId);
        questionRepository.insert(q);
        log.info("测验题目提交: id={}, user={}", q.getId(), userId);
        return QuizQuestionResponse.from(q);
    }

    // ==================== 管理员侧 ====================

    @Override
    public IPage<QuizQuestionResponse> list(int page, int size, Integer status, String keyword) {
        LambdaQueryWrapper<QuizQuestion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, QuizQuestion::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(QuizQuestion::getQuestion, keyword.trim());
        }
        wrapper.orderByDesc(QuizQuestion::getId);
        IPage<QuizQuestion> raw = questionRepository.selectPage(newPage(page, size), wrapper);
        return raw.convert(QuizQuestionResponse::from);
    }

    @Override
    @Transactional
    public QuizQuestionResponse create(QuizQuestionRequest request) {
        String normalized = normalize(request.getQuestion());
        if (normalized.isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        ensureNotDuplicate(normalized, null);
        QuizQuestion q = build(request, normalized, STATUS_APPROVED);
        questionRepository.insert(q);
        return QuizQuestionResponse.from(q);
    }

    @Override
    @Transactional
    public QuizQuestionResponse update(Long id, QuizQuestionRequest request) {
        QuizQuestion q = require(id);
        String normalized = normalize(request.getQuestion());
        if (normalized.isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        ensureNotDuplicate(normalized, id);
        apply(q, request, normalized);
        questionRepository.updateById(q);
        return QuizQuestionResponse.from(q);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        require(id);
        questionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的题目");
        }
        List<Long> distinct = ids.stream().distinct().toList();
        questionRepository.delete(new LambdaQueryWrapper<QuizQuestion>().in(QuizQuestion::getId, distinct));
    }

    @Override
    @Transactional
    public void approve(Long id) {
        QuizQuestion q = require(id);
        if (q.getStatus() == null || q.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该题目不在待审批状态");
        }
        q.setStatus(STATUS_APPROVED);
        questionRepository.updateById(q);
    }

    @Override
    @Transactional
    public void reject(Long id) {
        QuizQuestion q = require(id);
        if (q.getStatus() == null || q.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该题目不在待审批状态");
        }
        q.setStatus(STATUS_REJECTED);
        questionRepository.updateById(q);
    }

    @Override
    @Transactional
    public QuizImportResponse importExcel(MultipartFile file) {
        List<QuizQuestionRequest> rows = parseExcel(file);
        int imported = 0;
        int skipped = 0;
        Set<String> seen = new HashSet<>();
        for (QuizQuestionRequest row : rows) {
            String normalized = normalize(row.getQuestion());
            if (normalized.isEmpty()) {
                skipped++;
                continue;
            }
            Long count = questionRepository.selectCount(new LambdaQueryWrapper<QuizQuestion>()
                    .eq(QuizQuestion::getNormalizedQuestion, normalized));
            if ((count != null && count > 0) || !seen.add(normalized)) {
                skipped++;
                continue;
            }
            questionRepository.insert(build(row, normalized, STATUS_APPROVED));
            imported++;
        }
        log.info("测验题目导入: imported={}, skipped={}", imported, skipped);
        return QuizImportResponse.builder().imported(imported).skipped(skipped).build();
    }

    // ==================== 我的提交 ====================

    @Override
    public List<QuizQuestionResponse> listMine(Long userId) {
        return questionRepository.selectList(new LambdaQueryWrapper<QuizQuestion>()
                        .eq(QuizQuestion::getCreatedBy, userId)
                        .orderByDesc(QuizQuestion::getId)).stream()
                .map(QuizQuestionResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public QuizQuestionResponse updateMine(Long id, QuizQuestionRequest request, Long userId) {
        QuizQuestion q = requireOwnSubmission(id, userId);
        if (q.getStatus() == null || (q.getStatus() != STATUS_PENDING && q.getStatus() != STATUS_REJECTED)) {
            throw new BusinessException("已通过的题目无法修改");
        }
        String normalized = normalize(request.getQuestion());
        if (normalized.isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        ensureNotDuplicate(normalized, id);
        apply(q, request, normalized);
        q.setStatus(STATUS_PENDING);
        questionRepository.updateById(q);
        return QuizQuestionResponse.from(q);
    }

    @Override
    @Transactional
    public void deleteMine(Long id, Long userId) {
        QuizQuestion q = requireOwnSubmission(id, userId);
        if (q.getStatus() != null && q.getStatus() == STATUS_APPROVED) {
            throw new BusinessException("已通过的题目无法删除");
        }
        questionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void resubmit(Long id, Long userId) {
        QuizQuestion q = requireOwnSubmission(id, userId);
        if (q.getStatus() == null || q.getStatus() != STATUS_REJECTED) {
            throw new BusinessException("仅被驳回的题目可重新提交");
        }
        q.setStatus(STATUS_PENDING);
        questionRepository.updateById(q);
    }

    // ==================== 内部方法 ====================

    private QuizQuestion require(Long id) {
        QuizQuestion q = questionRepository.selectById(id);
        if (q == null) {
            throw new BusinessException("题目不存在");
        }
        return q;
    }

    private QuizQuestion requireOwnSubmission(Long id, Long userId) {
        QuizQuestion q = require(id);
        if (userId == null || !userId.equals(q.getCreatedBy())) {
            throw new BusinessException(403, "无权操作该题目");
        }
        return q;
    }

    private void ensureNotDuplicate(String normalized, Long excludeId) {
        Long count = questionRepository.selectCount(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getNormalizedQuestion, normalized)
                .ne(excludeId != null, QuizQuestion::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("该题目已存在");
        }
    }

    private QuizQuestion build(QuizQuestionRequest request, String normalized, int status) {
        return QuizQuestion.builder()
                .question(request.getQuestion().trim())
                .normalizedQuestion(normalized)
                .optionA(request.getOptionA().trim())
                .optionB(request.getOptionB().trim())
                .optionC(request.getOptionC().trim())
                .optionD(request.getOptionD().trim())
                .answer(request.getAnswer().trim())
                .status(status)
                .deleted(0)
                .build();
    }

    private void apply(QuizQuestion q, QuizQuestionRequest request, String normalized) {
        q.setQuestion(request.getQuestion().trim());
        q.setNormalizedQuestion(normalized);
        q.setOptionA(request.getOptionA().trim());
        q.setOptionB(request.getOptionB().trim());
        q.setOptionC(request.getOptionC().trim());
        q.setOptionD(request.getOptionD().trim());
        q.setAnswer(request.getAnswer().trim());
    }

    /** 去标点/空白/全角转半角、转小写，仅保留中文/字母/数字 */
    private String normalize(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0xFF01 && c <= 0xFF5E) {
                c = (char) (c - 0xFEE0);
            }
            if (Character.isLetterOrDigit(c)) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }

    private double similarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        if (a.contains(b) || b.contains(a)) {
            return 1.0;
        }
        int dist = levenshtein(a, b);
        return 1.0 - (double) dist / Math.max(a.length(), b.length());
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private List<QuizQuestionRequest> parseExcel(MultipartFile file) {
        List<QuizQuestionRequest> rows = new ArrayList<>();
        try (InputStream in = file.getInputStream()) {
            ExcelReader reader = ExcelUtil.getReader(in);
            List<Map<String, Object>> maps = reader.readAll();
            for (Map<String, Object> map : maps) {
                QuizQuestionRequest req = new QuizQuestionRequest();
                req.setQuestion(str(map.get("问题")));
                req.setOptionA(str(map.get("选项A")));
                req.setOptionB(str(map.get("选项B")));
                req.setOptionC(str(map.get("选项C")));
                req.setOptionD(str(map.get("选项D")));
                req.setAnswer(str(map.get("答案")));
                rows.add(req);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel 解析失败: {}", e.getMessage(), e);
            throw new BusinessException("Excel 格式不正确，请使用参考格式（第一行：问题/选项A/选项B/选项C/选项D/答案）");
        }
        return rows;
    }

    private String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof Number n) {
            // 整数去掉 .0
            return n.doubleValue() == Math.floor(n.doubleValue()) && !Double.isInfinite(n.doubleValue())
                    ? String.valueOf(n.longValue())
                    : String.valueOf(n.doubleValue());
        }
        return String.valueOf(o).trim();
    }

    private Page<QuizQuestion> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
