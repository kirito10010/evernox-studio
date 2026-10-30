package com.evernox.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON 导入结果
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizImportResponse {

    /** 成功导入数量 */
    private int imported;

    /** 题库中已有该题但答案为空，本次补全了多少条 */
    private int updated;

    private int skipped;
}

