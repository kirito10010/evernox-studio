package com.evernox.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 喵问答文件夹导入结果
 */
@Data
@Builder
public class MiaoImportResponse {

    /** 新增题目数 */
    private int imported;

    /** 已存在题目被补全（答案/排除项/图片）的数量 */
    private int updated;

    /** 跳过的条目数（重复、格式不合法） */
    private int skipped;

    /** 本次落盘的图片张数 */
    private int images;

    /** 题目引用了但文件夹里没找到的图片数 */
    private int missingImages;
}
