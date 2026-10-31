package com.evernox.dto;

import lombok.Data;

/**
 * 「更新模型」范围筛选。全部可空：
 *  - 全部为空 → 按 last_updated_at 最久的 limit（默认 100）条；
 *  - 任意一项非空 → 更新该范围内的全部模型。
 */
@Data
public class OllamaUpdateRequest {

    /** official / third_party / community */
    private String category;

    /** 厂商 key */
    private String vendor;

    /** 参数量，如 7b */
    private String size;

    /** 能力：vision / tools / thinking / embedding / cloud */
    private String capability;

    /** true=只看 abliterated；false=只看标准版本 */
    private Boolean abliterated;

    /** 无筛选时的条数上限，默认 100 */
    private Integer limit;

    /**
     * 明确勾选的模型 ID（筛选后手动选择时传入）。
     * 非空时忽略上面的筛选与 limit，只更新这些模型。
     */
    private java.util.List<Long> ids;
}
