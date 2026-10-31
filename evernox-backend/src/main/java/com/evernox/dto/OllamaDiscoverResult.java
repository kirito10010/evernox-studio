package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 获取新模型结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaDiscoverResult {

    /** 本次新增的模型数 */
    private int added;

    /** 新增模型带来的变体总数 */
    private int variants;

    /** 扫描过的模型数（含已存在的） */
    private int scanned;

    /** 使用过的关键词；空表示扫描官方库 */
    private java.util.List<String> keywords;

    /** 列表是否完整抓完；false 表示可能还有未收录模型 */
    private boolean complete;

    /** 新增模型明细 */
    private java.util.List<DiscoveredModel> models;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DiscoveredModel {
        private Long id;
        private String name;
        private String pullsText;
        private int tagCount;
    }
}
