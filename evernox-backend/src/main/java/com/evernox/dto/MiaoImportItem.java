package com.evernox.dto;

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
 *   "removed": "可空，游戏内已排除的选项文本",
 *   "image": "可空，如 images/10_10089.png"
 * }
 */
@Data
public class MiaoImportItem {

    private String question;

    private List<String> options;

    private String answer;

    private String removed;

    private String image;
}
