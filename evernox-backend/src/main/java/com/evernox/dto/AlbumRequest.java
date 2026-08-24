package com.evernox.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 相册创建/更新请求
 */
@Data
public class AlbumRequest {

    /** 相册名称 */
    @NotBlank(message = "相册名称不能为空")
    private String name;

    /** 相册描述 */
    private String description;

    /** 封面图片ID */
    private Long coverImageId;

    /** 可见性: 0私密/1公开 */
    private Integer visibility = 0;
}
