package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 活动名及出现次数（用于筛选器，按次数从多到少排序）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolActivityNameCount {

    /** 活动全名，保持官网解析原文 */
    private String name;

    private long count;
}
