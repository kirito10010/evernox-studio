package com.evernox.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 图片真实像素尺寸校正请求
 *
 * 上传时的 width/height 由浏览器测量后提交，可能因 EXIF 方向或历史数据而不准。
 * 前端渲染时若发现解码后的真实尺寸与库中记录不符，用这个接口把它纠正回来。
 */
@Data
public class ImageDimensionsRequest {

    /** 图片真实宽度(px) */
    @NotNull(message = "宽度不能为空")
    @Min(value = 1, message = "宽度必须大于 0")
    @Max(value = 65535, message = "宽度超出合理范围")
    private Integer width;

    /** 图片真实高度(px) */
    @NotNull(message = "高度不能为空")
    @Min(value = 1, message = "高度必须大于 0")
    @Max(value = 65535, message = "高度超出合理范围")
    private Integer height;
}
