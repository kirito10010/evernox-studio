package com.evernox.util;

import java.util.Map;

/**
 * 排序字段白名单解析。
 *
 * <p>白名单一般用 {@link Map#of} 声明，而 {@code Map.of} 产生的不可变 Map 不允许 null key：
 * {@code getOrDefault(null, def)} 会走到 {@code key.hashCode()} 直接抛 NPE，而不是返回默认值。
 * 前端排序参数是可选的，null 属于正常输入，因此统一在这里兜住。
 */
public final class SortColumnResolver {

    private SortColumnResolver() {
    }

    /**
     * @param whitelist     前端字段名 → 数据库列名
     * @param sortField     前端传入的排序字段，可能为 null / 空串 / 非法值
     * @param defaultColumn 兜底列名
     * @return 命中白名单的数据库列名，否则 defaultColumn
     */
    public static String resolve(Map<String, String> whitelist, String sortField, String defaultColumn) {
        if (sortField == null || sortField.isBlank()) {
            return defaultColumn;
        }
        return whitelist.getOrDefault(sortField, defaultColumn);
    }
}
