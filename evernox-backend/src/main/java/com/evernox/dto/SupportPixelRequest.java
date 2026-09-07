package com.evernox.dto;

import lombok.Data;

/**
 * 绘制/擦除像素请求；color 为空表示擦除
 */
@Data
public class SupportPixelRequest {

    private Integer x;
    private Integer y;
    private String color;
}
