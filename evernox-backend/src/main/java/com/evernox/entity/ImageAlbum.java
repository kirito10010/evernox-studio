package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图片-相册关联实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("image_album")
public class ImageAlbum {

    /** 图片ID */
    @TableId(value = "image_id")
    private Long imageId;

    /** 相册ID */
    private Long albumId;

    private LocalDateTime createdAt;
}
