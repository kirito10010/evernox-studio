package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.PerformanceLate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 迟到记录 Mapper
 */
@Mapper
public interface PerformanceLateRepository extends BaseMapper<PerformanceLate> {

    @Select("SELECT work_date, SUM(late_days) AS total FROM performance_late " +
            "WHERE user_id = #{userId} AND deleted = 0 " +
            "AND work_date >= #{startDate} AND work_date <= #{endDate} " +
            "GROUP BY work_date ORDER BY work_date ASC")
    List<DailyTotal> selectDailyTotals(@Param("userId") Long userId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    @Select("SELECT COALESCE(SUM(late_minutes), 0) FROM performance_late " +
            "WHERE user_id = #{userId} AND deleted = 0 " +
            "AND work_date >= #{startDate} AND work_date <= #{endDate}")
    Long selectTotalLateMinutes(@Param("userId") Long userId,
                                @Param("startDate") LocalDate startDate,
                                @Param("endDate") LocalDate endDate);

    /** 每日迟到天数聚合投影 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class DailyTotal {
        private LocalDate workDate;
        private BigDecimal total;
    }
}
