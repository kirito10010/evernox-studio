package com.evernox.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 版本发布状态
 */
@Getter
@AllArgsConstructor
public enum ReleaseStatus {

    DRAFT(0, "草稿"),
    PUBLISHED(1, "已发布");

    private final Integer code;
    private final String label;
}
