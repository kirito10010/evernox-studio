package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新模型结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OllamaUpdateResult {

    /** 本次选中的模型总数 */
    private int requested;

    /** 数据有变化的模型数 */
    private int updated;

    /** 数据无变化（但日期已刷新）的模型数 */
    private int unchanged;

    /** 已下架并删除的模型数 */
    private int deleted;

    /** 抓取失败、保留旧数据的模型数 */
    private int failed;

    private List<UpdatedModel> items;

    /** 状态：UPDATED / UNCHANGED / DELETED / FAILED */
    public static final String STATUS_UPDATED = "UPDATED";
    public static final String STATUS_UNCHANGED = "UNCHANGED";
    public static final String STATUS_DELETED = "DELETED";
    public static final String STATUS_FAILED = "FAILED";

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatedModel {
        private Long id;
        private String name;
        private String status;

        private Long pullsBefore;
        private Long pullsAfter;
        private String pullsBeforeText;
        private String pullsAfterText;

        private int tagsBefore;
        private int tagsAfter;

        /** 新增的变体名 */
        private List<String> addedTags;

        /** 删除的变体名 */
        private List<String> removedTags;

        /** 失败原因等补充说明 */
        private String message;
    }
}
