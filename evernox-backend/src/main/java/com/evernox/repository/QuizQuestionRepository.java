package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.QuizQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 火影忍者OL测验题目 Mapper
 */
@Mapper
public interface QuizQuestionRepository extends BaseMapper<QuizQuestion> {

    /**
     * 投票时锁住题目行，保证「第三票写入答案 + 发积分」只执行一次。
     */
    @Select("SELECT * FROM quiz_question WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    QuizQuestion selectByIdForUpdate(@Param("id") Long id);

    /** 全站无答案题目数量（用于右侧计数） */
    @Select("SELECT COUNT(*) FROM quiz_question WHERE status = 1 AND deleted = 0 " +
            "AND (answer IS NULL OR answer = '')")
    long countPending();

    /**
     * 当前用户还能作答的无答案题目；已投过票的排除。
     */
    @Select("<script>" +
            "SELECT q.* FROM quiz_question q " +
            "WHERE q.status = 1 AND q.deleted = 0 AND (q.answer IS NULL OR q.answer = '') " +
            "<if test='userId != null'>" +
            "AND NOT EXISTS (SELECT 1 FROM quiz_answer_vote v " +
            "WHERE v.question_id = q.id AND v.user_id = #{userId}) " +
            "</if>" +
            "ORDER BY q.id DESC LIMIT #{limit}" +
            "</script>")
    List<QuizQuestion> findPending(@Param("userId") Long userId, @Param("limit") int limit);
}

