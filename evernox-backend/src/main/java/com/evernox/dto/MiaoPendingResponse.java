package com.evernox.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 喵问答待确认题目面板：总数 + 当前可作答的题目
 */
@Data
@Builder
public class MiaoPendingResponse {

    /** 全站无答案题目数量 */
    private long total;

    /** 当前用户还能作答的题目（已投过票的已排除） */
    private List<MiaoQuestionResponse> items;
}
