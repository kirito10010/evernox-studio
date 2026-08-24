package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.common.NoteStatus;
import com.evernox.dto.NoteResponse;
import com.evernox.dto.NoteStatsResponse;
import com.evernox.entity.Note;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.NoteRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.AdminNoteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 记事本审批服务实现（管理员）
 *
 * 状态流转全部用条件更新：既能把 null 写进去（updateById 会忽略 null），
 * 又能挡住两个管理员同时审同一条。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminNoteServiceImpl implements AdminNoteService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final NoteImageSupport noteImageSupport;

    @Override
    public IPage<NoteResponse> listNotes(int page, int size, Integer status, String keyword) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(status != null, Note::getStatus, status);
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Note::getTitle, kw).or().like(Note::getSummary, kw));
        }
        // 待审批的按提交时间先来先审，其余按更新时间倒序
        if (status != null && status == NoteStatus.PENDING) {
            wrapper.orderByAsc(Note::getSubmittedAt);
        } else {
            wrapper.orderByDesc(Note::getUpdatedAt);
        }

        IPage<Note> raw = noteRepository.selectPage(newPage(page, size), wrapper);
        Page<NoteResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        Map<Long, String> nameCache = new HashMap<>();
        result.setRecords(raw.getRecords().stream().map(note -> {
            NoteResponse dto = NoteResponse.brief(note);
            dto.setOwnerName(nameCache.computeIfAbsent(note.getUserId(), this::username));
            if (note.getReviewedBy() != null) {
                dto.setReviewerName(nameCache.computeIfAbsent(note.getReviewedBy(), this::username));
            }
            return dto;
        }).toList());
        return result;
    }

    @Override
    public NoteResponse getById(Long id) {
        Note note = require(id);
        NoteResponse dto = NoteResponse.detail(note);
        dto.setOwnerName(username(note.getUserId()));
        return dto;
    }

    @Override
    @Transactional
    public void approve(Long id, Long adminId) {
        Note note = require(id);
        if (note.getStatus() != NoteStatus.PENDING) {
            throw new BusinessException("只能审批待审批的笔记");
        }
        int rows = noteRepository.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, id)
                .eq(Note::getStatus, NoteStatus.PENDING)
                .set(Note::getStatus, NoteStatus.PUBLIC)
                .set(Note::getRejectReason, null)
                .set(Note::getReviewedBy, adminId)
                .set(Note::getReviewedAt, LocalDateTime.now())
                .set(Note::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException("该笔记状态已变化，请刷新后重试");
        }
        // 正文插图必须同步转公开，否则读者只能看到加载失败的图
        noteImageSupport.setVisibility(id, 1);
        log.info("笔记审批通过: id={}, admin={}", id, adminId);
    }

    @Override
    @Transactional
    public void reject(Long id, String reason, Long adminId) {
        Note note = require(id);
        if (note.getStatus() != NoteStatus.PENDING) {
            throw new BusinessException("只能驳回待审批的笔记");
        }
        int rows = noteRepository.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, id)
                .eq(Note::getStatus, NoteStatus.PENDING)
                .set(Note::getStatus, NoteStatus.REJECTED)
                .set(Note::getRejectReason, reason.trim())
                .set(Note::getReviewedBy, adminId)
                .set(Note::getReviewedAt, LocalDateTime.now())
                .set(Note::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException("该笔记状态已变化，请刷新后重试");
        }
        noteImageSupport.setVisibility(id, 0);
        log.info("笔记驳回: id={}, admin={}", id, adminId);
    }

    @Override
    @Transactional
    public void offline(Long id, Long adminId) {
        Note note = require(id);
        if (note.getStatus() != NoteStatus.PUBLIC) {
            throw new BusinessException("只能撤下已公开的笔记");
        }
        int rows = noteRepository.update(null, new LambdaUpdateWrapper<Note>()
                .eq(Note::getId, id)
                .eq(Note::getStatus, NoteStatus.PUBLIC)
                .set(Note::getStatus, NoteStatus.PRIVATE)
                .set(Note::getReviewedBy, adminId)
                .set(Note::getReviewedAt, LocalDateTime.now())
                .set(Note::getUpdatedAt, LocalDateTime.now()));
        if (rows == 0) {
            throw new BusinessException("该笔记状态已变化，请刷新后重试");
        }
        noteImageSupport.setVisibility(id, 0);
        log.info("笔记撤下: id={}, admin={}", id, adminId);
    }

    @Override
    public NoteStatsResponse getStats() {
        return NoteStatsResponse.builder()
                .pending(countByStatus(NoteStatus.PENDING))
                .published(countByStatus(NoteStatus.PUBLIC))
                .rejected(countByStatus(NoteStatus.REJECTED))
                .build();
    }

    private long countByStatus(int status) {
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Note::getStatus, status);
        return noteRepository.selectCount(wrapper);
    }

    private Note require(Long id) {
        Note note = noteRepository.selectById(id);
        if (note == null) {
            throw new BusinessException("笔记不存在");
        }
        return note;
    }

    private String username(Long userId) {
        if (userId == null) {
            return null;
        }
        User user = userRepository.selectById(userId);
        return user != null ? user.getUsername() : "未知用户";
    }

    private Page<Note> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
