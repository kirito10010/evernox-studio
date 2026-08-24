package com.evernox.dto;

import com.evernox.entity.Note;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 笔记响应 DTO
 *
 * 列表接口不返回 content（正文可能很大），只给 summary；详情接口才带完整正文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteResponse {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String summary;
    private Integer pinned;
    private Integer status;
    private String rejectReason;
    private LocalDateTime submittedAt;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 作者用户名 */
    private String ownerName;

    /** 审批管理员用户名 */
    private String reviewerName;

    /** 列表用：不带正文 */
    public static NoteResponse brief(Note note) {
        return base(note).build();
    }

    /** 详情用：带正文 */
    public static NoteResponse detail(Note note) {
        return base(note).content(note.getContent()).build();
    }

    private static NoteResponseBuilder base(Note note) {
        return NoteResponse.builder()
                .id(note.getId())
                .userId(note.getUserId())
                .title(note.getTitle())
                .summary(note.getSummary())
                .pinned(note.getPinned())
                .status(note.getStatus())
                .rejectReason(note.getRejectReason())
                .submittedAt(note.getSubmittedAt())
                .reviewedAt(note.getReviewedAt())
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt());
    }
}
