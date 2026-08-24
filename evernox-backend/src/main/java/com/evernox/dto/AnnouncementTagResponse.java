package com.evernox.dto;

import com.evernox.entity.AnnouncementTag;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 公告标签响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementTagResponse {

    private Long id;
    private String name;
    private String color;
    private Integer sort;

    public static AnnouncementTagResponse from(AnnouncementTag tag) {
        return AnnouncementTagResponse.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .sort(tag.getSort())
                .build();
    }
}
