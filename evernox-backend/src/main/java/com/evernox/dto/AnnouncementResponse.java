package com.evernox.dto;

import com.evernox.entity.Announcement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 公告响应 DTO
 *
 * 列表接口不返回 content，详情接口才带完整正文；read 仅用户列表使用。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {

    private Long id;
    private String title;
    private String content;
    private Long tagId;
    private String tagName;
    private String tagColor;
    private Long createdBy;
    private String createdByName;

    /** 用户列表用：当前用户是否已读（管理员列表为 null） */
    private Boolean read;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 列表用：不带正文 */
    public static AnnouncementResponse brief(Announcement a) {
        return base(a).build();
    }

    /** 详情用：带正文 */
    public static AnnouncementResponse detail(Announcement a) {
        return base(a).content(a.getContent()).build();
    }

    private static AnnouncementResponseBuilder base(Announcement a) {
        return AnnouncementResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .tagId(a.getTagId())
                .createdBy(a.getCreatedBy())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt());
    }
}
