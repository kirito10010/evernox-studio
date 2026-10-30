package com.evernox.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 待确认题目投票结果
 */
@Data
@Builder
public class QuizVoteResponse {

    /** 是否已经达到 2 票并写入题库 */
    private boolean resolved;

    /** 确认后的答案；未确认时为 null */
    private String answer;

    /** 当前用户是否获得积分 */
    private boolean rewarded;

    /** 本次奖励积分（默认 10） */
    private int points;

    /** 当前选项已有的票数 */
    private long votes;

    private String message;
}
