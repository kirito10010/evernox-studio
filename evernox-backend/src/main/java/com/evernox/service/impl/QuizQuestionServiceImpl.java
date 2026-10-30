package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.ResultCode;
import com.evernox.dto.QuizImportItem;
import com.evernox.dto.QuizImportResponse;
import com.evernox.dto.QuizPendingResponse;
import com.evernox.dto.QuizQuestionRequest;

import com.evernox.dto.QuizQuestionResponse;
import com.evernox.dto.QuizVoteRequest;
import com.evernox.dto.QuizVoteResponse;
import com.evernox.entity.QuizAnswerVote;
import com.evernox.entity.QuizQuestion;
import com.evernox.exception.BusinessException;
import com.evernox.repository.QuizAnswerVoteRepository;
import com.evernox.repository.QuizQuestionRepository;
import com.evernox.service.AdminApprovalService;
import com.evernox.service.PointsService;
import com.evernox.service.QuizQuestionService;
import com.evernox.util.SortColumnResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;



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
    private static final int MAX_PENDING_RESULTS = 50;
    private static final int QUIZ_CONSENSUS_POINTS = 10;


    /** 排序字段白名单 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "created_at"
    );

    private final QuizQuestionRepository questionRepository;
    private final QuizAnswerVoteRepository voteRepository;
    private final AdminApprovalService adminApprovalService;
    private final PointsService pointsService;


    // ==================== 用户侧 ====================

    @Override
    public List<QuizQuestionResponse> search(String keyword, Long userId) {
        String query = normalize(keyword);
        if (query.isEmpty()) {
            return List.of();
        }
        List<QuizQuestion> all = questionRepository.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getStatus, STATUS_APPROVED));
        Map<Long, String> myVotes = myVotes(userId);

        record Scored(QuizQuestion question, double score) {
        }

        return all.stream()
                .map(item -> new Scored(item, similarity(query, normalize(item.getQuestion()))))
                .filter(s -> s.score() >= MIN_SCORE)
                // 没有答案的题目置顶，其余按相似度
                .sorted(Comparator
                        .comparingInt((Scored s) -> StringUtils.hasText(s.question().getAnswer()) ? 1 : 0)
                        .thenComparing(Scored::score, Comparator.reverseOrder()))
                .limit(MAX_SEARCH_RESULTS)
                .map(s -> {
                    QuizQuestionResponse resp = QuizQuestionResponse.from(
                            s.question(), myVotes.get(s.question().getId()));
                    resp.setScore(s.score());
                    return resp;
                })
                .toList();
    }

    @Override
    public QuizPendingResponse unanswered(int limit, Long userId) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PENDING_RESULTS);
        long total = questionRepository.countPending();
        List<QuizQuestion> list = questionRepository.findPending(userId, safeLimit);
        Map<Long, String> myVotes = myVotes(userId);
        List<QuizQuestionResponse> items = list.stream()
                .map(q -> QuizQuestionResponse.from(q, myVotes.get(q.getId())))
                .toList();
        return QuizPendingResponse.builder()
                .total(total)
                .items(items)
                .build();
    }


    @Override
    @Transactional
    public QuizVoteResponse vote(QuizVoteRequest request, Long userId) {
        if (request == null || request.getQuestionId() == null) {
            throw new BusinessException("题目ID不能为空");
        }
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }

        // 锁住题目行，确保“第三票写入答案 + 发积分”只执行一次

        QuizQuestion question = questionRepository.selectByIdForUpdate(request.getQuestionId());
        if (question == null || question.getStatus() == null
                || question.getStatus() != STATUS_APPROVED) {
            throw new BusinessException("题目不存在或不可作答");
        }
        if (StringUtils.hasText(question.getAnswer())) {
            throw new BusinessException("这道题已经有答案了");
        }
        if (voteRepository.findByQuestionAndUser(question.getId(), userId) != null) {
            throw new BusinessException("你已经回答过这道题");
        }

        String selected = resolveVoteAnswer(question, request.getAnswer());
        voteRepository.insert(QuizAnswerVote.builder()
                .questionId(question.getId())
                .userId(userId)
                .answer(selected)
                .build());

        List<QuizAnswerVote> latest = voteRepository.findLatestVotes(question.getId(), 3);
        int streak = 0;
        for (QuizAnswerVote vote : latest) {
            if (selected.equals(vote.getAnswer())) {
                streak++;
            } else {
                break;
            }
        }

        // 连续 3 位用户选择同一个答案才写入题库
        if (streak >= 3) {
            question.setAnswer(selected);
            questionRepository.updateById(question);

            boolean rewarded = false;
            for (QuizAnswerVote winner : latest) {
                pointsService.award(winner.getUserId(), QUIZ_CONSENSUS_POINTS,
                        "quiz_consensus", "忍者测验答案确认");
                if (userId.equals(winner.getUserId())) {
                    rewarded = true;
                }
            }
            log.info("测验答案确认: questionId={}, answer={}, streak={}",
                    question.getId(), selected, streak);
            return QuizVoteResponse.builder()
                    .resolved(true)
                    .answer(selected)
                    .rewarded(rewarded)
                    .points(QUIZ_CONSENSUS_POINTS)
                    .votes(streak)
                    .message("答案已确认：" + selected + "，连续 3 位用户各获得 "
                            + QUIZ_CONSENSUS_POINTS + " 积分")
                    .build();
        }

        return QuizVoteResponse.builder()
                .resolved(false)
                .votes(streak)
                .points(0)
                .message("已记录你的选择，还差 " + (3 - streak) + " 位用户连续选择相同答案")
                .build();
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
        adminApprovalService.notifyPending("忍者测验题目");
        return QuizQuestionResponse.from(q);
    }

    // ==================== 管理员侧 ====================

    @Override
    public IPage<QuizQuestionResponse> list(int page, int size, Integer status, String keyword,
                                            String startDate, String endDate, String sortField, String sortOrder) {
        QueryWrapper<QuizQuestion> wrapper = new QueryWrapper<>();
        if (status != null) {
            wrapper.eq("status", status);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.like("question", keyword.trim());
        }
        LocalDate start = parseDate(startDate);
        if (start != null) {
            wrapper.ge("created_at", start.atStartOfDay());
        }
        LocalDate end = parseDate(endDate);
        if (end != null) {
            wrapper.lt("created_at", end.plusDays(1).atStartOfDay());
        }

        String column = SortColumnResolver.resolve(SORT_COLUMNS, sortField, "created_at");
        String direction = "asc".equalsIgnoreCase(sortOrder) ? "ASC" : "DESC";
        // 无答案题目置顶，其余按用户选择的排序
        wrapper.last("ORDER BY (answer IS NULL OR answer = '') DESC, "
                + column + " " + direction + ", id DESC");
        IPage<QuizQuestion> raw = questionRepository.selectPage(newPage(page, size), wrapper);

        return raw.convert(QuizQuestionResponse::from);
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "日期格式应为 yyyy-MM-dd");
        }
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
        voteRepository.delete(new LambdaQueryWrapper<QuizAnswerVote>()
                .eq(QuizAnswerVote::getQuestionId, id));
        questionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的题目");
        }
        List<Long> distinct = ids.stream().distinct().toList();
        voteRepository.delete(new LambdaQueryWrapper<QuizAnswerVote>()
                .in(QuizAnswerVote::getQuestionId, distinct));
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
    public QuizImportResponse importJson(List<QuizImportItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("导入内容为空");
        }

        int skipped = 0;
        LinkedHashMap<String, QuizImportItem> unique = new LinkedHashMap<>();
        for (QuizImportItem raw : items) {
            String question = safeTrim(raw == null ? null : raw.getQuestion());
            String normalized = normalize(question);
            List<String> options = raw == null ? null : raw.getOptions();
            if (normalized.isEmpty() || question.length() > 500
                    || options == null || options.size() != 4) {
                skipped++;
                continue;
            }
            String a = safeTrim(options.get(0));
            String b = safeTrim(options.get(1));
            String c = safeTrim(options.get(2));
            String d = safeTrim(options.get(3));
            if (a.isEmpty() || b.isEmpty() || c.isEmpty() || d.isEmpty()
                    || a.length() > 200 || b.length() > 200 || c.length() > 200 || d.length() > 200) {
                skipped++;
                continue;
            }
            String answer = safeTrim(raw.getAnswer());
            if (answer.length() > 200) {
                skipped++;
                continue;
            }

            QuizImportItem cleaned = new QuizImportItem();
            cleaned.setQuestion(question);
            cleaned.setOptions(List.of(a, b, c, d));
            cleaned.setAnswer(answer);

            QuizImportItem duplicate = unique.get(normalized);
            if (duplicate != null) {
                // 同一文件内重复：保留第一条；如果第一条没答案、后一条有答案，则补上
                if (!StringUtils.hasText(duplicate.getAnswer()) && StringUtils.hasText(answer)) {
                    duplicate.setAnswer(answer);
                }
                skipped++;
                continue;
            }
            unique.put(normalized, cleaned);
        }

        int imported = 0;
        int updated = 0;
        for (Map.Entry<String, QuizImportItem> entry : unique.entrySet()) {
            String normalized = entry.getKey();
            QuizImportItem item = entry.getValue();
            QuizQuestion existing = questionRepository.selectOne(new LambdaQueryWrapper<QuizQuestion>()
                    .eq(QuizQuestion::getNormalizedQuestion, normalized)
                    .last("LIMIT 1"));
            if (existing != null) {
                // 题库已有该题：只在原答案为空时补全，避免覆盖人工确认的答案
                if (!StringUtils.hasText(existing.getAnswer())
                        && StringUtils.hasText(item.getAnswer())) {
                    existing.setAnswer(item.getAnswer());
                    questionRepository.updateById(existing);
                    updated++;
                } else {
                    skipped++;
                }
                continue;
            }

            List<String> options = item.getOptions();
            QuizQuestion question = QuizQuestion.builder()
                    .question(item.getQuestion())
                    .normalizedQuestion(normalized)
                    .optionA(options.get(0))
                    .optionB(options.get(1))
                    .optionC(options.get(2))
                    .optionD(options.get(3))
                    .answer(item.getAnswer() == null ? "" : item.getAnswer())
                    .status(STATUS_APPROVED)
                    .deleted(0)
                    .build();
            questionRepository.insert(question);
            imported++;
        }

        log.info("测验题目 JSON 导入: imported={}, updated={}, skipped={}",
                imported, updated, skipped);
        return QuizImportResponse.builder()
                .imported(imported)
                .updated(updated)
                .skipped(skipped)
                .build();
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
        adminApprovalService.notifyPending("忍者测验题目");
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
        adminApprovalService.notifyPending("忍者测验题目");
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
                .answer(safeTrim(request.getAnswer()))
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
        q.setAnswer(safeTrim(request.getAnswer()));
    }


    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<Long, String> myVotes(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        return voteRepository.selectList(new LambdaQueryWrapper<QuizAnswerVote>()
                        .eq(QuizAnswerVote::getUserId, userId)).stream()
                .collect(Collectors.toMap(
                        QuizAnswerVote::getQuestionId,
                        QuizAnswerVote::getAnswer,
                        (first, ignored) -> first));
    }

    /** 接受选项文本或 A/B/C/D，统一存选项文本 */
    private String resolveVoteAnswer(QuizQuestion question, String raw) {
        String value = safeTrim(raw);
        if (value.isEmpty()) {
            throw new BusinessException("请选择一个答案");
        }
        String mapped = switch (value.toUpperCase(Locale.ROOT)) {
            case "A" -> question.getOptionA();
            case "B" -> question.getOptionB();
            case "C" -> question.getOptionC();
            case "D" -> question.getOptionD();
            default -> null;
        };
        if (StringUtils.hasText(mapped)) {
            value = mapped;
        }
        if (value.equals(question.getOptionA())
                || value.equals(question.getOptionB())
                || value.equals(question.getOptionC())
                || value.equals(question.getOptionD())) {
            return value;
        }
        throw new BusinessException("请选择有效选项");
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

    private Page<QuizQuestion> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}

