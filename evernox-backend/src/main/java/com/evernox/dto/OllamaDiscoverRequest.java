package com.evernox.dto;

import lombok.Data;

import java.util.List;

/**
 * 「获取新模型」请求：关键词可为空（空 = 扫描官方库）
 */
@Data
public class OllamaDiscoverRequest {

    private List<String> keywords;
}
