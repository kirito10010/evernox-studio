package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.NoteStatus;
import com.evernox.common.UserRole;
import com.evernox.dto.NoteRequest;
import com.evernox.dto.NoteResponse;
import com.evernox.dto.NoteStatsResponse;
import com.evernox.entity.Note;
import com.evernox.entity.NoteImage;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.ImageRepository;
import com.evernox.repository.NoteImageRepository;
import com.evernox.repository.NoteRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.ImageService;
import com.evernox.service.NoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 记事本服务实现（用户侧）
 *
 * 正文里的插图只以 image.id 出现，引用关系另存 note_image：
 * 删除笔记或编辑时移除图片，都靠这张表判断哪些插图已无人引用、可以真正删掉。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class NoteServiceImpl implements NoteService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NoteRepository noteRepository;
    private final NoteImageRepository noteImageRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final ImageService imageService;
    private final NoteHtmlSanitizer sanitizer;
    private final NoteImageSupport noteImageSupport;

    @Override
    @Transactional
    public NoteResponse create(NoteRequest request, Long userId) {
        String safeHtml = sanitizer.sanitize(request.getContent());
        Set<Long> imageIds = ownedImageIds(safeHtml, userId);

        Note note = Note.builder()
                .userId(userId)
                .title(request.getTitle().trim())
                .content(safeHtml)
                .summary(sanitizer.toSummary(safeHtml))
                .pinned(0)
                .status(NoteStatus.PRIVATE)
                .deleted(0)
                .build();
        noteRepository.insert(note);
        linkImages(note.getId(), imageIds);
        log.info("笔记创建: id={}, user={}", note.getId(), userId);
        return NoteResponse.detail(note);
    }

    @Override
    @Transactional
    public NoteResponse update(Long id, NoteRequest request, Long userId) {
        Note note = requireOwned(id, userId);
        if (note.getStatus() == NoteStatus.PENDING) {
            throw new BusinessException("审批中不可编辑，请先撤回申请");
        }
        if (note.getStatus() == NoteStatus.PUBLIC) {
            throw new BusinessException("已公开的笔记不可直接编辑，请先撤下");
        }

        String safeHtml = sanitizer.sanitize(request.getContent());
        Set<Long> newIds = ownedImageIds(safeHtml, userId);

        note.setTitle(request.getTitle().trim());
        note.setContent(safeHtml);
        note.setSummary(sanitizer.toSummary(safeHtml));
        noteRepository.updateById(note);

        syncImages(id, newIds, userId);
        return NoteResponse.detail(note);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        Note note = requireOwned(id, userId);
        noteRepository.deleteById(id);
        // 笔记没了，插图也不再有别处引用，连文件一起清掉
        syncImages(id, Set.of(), userId);
        log.info("笔记删除: id={}, user={}, status={}", id, userId, note.getStatus());
    }

    @Override
    @Transactional
    public NoteResponse setPinned(Long id, boolean pinned, Long userId) {
        Note note = requireOwned(id, userId);
        note.setPinned(pinned ? 1 : 0);
        noteRepository.updateById(note);
        return NoteResponse.brief(note);
    }

    @Override
    public IPage<NoteResponse> listMine(Long userId, String keyword, Integer status,
                                        int page, int size) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Note::getUserId, userId)
                .eq(status != null, Note::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Note::getTitle, kw).or().like(Note::getSummary, kw));
        }
        wrapper.orderByDesc(Note::getPinned).orderByDesc(Note::getUpdatedAt);
        return toBriefPage(noteRepository.selectPage(newPage(page, size), wrapper));
    }

    @Override
    public IPage<NoteResponse> listPublic(String keyword, int page, int size) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Note::getStatus, NoteStatus.PUBLIC);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Note::getTitle, kw).or().like(Note::getSummary, kw));
        }
        wrapper.orderByDesc(Note::getReviewedAt).orderByDesc(Note::getId);
        return toBriefPage(noteRepository.selectPage(newPage(page, size), wrapper));
    }

    @Override
    public NoteResponse getById(Long id, Long userId) {
        Note note = noteRepository.selectById(id);
        if (note == null) {
            throw new BusinessException("笔记不存在");
        }
        if (note.getStatus() != NoteStatus.PUBLIC
                && !note.getUserId().equals(userId)
                && !isAdmin(userId)) {
            throw new BusinessException(403, "无权查看该笔记");
        }
        NoteResponse response = NoteResponse.detail(note);
        response.setOwnerName(username(note.getUserId()));
        return response;
    }

    @Override
    @Transactional
    public void submit(Long id, Long userId) {
        Note note = requireOwned(id, userId);
        if (note.getStatus() != NoteStatus.PRIVATE && note.getStatus() != NoteStatus.REJECTED) {
            throw new BusinessException("当前状态不可申请公开");
        }
        // 条件更新：既能把驳回原因清成 null（updateById 会忽略 null），又能挡住并发重复提交
        int rows = noteRepository.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, id)
                .in(Note::getStatus, List.of(NoteStatus.PRIVATE, NoteStatus.REJECTED))
                .set(Note::getStatus, NoteStatus.PENDING)
                .set(Note::getSubmittedAt, LocalDateTime.now())
                .set(Note::getUpdatedAt, LocalDateTime.now())
                .set(Note::getRejectReason, null)
                .set(Note::getReviewedBy, null)
                .set(Note::getReviewedAt, null));
        if (rows == 0) {
            throw new BusinessException("该笔记状态已变化，请刷新后重试");
        }
        log.info("笔记申请公开: id={}, user={}", id, userId);
    }

    @Override
    @Transactional
    public void withdraw(Long id, Long userId) {
        Note note = requireOwned(id, userId);
        if (note.getStatus() != NoteStatus.PENDING && note.getStatus() != NoteStatus.PUBLIC) {
            throw new BusinessException("当前状态无需撤回");
        }
        note.setStatus(NoteStatus.PRIVATE);
        noteRepository.updateById(note);
        // 撤下后插图必须收回私密，否则公开期间拿到 id 的人还能继续读图
        setImagesVisibility(id, 0);
        log.info("笔记撤回: id={}, user={}", id, userId);
    }

    @Override
    public NoteStatsResponse getStats(Long userId) {
        return NoteStatsResponse.builder()
                .mine(countMine(userId, null))
                .pending(countMine(userId, NoteStatus.PENDING))
                .published(countMine(userId, NoteStatus.PUBLIC))
                .rejected(countMine(userId, NoteStatus.REJECTED))
                .build();
    }

    // ---------------- 内部方法 ----------------

    /** 正文里引用的图片必须是自己上传的，否则可用他人的私密图 id 蹭显示 */
    private Set<Long> ownedImageIds(String safeHtml, Long userId) {
        Set<Long> ids = sanitizer.extractImageIds(safeHtml);
        if (ids.isEmpty()) {
            return ids;
        }
        Set<Long> owned = imageRepository.selectBatchIds(ids).stream()
                .filter(img -> img.getUserId().equals(userId))
                .map(img -> img.getId())
                .collect(Collectors.toSet());
        if (owned.size() != ids.size()) {
            throw new BusinessException(403, "正文引用了不属于你的图片");
        }
        return owned;
    }

    private void linkImages(Long noteId, Set<Long> imageIds) {
        for (Long imageId : imageIds) {
            noteImageRepository.insert(NoteImage.builder()
                    .noteId(noteId)
                    .imageId(imageId)
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    /**
     * 同步引用关系，并把不再被引用的插图真正删掉
     *
     * 只删本笔记曾引用、现在不再引用的图；不去猜别处是否也在引用（插图只服务于所属笔记）。
     */
    private void syncImages(Long noteId, Set<Long> newIds, Long userId) {
        Set<Long> oldIds = new HashSet<>(currentImageIds(noteId));

        Set<Long> removed = new HashSet<>(oldIds);
        removed.removeAll(newIds);
        for (Long imageId : removed) {
            noteImageRepository.delete(new LambdaQueryWrapper<NoteImage>()
                    .eq(NoteImage::getNoteId, noteId)
                    .eq(NoteImage::getImageId, imageId));
            try {
                imageService.deleteImage(imageId, userId);
            } catch (RuntimeException e) {
                // 图片可能已被用户在图床侧删除，清理失败不该阻断保存
                log.warn("清理笔记插图失败: noteId={}, imageId={}, err={}", noteId, imageId, e.getMessage());
            }
        }

        Set<Long> added = new HashSet<>(newIds);
        added.removeAll(oldIds);
        linkImages(noteId, added);
    }

    private List<Long> currentImageIds(Long noteId) {
        return noteImageSupport.imageIds(noteId);
    }

    private void setImagesVisibility(Long noteId, int visibility) {
        noteImageSupport.setVisibility(noteId, visibility);
    }

    private long countMine(Long userId, Integer status) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Note::getUserId, userId).eq(status != null, Note::getStatus, status);
        return noteRepository.selectCount(wrapper);
    }

    /** 取出并校验归属，越权直接 403 */
    private Note requireOwned(Long id, Long userId) {
        Note note = noteRepository.selectById(id);
        if (note == null) {
            throw new BusinessException("笔记不存在");
        }
        if (!note.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该笔记");
        }
        return note;
    }

    /** 列表统一补作者名：一次查库补齐，避免逐条查用户表 */
    private IPage<NoteResponse> toBriefPage(IPage<Note> raw) {
        Page<NoteResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        List<Note> notes = raw.getRecords();
        Map<Long, String> nameCache = new HashMap<>();
        result.setRecords(notes.stream().map(note -> {
            NoteResponse dto = NoteResponse.brief(note);
            dto.setOwnerName(nameCache.computeIfAbsent(note.getUserId(), this::username));
            return dto;
        }).toList());
        return result;
    }

    private String username(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.selectById(userId);
        return user != null ? user.getUsername() : "未知用户";
    }

    private boolean isAdmin(Long userId) {
        if (userId == null) {
            return false;
        }
        User user = userRepository.selectById(userId);
        return user != null && UserRole.ADMIN.equals(user.getRole());
    }

    private Page<Note> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
