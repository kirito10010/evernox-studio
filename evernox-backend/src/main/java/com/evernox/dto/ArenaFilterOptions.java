package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Code Arena 筛选项：厂商选项（带数量） + 当前分类的价格上下限
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaFilterOptions {

    /** 厂商(开发方)选项，按数量倒序 */
    @Builder.Default
    private List<OrgOption> orgs = new ArrayList<>();

    /** 当前分类输入价格下限 */
    private BigDecimal minInput;

    /** 当前分类输入价格上限 */
    private BigDecimal maxInput;

    /** 当前分类输出价格下限 */
    private BigDecimal minOutput;

    /** 当前分类输出价格上限 */
    private BigDecimal maxOutput;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrgOption {
        /** 厂商名(英文原文) */
        private String key;
        /** 展示名 */
        private String label;
        /** 该分类下该厂商的模型数量 */
        private long count;
    }
}
