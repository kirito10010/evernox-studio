package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 官方公告刷新结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HyolRefreshResponse {

    /** 成功抓取/更新数量 */
    private int fetched;

    /** 失败跳过数量 */
    private int failed;
}
