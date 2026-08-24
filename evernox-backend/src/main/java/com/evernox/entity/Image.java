package com.evernox.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 图片实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("image")
public class Image {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 上传者用户ID */
    private Long userId;

    /** 原始文件名 */
    private String originalName;

    /** 加密文件存储路径 */
    private String storagePath;

    /** 缩略图存储路径 */
    private String thumbnailPath;

    /** 缩略图 AES-GCM 初始化向量(Hex) */
    private String thumbnailIv;

    /** 文件大小(bytes) */
    private Long fileSize;

    /** MIME类型 */
    private String mimeType;

    /** 图片宽度 */
    private Integer width;

    /** 图片高度 */
    private Integer height;

    /** AES-GCM初始化向量(Hex) */
    private String iv;

    /** 0私密/1公开 */
    private Integer visibility;

    /**
     * 用途: 0图床照片/1相册封面/2网站分享封面
     *
     * 封面不属于用户的图床内容：列表与数量统计一律只取 0，落盘也走独立目录。
     */
    private Integer purpose;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
