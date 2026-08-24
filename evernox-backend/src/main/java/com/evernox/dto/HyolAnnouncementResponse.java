package com.evernox.dto;

import com.evernox.entity.HyolAnnouncement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 火影忍者OL官方公告响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolAnnouncementResponse {

    private Long id;
    private String title;
    private String sourceUrl;
    private String publishTime;
    private String content;
    private LocalDateTime createdAt;

    /** 列表用：不带正文 */
    public static HyolAnnouncementResponse brief(HyolAnnouncement a) {
        return base(a).build();
    }

    /** 详情用：带正文 */
    public static HyolAnnouncementResponse detail(HyolAnnouncement a) {
        return base(a).content(a.getContent()).build();
    }

    private static HyolAnnouncementResponseBuilder base(HyolAnnouncement a) {
        return HyolAnnouncementResponse.builder()
                .id(a.getId())
                .title(a.getTitle())
                .sourceUrl(a.getSourceUrl())
                .publishTime(a.getPublishTime())
                .createdAt(a.getCreatedAt());
    }
}
