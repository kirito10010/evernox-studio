package com.evernox.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.List;

/**
 * 喵问答文件夹导入的单道题目。
 *
 * 文件格式（questions.json 数组元素）：
 * {
 *   "question": "...",
 *   "options": ["A", "B", "C", "D"],
 *   "answer": "可空，空表示待用户确认",
 *   "removed": ["已排除选项", "可多个"],
 *   "image": "可空，如 images/10_10089.png"
 * }
 *
 * removed 兼容旧格式：单个字符串也能解析（ACCEPT_SINGLE_VALUE_AS_ARRAY）。
 */
@Data
public class MiaoImportItem {

    private String question;

    private List<String> options;

    private String answer;

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    private List<String> removed;

    private String image;
}
