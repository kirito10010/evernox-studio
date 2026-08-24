package com.evernox.dto;

import com.evernox.entity.Image;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图片响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageResponse {

    private Long id;
    private Long userId;
    private String originalName;
    private Long fileSize;
    private String mimeType;
    private Integer width;
    private Integer height;
    private String iv;
    private Integer visibility;

    /** 用途: 0图床照片/1相册封面/2网站分享封面 */
    private Integer purpose;
    private LocalDateTime createdAt;

    /** 是否存在缩略图 */
    private Boolean hasThumbnail;

    /** 上传者用户名 */
    private String uploaderName;

    public static ImageResponse from(Image image) {
        return ImageResponse.builder()
                .id(image.getId())
                .userId(image.getUserId())
                .originalName(image.getOriginalName())
                .fileSize(image.getFileSize())
                .mimeType(image.getMimeType())
                .width(image.getWidth())
                .height(image.getHeight())
                .iv(image.getIv())
                .visibility(image.getVisibility())
                .purpose(image.getPurpose())
                .hasThumbnail(image.getThumbnailPath() != null)
                .createdAt(image.getCreatedAt())
                .build();
    }
}
