package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Excel 导入结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizImportResponse {

    /** 成功导入数量 */
    private int imported;

    /** 跳过数量（重复/空行） */
    private int skipped;
}
