package com.evernox.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量向相册加入图片请求
 */
@Data
public class AdminAlbumImagesRequest {

    @NotEmpty(message = "图片ID列表不能为空")
    private List<Long> imageIds;
}
