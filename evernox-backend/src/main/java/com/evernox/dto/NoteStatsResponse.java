package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 笔记统计
 *
 * 用户侧：均为当前用户的数量；管理侧：pending/published/rejected 为全站数量，mine 不填充。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteStatsResponse {

    private Long mine;
    private Long pending;
    private Long published;
    private Long rejected;
}
