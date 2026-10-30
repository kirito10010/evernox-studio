package com.evernox.dto;

import lombok.Data;

import java.util.List;

/**
 * JSON 导入的单道题目。
 *
 * 格式：
 * { "question": "...", "options": ["A", "B", "C", "D"], "answer": "..." }
 * answer 允许为空，表示待用户确认。
 */
@Data
public class QuizImportItem {

    private String question;

    private List<String> options;

    private String answer;
}
