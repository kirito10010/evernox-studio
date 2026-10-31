package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.MiaoAnswerVote;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 喵问答待确认答案投票 Mapper
 */
@Mapper
public interface MiaoAnswerVoteRepository extends BaseMapper<MiaoAnswerVote> {

    @Select("SELECT * FROM miao_answer_vote WHERE question_id = #{questionId} " +
            "AND user_id = #{userId} LIMIT 1")
    MiaoAnswerVote findByQuestionAndUser(@Param("questionId") Long questionId,
                                         @Param("userId") Long userId);

    /** 最近的 N 条投票，按时间倒序；用于判断连续 3 人是否选择一致 */
    @Select("SELECT * FROM miao_answer_vote WHERE question_id = #{questionId} " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<MiaoAnswerVote> findLatestVotes(@Param("questionId") Long questionId,
                                         @Param("limit") int limit);
}
