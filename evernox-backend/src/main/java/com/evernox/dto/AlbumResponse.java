package com.evernox.dto;

import com.evernox.entity.Album;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 相册响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlbumResponse {

    private Long id;
    private Long userId;
    private String name;
    private String description;
    private Long coverImageId;
    private Integer visibility;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 创建者用户名 */
    private String creatorName;

    /** 相册内图片数量 */
    private Integer imageCount;

    public static AlbumResponse from(Album album) {
        return AlbumResponse.builder()
                .id(album.getId())
                .userId(album.getUserId())
                .name(album.getName())
                .description(album.getDescription())
                .coverImageId(album.getCoverImageId())
                .visibility(album.getVisibility())
                .createdAt(album.getCreatedAt())
                .updatedAt(album.getUpdatedAt())
                .build();
    }
}
