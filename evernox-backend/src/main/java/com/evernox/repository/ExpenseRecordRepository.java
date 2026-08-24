package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.ExpenseRecord;
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
 * 消费记录 Mapper
 */
@Mapper
public interface ExpenseRecordRepository extends BaseMapper<ExpenseRecord> {

    /**
     * 有记录的月份列表（倒序，最新在前）。
     * DATE_FORMAT 结果形如 2026-08，按字符串倒序等价于按月份倒序。
     */
    @Select("SELECT DISTINCT DATE_FORMAT(expense_date, '%Y-%m') AS m " +
            "FROM expense_record WHERE user_id = #{userId} AND deleted = 0 " +
            "ORDER BY m DESC")
    List<String> selectDistinctMonths(@Param("userId") Long userId);

    /**
     * 范围内按天聚合（金额求和），仅返回有记录的日期，按日期升序。
     */
    @Select("SELECT expense_date, SUM(amount) AS total FROM expense_record " +
            "WHERE user_id = #{userId} AND deleted = 0 " +
            "AND expense_date >= #{startDate} AND expense_date <= #{endDate} " +
            "GROUP BY expense_date ORDER BY expense_date ASC")
    List<DailyTotal> selectDailyTotals(@Param("userId") Long userId,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);

    /**
     * 每日总额投影：expense_date 经下划线转驼峰映射为 expenseDate。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class DailyTotal {
        private LocalDate expenseDate;
        private BigDecimal total;
    }
}
