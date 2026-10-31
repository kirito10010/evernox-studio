package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.ResultCode;
import com.evernox.config.StorageConfig;
import com.evernox.dto.MiaoImportItem;
import com.evernox.dto.MiaoImportResponse;
import com.evernox.dto.MiaoPendingResponse;
import com.evernox.dto.MiaoQuestionRequest;
import com.evernox.dto.MiaoQuestionResponse;
import com.evernox.dto.QuizVoteRequest;
import com.evernox.dto.QuizVoteResponse;
import com.evernox.entity.MiaoAnswerVote;
import com.evernox.entity.MiaoQuestion;
import com.evernox.exception.BusinessException;
import com.evernox.repository.MiaoAnswerVoteRepository;
import com.evernox.repository.MiaoQuestionRepository;
import com.evernox.service.AdminApprovalService;
import com.evernox.service.MiaoQuestionService;
import com.evernox.service.PointsService;
import com.evernox.util.ImageTypeValidator;
import com.evernox.util.SortColumnResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 火影忍者OL 喵问答题库服务实现
 *
 * 与忍者测验的区别：多「已排除选项」（可累积，最多 3 个）与看图题的图片。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MiaoQuestionServiceImpl implements MiaoQuestionService {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_APPROVED = 1;
    public static final int STATUS_REJECTED = 2;

    private static final int MAX_PAGE_SIZE = 100;
    private static final double MIN_SCORE = 0.3;
    private static final int MAX_SEARCH_RESULTS = 20;
    private static final int MAX_PENDING_RESULTS = 50;
    private static final int CONSENSUS_POINTS = 10;
    private static final int MAX_REMOVED_OPTIONS = 3;
    private static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;
    private static final String POINTS_TYPE = "miao_consensus";
    private static final Set<String> IMAGE_EXTENSIONS = Set.of("png", "jpg", "jpeg", "webp", "gif");

    /** 排序字段白名单 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "createdAt", "created_at"
    );

    private final MiaoQuestionRepository questionRepository;
    private final MiaoAnswerVoteRepository voteRepository;
    private final AdminApprovalService adminApprovalService;
    private final PointsService pointsService;
    private final StorageConfig storageConfig;
    private final ObjectMapper objectMapper;

    // ==================== 用户侧 ====================

    @Override
    public List<MiaoQuestionResponse> search(String keyword, Long userId) {
        String query = normalize(keyword);
        if (query.isEmpty()) {
            return List.of();
        }
        List<MiaoQuestion> all = questionRepository.selectList(new LambdaQueryWrapper<MiaoQuestion>()
                .eq(MiaoQuestion::getStatus, STATUS_APPROVED));
        Map<Long, String> myVotes = myVotes(userId);

        record Scored(MiaoQuestion question, double score) {
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
                    MiaoQuestionResponse resp = toResponse(s.question(), myVotes.get(s.question().getId()));
                    resp.setScore(s.score());
                    return resp;
                })
                .toList();
    }

    @Override
    public MiaoPendingResponse unanswered(int limit, Long userId) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_PENDING_RESULTS);
        long total = questionRepository.countPending();
        List<MiaoQuestion> list = questionRepository.findPending(userId, safeLimit);
        Map<Long, String> myVotes = myVotes(userId);
        List<MiaoQuestionResponse> items = list.stream()
                .map(q -> toResponse(q, myVotes.get(q.getId())))
                .toList();
        return MiaoPendingResponse.builder()
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

        // 锁住题目行，确保「第三票写入答案 + 发积分」只执行一次
        MiaoQuestion question = questionRepository.selectByIdForUpdate(request.getQuestionId());
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
        voteRepository.insert(MiaoAnswerVote.builder()
                .questionId(question.getId())
                .userId(userId)
                .answer(selected)
                .build());

        List<MiaoAnswerVote> latest = voteRepository.findLatestVotes(question.getId(), 3);
        int streak = 0;
        for (MiaoAnswerVote vote : latest) {
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
            for (MiaoAnswerVote winner : latest) {
                pointsService.award(winner.getUserId(), CONSENSUS_POINTS,
                        POINTS_TYPE, "喵问答答案确认");
                if (userId.equals(winner.getUserId())) {
                    rewarded = true;
                }
            }
            log.info("喵问答答案确认: questionId={}, answer={}, streak={}",
                    question.getId(), selected, streak);
            return QuizVoteResponse.builder()
                    .resolved(true)
                    .answer(selected)
                    .rewarded(rewarded)
                    .points(CONSENSUS_POINTS)
                    .votes(streak)
                    .message("答案已确认：" + selected + "，连续 3 位用户各获得 "
                            + CONSENSUS_POINTS + " 积分")
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
    public MiaoQuestionResponse submit(MiaoQuestionRequest request, Long userId) {
        String normalized = normalize(request.getQuestion());
        if (normalized.isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        ensureNotDuplicate(normalized, null);
        MiaoQuestion q = build(request, normalized, STATUS_PENDING);
        q.setCreatedBy(userId);
        questionRepository.insert(q);
        log.info("喵问答题目提交: id={}, user={}", q.getId(), userId);
        adminApprovalService.notifyPending("喵问答题目");
        return toResponse(q, null);
    }

    // ==================== 管理员侧 ====================

    @Override
    public IPage<MiaoQuestionResponse> list(int page, int size, Integer status, String keyword,
                                            String startDate, String endDate, String sortField, String sortOrder) {
        QueryWrapper<MiaoQuestion> wrapper = new QueryWrapper<>();
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
        IPage<MiaoQuestion> raw = questionRepository.selectPage(newPage(page, size), wrapper);

        return raw.convert(q -> toResponse(q, null));
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
    public MiaoQuestionResponse create(MiaoQuestionRequest request) {
        String normalized = normalize(request.getQuestion());
        if (normalized.isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        ensureNotDuplicate(normalized, null);
        MiaoQuestion q = build(request, normalized, STATUS_APPROVED);
        questionRepository.insert(q);
        return toResponse(q, null);
    }

    @Override
    @Transactional
    public MiaoQuestionResponse update(Long id, MiaoQuestionRequest request) {
        MiaoQuestion q = require(id);
        String normalized = normalize(request.getQuestion());
        if (normalized.isEmpty()) {
            throw new BusinessException("问题不能为空");
        }
        ensureNotDuplicate(normalized, id);
        apply(q, request, normalized);
        questionRepository.updateById(q);
        return toResponse(q, null);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        require(id);
        voteRepository.delete(new LambdaQueryWrapper<MiaoAnswerVote>()
                .eq(MiaoAnswerVote::getQuestionId, id));
        questionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请选择要删除的题目");
        }
        List<Long> distinct = ids.stream().distinct().toList();
        voteRepository.delete(new LambdaQueryWrapper<MiaoAnswerVote>()
                .in(MiaoAnswerVote::getQuestionId, distinct));
        questionRepository.delete(new LambdaQueryWrapper<MiaoQuestion>().in(MiaoQuestion::getId, distinct));
    }

    @Override
    @Transactional
    public void approve(Long id) {
        MiaoQuestion q = require(id);
        if (q.getStatus() == null || q.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该题目不在待审批状态");
        }
        q.setStatus(STATUS_APPROVED);
        questionRepository.updateById(q);
    }

    @Override
    @Transactional
    public void reject(Long id) {
        MiaoQuestion q = require(id);
        if (q.getStatus() == null || q.getStatus() != STATUS_PENDING) {
            throw new BusinessException("该题目不在待审批状态");
        }
        q.setStatus(STATUS_REJECTED);
        questionRepository.updateById(q);
    }

    // ==================== 文件夹导入 ====================

    @Override
    @Transactional
    public MiaoImportResponse importFolder(MultipartFile manifest, List<MultipartFile> images) {
        if (manifest == null || manifest.isEmpty()) {
            throw new BusinessException("请选择包含 questions.json 的题库文件夹");
        }
        String manifestName = safeTrim(manifest.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!manifestName.endsWith(".json")) {
            throw new BusinessException("请选择 questions.json 文件");
        }

        List<MiaoImportItem> items;
        try {
            items = objectMapper.readValue(new String(manifest.getBytes(), StandardCharsets.UTF_8),
                    new TypeReference<List<MiaoImportItem>>() {
                    });
        } catch (Exception e) {
            throw new BusinessException("questions.json 格式不正确");
        }
        if (items == null || items.isEmpty()) {
            throw new BusinessException("questions.json 里没有题目");
        }

        // 1. 图片落盘：按内容哈希命名，天然去重
        Map<String, String> imageMap = new HashMap<>();
        int availableImages = 0;
        if (images != null) {
            for (MultipartFile file : images) {
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String baseName = baseName(file.getOriginalFilename());
                String extension = extensionOf(baseName);
                if (baseName.isEmpty() || !IMAGE_EXTENSIONS.contains(extension)) {
                    continue;
                }
                if (file.getSize() > MAX_IMAGE_BYTES) {
                    log.warn("喵问答图片超过 8MB，已跳过: {}", baseName);
                    continue;
                }
                try {
                    byte[] bytes = file.getBytes();
                    if (!ImageTypeValidator.isSupportedImage(bytes)) {
                        log.warn("喵问答图片格式不支持，已跳过: {}", baseName);
                        continue;
                    }
                    String stored = sha256Hex(bytes).substring(0, 32) + "." + extension;
                    Path target = storageConfig.getMiaoImagePath().resolve(stored);
                    if (!Files.exists(target)) {
                        Files.write(target, bytes);
                    }
                    imageMap.put(baseName.toLowerCase(Locale.ROOT), stored);
                    availableImages++;
                } catch (IOException e) {
                    log.warn("喵问答图片保存失败: {}, {}", baseName, e.getMessage());
                }
            }
        }

        // 2. 清洗 + 批内去重
        int skipped = 0;
        int missingImages = 0;
        LinkedHashMap<String, Cleaned> unique = new LinkedHashMap<>();
        for (MiaoImportItem raw : items) {
            String question = safeTrim(raw == null ? null : raw.getQuestion());
            String normalized = normalize(question);
            List<String> rawOptions = raw == null ? null : raw.getOptions();
            if (normalized.isEmpty() || question.length() > 500
                    || rawOptions == null || rawOptions.size() != 4) {
                skipped++;
                continue;
            }
            List<String> options = rawOptions.stream().map(this::safeTrim).toList();
            if (options.stream().anyMatch(String::isEmpty)
                    || options.stream().anyMatch(o -> o.length() > 200)) {
                skipped++;
                continue;
            }
            String answer = safeTrim(raw.getAnswer());
            if (answer.length() > 200) {
                skipped++;
                continue;
            }
            // 答案不在四个选项内 → 视为待确认，不丢弃整题
            if (!answer.isEmpty() && !options.contains(answer)) {
                answer = "";
            }
            List<String> removed = new ArrayList<>();
            String removedRaw = safeTrim(raw.getRemoved());
            if (!removedRaw.isEmpty() && options.contains(removedRaw)) {
                removed.add(removedRaw);
            }
            String image = "";
            String imageRef = safeTrim(raw.getImage());
            if (!imageRef.isEmpty()) {
                String stored = imageMap.get(baseName(imageRef).toLowerCase(Locale.ROOT));
                if (stored != null) {
                    image = stored;
                } else {
                    missingImages++;
                }
            }

            Cleaned cleaned = new Cleaned(question, new ArrayList<>(options), answer,
                    sanitizeRemoved(removed, options), image);
            Cleaned duplicate = unique.get(normalized);
            if (duplicate != null) {
                duplicate.merge(cleaned);
                skipped++;
                continue;
            }
            unique.put(normalized, cleaned);
        }

        // 3. 入库：已存在的题目只做增量补全
        int imported = 0;
        int updated = 0;
        for (Map.Entry<String, Cleaned> entry : unique.entrySet()) {
            String normalized = entry.getKey();
            Cleaned item = entry.getValue();
            MiaoQuestion existing = questionRepository.selectOne(new LambdaQueryWrapper<MiaoQuestion>()
                    .eq(MiaoQuestion::getNormalizedQuestion, normalized)
                    .last("LIMIT 1"));
            if (existing != null) {
                boolean changed = false;
                if (!StringUtils.hasText(existing.getAnswer()) && StringUtils.hasText(item.answer)) {
                    existing.setAnswer(item.answer);
                    changed = true;
                }
                List<String> existRemoved = parseRemoved(existing.getRemovedOptions());
                List<String> mergedRemoved = mergeRemoved(existRemoved, item.removed, item.options);
                if (!mergedRemoved.equals(existRemoved)) {
                    existing.setRemovedOptions(serializeRemoved(mergedRemoved));
                    changed = true;
                }
                if (!StringUtils.hasText(existing.getImage()) && StringUtils.hasText(item.image)) {
                    existing.setImage(item.image);
                    changed = true;
                }
                if (changed) {
                    questionRepository.updateById(existing);
                    updated++;
                } else {
                    skipped++;
                }
                continue;
            }

            MiaoQuestion question = MiaoQuestion.builder()
                    .question(item.question)
                    .normalizedQuestion(normalized)
                    .optionA(item.options.get(0))
                    .optionB(item.options.get(1))
                    .optionC(item.options.get(2))
                    .optionD(item.options.get(3))
                    .answer(item.answer == null ? "" : item.answer)
                    .removedOptions(serializeRemoved(item.removed))
                    .image(item.image)
                    .status(STATUS_APPROVED)
                    .deleted(0)
                    .build();
            questionRepository.insert(question);
            imported++;
        }

        log.info("喵问答文件夹导入: imported={}, updated={}, skipped={}, images={}, missingImages={}",
                imported, updated, skipped, availableImages, missingImages);
        return MiaoImportResponse.builder()
                .imported(imported)
                .updated(updated)
                .skipped(skipped)
                .images(availableImages)
                .missingImages(missingImages)
                .build();
    }

    // ==================== 我的提交 ====================

    @Override
    public List<MiaoQuestionResponse> listMine(Long userId) {
        return questionRepository.selectList(new LambdaQueryWrapper<MiaoQuestion>()
                        .eq(MiaoQuestion::getCreatedBy, userId)
                        .orderByDesc(MiaoQuestion::getId)).stream()
                .map(q -> toResponse(q, null))
                .toList();
    }

    @Override
    @Transactional
    public MiaoQuestionResponse updateMine(Long id, MiaoQuestionRequest request, Long userId) {
        MiaoQuestion q = requireOwnSubmission(id, userId);
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
        adminApprovalService.notifyPending("喵问答题目");
        return toResponse(q, null);
    }

    @Override
    @Transactional
    public void deleteMine(Long id, Long userId) {
        MiaoQuestion q = requireOwnSubmission(id, userId);
        if (q.getStatus() != null && q.getStatus() == STATUS_APPROVED) {
            throw new BusinessException("已通过的题目无法删除");
        }
        questionRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void resubmit(Long id, Long userId) {
        MiaoQuestion q = requireOwnSubmission(id, userId);
        if (q.getStatus() == null || q.getStatus() != STATUS_REJECTED) {
            throw new BusinessException("仅被驳回的题目可重新提交");
        }
        q.setStatus(STATUS_PENDING);
        questionRepository.updateById(q);
        adminApprovalService.notifyPending("喵问答题目");
    }

    // ==================== 内部方法 ====================

    private MiaoQuestion require(Long id) {
        MiaoQuestion q = questionRepository.selectById(id);
        if (q == null) {
            throw new BusinessException("题目不存在");
        }
        return q;
    }

    private MiaoQuestion requireOwnSubmission(Long id, Long userId) {
        MiaoQuestion q = require(id);
        if (userId == null || !userId.equals(q.getCreatedBy())) {
            throw new BusinessException(403, "无权操作该题目");
        }
        return q;
    }

    private void ensureNotDuplicate(String normalized, Long excludeId) {
        Long count = questionRepository.selectCount(new LambdaQueryWrapper<MiaoQuestion>()
                .eq(MiaoQuestion::getNormalizedQuestion, normalized)
                .ne(excludeId != null, MiaoQuestion::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("该题目已存在");
        }
    }

    private MiaoQuestion build(MiaoQuestionRequest request, String normalized, int status) {
        return MiaoQuestion.builder()
                .question(request.getQuestion().trim())
                .normalizedQuestion(normalized)
                .optionA(request.getOptionA().trim())
                .optionB(request.getOptionB().trim())
                .optionC(request.getOptionC().trim())
                .optionD(request.getOptionD().trim())
                .answer(safeTrim(request.getAnswer()))
                .removedOptions(serializeRemoved(normalizeRequestRemoved(request)))
                .image(normalizeImageName(request.getImage()))
                .status(status)
                .deleted(0)
                .build();
    }

    private void apply(MiaoQuestion q, MiaoQuestionRequest request, String normalized) {
        q.setQuestion(request.getQuestion().trim());
        q.setNormalizedQuestion(normalized);
        q.setOptionA(request.getOptionA().trim());
        q.setOptionB(request.getOptionB().trim());
        q.setOptionC(request.getOptionC().trim());
        q.setOptionD(request.getOptionD().trim());
        q.setAnswer(safeTrim(request.getAnswer()));
        q.setRemovedOptions(serializeRemoved(normalizeRequestRemoved(request)));
        q.setImage(normalizeImageName(request.getImage()));
    }

    /** 请求里的排除项：只保留属于四个选项的值，去重并限制 3 个 */
    private List<String> normalizeRequestRemoved(MiaoQuestionRequest request) {
        List<String> options = List.of(
                safeTrim(request.getOptionA()),
                safeTrim(request.getOptionB()),
                safeTrim(request.getOptionC()),
                safeTrim(request.getOptionD()));
        List<String> source = request.getRemovedOptions() == null ? List.of() : request.getRemovedOptions();
        return sanitizeRemoved(source.stream().map(this::safeTrim).toList(), options);
    }

    private List<String> sanitizeRemoved(List<String> source, List<String> options) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : source) {
            if (result.size() >= MAX_REMOVED_OPTIONS) {
                break;
            }
            if (StringUtils.hasText(value) && options.contains(value)) {
                result.add(value);
            }
        }
        return new ArrayList<>(result);
    }

    private List<String> mergeRemoved(List<String> existing, List<String> incoming, List<String> options) {
        List<String> merged = new ArrayList<>(existing);
        merged.addAll(incoming);
        return sanitizeRemoved(merged, options);
    }

    private String normalizeImageName(String raw) {
        String value = safeTrim(raw);
        if (value.isEmpty()) {
            return "";
        }
        // 只接受服务端生成的文件名，避免把路径写进库
        if (value.contains("/") || value.contains("\\") || value.contains("..") || value.length() > 120) {
            throw new BusinessException("图片文件名不合法");
        }
        return value;
    }

    private MiaoQuestionResponse toResponse(MiaoQuestion q, String myVote) {
        return MiaoQuestionResponse.builder()
                .id(q.getId())
                .question(q.getQuestion())
                .optionA(q.getOptionA())
                .optionB(q.getOptionB())
                .optionC(q.getOptionC())
                .optionD(q.getOptionD())
                .answer(q.getAnswer())
                .removedOptions(parseRemoved(q.getRemovedOptions()))
                .image(q.getImage())
                .status(q.getStatus())
                .myVote(myVote)
                .createdAt(q.getCreatedAt())
                .build();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private Map<Long, String> myVotes(Long userId) {
        if (userId == null) {
            return Map.of();
        }
        return voteRepository.selectList(new LambdaQueryWrapper<MiaoAnswerVote>()
                        .eq(MiaoAnswerVote::getUserId, userId)).stream()
                .collect(Collectors.toMap(
                        MiaoAnswerVote::getQuestionId,
                        MiaoAnswerVote::getAnswer,
                        (first, ignored) -> first));
    }

    /** 接受选项文本或 A/B/C/D，统一存选项文本；已排除的选项不允许选择 */
    private String resolveVoteAnswer(MiaoQuestion question, String raw) {
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
        boolean isOption = value.equals(question.getOptionA())
                || value.equals(question.getOptionB())
                || value.equals(question.getOptionC())
                || value.equals(question.getOptionD());
        if (!isOption) {
            throw new BusinessException("请选择有效选项");
        }
        if (parseRemoved(question.getRemovedOptions()).contains(value)) {
            throw new BusinessException("该选项已被排除，不能选择");
        }
        return value;
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

    private Page<MiaoQuestion> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    private List<String> parseRemoved(String raw) {
        if (!StringUtils.hasText(raw)) {
            return new ArrayList<>();
        }
        try {
            return new ArrayList<>(objectMapper.readValue(raw, new TypeReference<List<String>>() {
            }));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String serializeRemoved(List<String> removed) {
        if (removed == null || removed.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(removed);
        } catch (Exception e) {
            return null;
        }
    }

    /** 取路径最后一段（浏览器可能传来 folder/images/x.png） */
    private String baseName(String path) {
        String value = safeTrim(path);
        int slash = Math.max(value.lastIndexOf('/'), value.lastIndexOf('\\'));
        return slash >= 0 ? value.substring(slash + 1) : value;
    }

    private String extensionOf(String baseName) {
        int dot = baseName.lastIndexOf('.');
        if (dot < 0 || dot == baseName.length() - 1) {
            return "";
        }
        return baseName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** 导入过程中清洗后的题目，批内重复时合并答案/排除项/图片 */
    private static final class Cleaned {
        private final String question;
        private final List<String> options;
        private String answer;
        private List<String> removed;
        private String image;

        private Cleaned(String question, List<String> options,
                        String answer, List<String> removed, String image) {
            this.question = question;
            this.options = options;
            this.answer = answer;
            this.removed = removed;
            this.image = image;
        }

        private void merge(Cleaned other) {
            if (!StringUtils.hasText(answer) && StringUtils.hasText(other.answer)) {
                answer = other.answer;
            }
            LinkedHashSet<String> mergedRemoved = new LinkedHashSet<>(removed);
            mergedRemoved.addAll(other.removed);
            // 单题最多 3 个排除项，保留至少一个可选项
            List<String> limited = new ArrayList<>();
            for (String value : mergedRemoved) {
                if (limited.size() >= MAX_REMOVED_OPTIONS) {
                    break;
                }
                if (options.contains(value)) {
                    limited.add(value);
                }
            }
            removed = limited;
            if (!StringUtils.hasText(image) && StringUtils.hasText(other.image)) {
                image = other.image;
            }
        }
    }
}
