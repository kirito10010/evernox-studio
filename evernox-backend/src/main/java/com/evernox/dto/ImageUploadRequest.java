package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 图片上传元数据请求
 * 注意: 文件内容的编解码完全由服务端负责，前端不再传 IV / 密钥
 */
@Data
public class ImageUploadRequest {

    /** 原始文件名 */
    @NotBlank(message = "文件名不能为空")
    private String originalName;

    /** MIME类型 */
    @NotBlank(message = "MIME类型不能为空")
    private String mimeType;

    /** 文件大小(bytes) */
    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    /** 图片宽度(可选) */
    private Integer width;

    /** 图片高度(可选) */
    private Integer height;

    /** 可见性: 0私密/1公开 */
    private Integer visibility = 0;

    /** 用途: 0图床照片/1相册封面/2网站分享封面/3笔记插图/4公告插图，决定落盘目录与是否出现在图床列表 */
    private Integer purpose = 0;

    /** 关联相册ID(可选) */
    private Long albumId;
}
