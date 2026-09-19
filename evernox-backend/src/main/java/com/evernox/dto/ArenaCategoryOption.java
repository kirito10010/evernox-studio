package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Code Arena 分类选项（驱动前端类别切换按钮组）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArenaCategoryOption {

    /** 分类slug(URL 路径段) */
    private String slug;

    /** 中文标签 */
    private String label;

    /** 站点英文标签(用于 tooltip) */
    private String labelEn;

    /** 分组: category/domain */
    private String group;

    /** 库中该分类的模型数量 */
    private long modelCount;
}
