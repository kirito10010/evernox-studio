package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.PerformanceRecord;
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
 * 绩效记录 Mapper
 */
@Mapper
public interface PerformanceRecordRepository extends BaseMapper<PerformanceRecord> {

    @Select("SELECT DISTINCT DATE_FORMAT(work_date, '%Y-%m') AS m " +
            "FROM performance_record WHERE user_id = #{userId} AND deleted = 0 " +
            "ORDER BY m DESC")
    List<String> selectDistinctMonths(@Param("userId") Long userId);

    @Select("SELECT work_date, SUM(performance_days) AS total FROM performance_record " +
            "WHERE user_id = #{userId} AND deleted = 0 " +
            "AND work_date >= #{startDate} AND work_date <= #{endDate} " +
            "GROUP BY work_date ORDER BY work_date ASC")
    List<DailyTotal> selectDailyTotals(@Param("userId") Long userId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    /** 每日绩效人天聚合投影 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class DailyTotal {
        private LocalDate workDate;
        private BigDecimal total;
    }
}
