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
import com.evernox.util.QuizSearchUtils;
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
import java.util.Objects;
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
                .map(item -> new Scored(item, QuizSearchUtils.similarity(query, normalize(item.getQuestion()))))
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
        ensureNotDuplicate(normalized, normalizeImageName(request.getImage()), null);
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
        ensureNotDuplicate(normalized, normalizeImageName(request.getImage()), null);
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
        ensureNotDuplicate(normalized, normalizeImageName(request.getImage()), id);
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
            // removed 现在是数组（可能多个），旧格式单字符串也能解析
            List<String> removedCandidates = raw.getRemoved() == null ? List.of()
                    : raw.getRemoved().stream().map(this::safeTrim).toList();
            Resolved resolved = resolveOptions(options, removedCandidates, answer);
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

            Cleaned cleaned = new Cleaned(normalized, question, new ArrayList<>(options),
                    resolved.answer(), resolved.removed(), image);
            String key = questionKey(normalized, image);
            Cleaned duplicate = unique.get(key);
            if (duplicate != null) {
                // 同问题 + 同图但选项不一致：宁可跳过，也不要把另一套选项的排除项并进来
                if (duplicate.options.equals(cleaned.options)) {
                    duplicate.merge(cleaned);
                } else {
                    log.warn("喵问答同问题同图但选项不一致，已跳过合并: question={}, image={}",
                            normalized, image);
                }
                skipped++;
                continue;
            }
            unique.put(key, cleaned);
        }

        // 3. 入库：已存在的题目只做增量补全
        int imported = 0;
        int updated = 0;
        for (Cleaned item : unique.values()) {
            // 身份 = 归一化问题 + 题图：看图题文案相同但图片不同，是两道不同的题
            LambdaQueryWrapper<MiaoQuestion> existingWrapper = new LambdaQueryWrapper<MiaoQuestion>()
                    .eq(MiaoQuestion::getNormalizedQuestion, item.normalized);
            if (StringUtils.hasText(item.image)) {
                existingWrapper.eq(MiaoQuestion::getImage, item.image);
            } else {
                existingWrapper.and(w -> w.isNull(MiaoQuestion::getImage).or().eq(MiaoQuestion::getImage, ""));
            }
            existingWrapper.last("LIMIT 1");
            MiaoQuestion existing = questionRepository.selectOne(existingWrapper);
            if (existing != null) {
                List<String> existOptions = List.of(
                        safeTrim(existing.getOptionA()), safeTrim(existing.getOptionB()),
                        safeTrim(existing.getOptionC()), safeTrim(existing.getOptionD()));
                if (!existOptions.equals(item.options)) {
                    // 身份相同但选项不同：历史数据可能已经串题，跳过更新避免继续污染
                    log.warn("喵问答已有题目选项不一致，已跳过更新: question={}, image={}",
                            item.normalized, item.image);
                    skipped++;
                    continue;
                }
                boolean changed = false;
                List<String> existRemoved = parseRemoved(existing.getRemovedOptions());
                List<String> mergedRemovedCandidates = new ArrayList<>(existRemoved);
                mergedRemovedCandidates.addAll(item.removed);
                String baseAnswer = StringUtils.hasText(existing.getAnswer())
                        ? existing.getAnswer() : item.answer;
                // 排除项取并集后统一校正答案：答案被排除就清掉；凑齐 3 个排除项则反推答案
                Resolved resolved = resolveOptions(item.options, mergedRemovedCandidates, baseAnswer);
                if (!resolved.removed().equals(existRemoved)) {
                    existing.setRemovedOptions(serializeRemoved(resolved.removed()));
                    changed = true;
                }
                String oldAnswer = existing.getAnswer() == null ? "" : existing.getAnswer();
                if (!Objects.equals(resolved.answer(), oldAnswer)) {
                    existing.setAnswer(resolved.answer());
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
                    .normalizedQuestion(item.normalized)
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
        ensureNotDuplicate(normalized, normalizeImageName(request.getImage()), id);
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

    /**
     * 查重身份 = 归一化问题 + 题图。
     *
     * <p>看图题文案往往完全相同（如「看局部猜人物」），只有图片不同就是不同的题；
     * 只按文本查重会把多道看图题合并成一道。
     */
    private void ensureNotDuplicate(String normalized, String image, Long excludeId) {
        LambdaQueryWrapper<MiaoQuestion> wrapper = new LambdaQueryWrapper<MiaoQuestion>()
                .eq(MiaoQuestion::getNormalizedQuestion, normalized)
                .ne(excludeId != null, MiaoQuestion::getId, excludeId);
        if (StringUtils.hasText(image)) {
            wrapper.eq(MiaoQuestion::getImage, image);
        } else {
            wrapper.and(w -> w.isNull(MiaoQuestion::getImage).or().eq(MiaoQuestion::getImage, ""));
        }
        Long count = questionRepository.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException("该题目已存在");
        }
    }

    private MiaoQuestion build(MiaoQuestionRequest request, String normalized, int status) {
        List<String> options = optionsOf(request);
        Resolved resolved = resolveOptions(options, removedOf(request), safeTrim(request.getAnswer()));
        return MiaoQuestion.builder()
                .question(request.getQuestion().trim())
                .normalizedQuestion(normalized)
                .optionA(options.get(0))
                .optionB(options.get(1))
                .optionC(options.get(2))
                .optionD(options.get(3))
                .answer(resolved.answer())
                .removedOptions(serializeRemoved(resolved.removed()))
                .image(normalizeImageName(request.getImage()))
                .status(status)
                .deleted(0)
                .build();
    }

    private void apply(MiaoQuestion q, MiaoQuestionRequest request, String normalized) {
        List<String> options = optionsOf(request);
        Resolved resolved = resolveOptions(options, removedOf(request), safeTrim(request.getAnswer()));
        q.setQuestion(request.getQuestion().trim());
        q.setNormalizedQuestion(normalized);
        q.setOptionA(options.get(0));
        q.setOptionB(options.get(1));
        q.setOptionC(options.get(2));
        q.setOptionD(options.get(3));
        q.setAnswer(resolved.answer());
        q.setRemovedOptions(serializeRemoved(resolved.removed()));
        q.setImage(normalizeImageName(request.getImage()));
    }

    private List<String> optionsOf(MiaoQuestionRequest request) {
        return List.of(
                safeTrim(request.getOptionA()),
                safeTrim(request.getOptionB()),
                safeTrim(request.getOptionC()),
                safeTrim(request.getOptionD()));
    }

    private List<String> removedOf(MiaoQuestionRequest request) {
        return request.getRemovedOptions() == null ? List.of()
                : request.getRemovedOptions().stream().map(this::safeTrim).toList();
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
        return QuizSearchUtils.normalize(s);
    }

    /** 校正后的答案与排除项 */
    private record Resolved(List<String> removed, String answer) {
    }

    /**
     * 排除项与答案的统一校正规则：
     *  - 排除项只保留属于四个选项的值，去重，最多 3 个；
     *  - 答案如果落在排除项里，一定错了 → 清空；
     *  - **不自动反推答案**：排除项可能来自串题的脏数据，3 个排除项≠可信答案，
     *    没有答案就留空，交给用户投票确认。
     */
    private static Resolved resolveOptions(List<String> options, List<String> removedCandidates,
                                           String answerCandidate) {
        List<String> removed = sanitizeRemoved(options, removedCandidates);
        String answer = answerCandidate == null ? "" : answerCandidate.trim();
        if (!answer.isEmpty() && !options.contains(answer)) {
            answer = "";
        }
        if (!answer.isEmpty() && removed.contains(answer)) {
            answer = "";
        }
        return new Resolved(removed, answer);
    }

    /** 过滤非法值、去重、最多保留 3 个排除项 */
    private static List<String> sanitizeRemoved(List<String> options, List<String> source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (source != null) {
            for (String value : source) {
                if (result.size() >= MAX_REMOVED_OPTIONS) {
                    break;
                }
                if (StringUtils.hasText(value) && options.contains(value)) {
                    result.add(value);
                }
            }
        }
        return new ArrayList<>(result);
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

    /** 导入身份：归一化问题 + 题图（无图用空串）。只看文案会把多道看图题合成一道 */
    private static String questionKey(String normalized, String image) {
        return normalized + "\u0000" + (image == null ? "" : image.toLowerCase(Locale.ROOT));
    }

    /** 导入过程中清洗后的题目，批内重复时合并答案/排除项/图片 */
    private static final class Cleaned {
        private final String normalized;
        private final String question;
        private final List<String> options;
        private String answer;
        private List<String> removed;
        private String image;

        private Cleaned(String normalized, String question, List<String> options,
                        String answer, List<String> removed, String image) {
            this.normalized = normalized;
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
            List<String> mergedRemoved = new ArrayList<>(removed);
            mergedRemoved.addAll(other.removed);
            removed = mergedRemoved;
            if (!StringUtils.hasText(image) && StringUtils.hasText(other.image)) {
                image = other.image;
            }
            // 合并后再按「答案不能被排除 / 3 个排除项反推答案」校正一次
            Resolved resolved = resolveOptions(options, removed, answer);
            removed = resolved.removed();
            answer = resolved.answer();
        }
    }
}
