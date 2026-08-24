package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.SalaryConfigRequest;
import com.evernox.dto.SalaryConfigResponse;
import com.evernox.dto.SalaryRecordRequest;
import com.evernox.dto.SalaryRecordResponse;
import com.evernox.entity.SalaryConfig;
import com.evernox.entity.SalaryRecord;
import com.evernox.exception.BusinessException;
import com.evernox.repository.PerformanceLateRepository;
import com.evernox.repository.PerformanceOvertimeRepository;
import com.evernox.repository.PerformanceRecordRepository;
import com.evernox.repository.SalaryConfigRepository;
import com.evernox.repository.SalaryRecordRepository;
import com.evernox.service.SalaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 工资服务实现（纯私有数据，严格按 userId 隔离）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SalaryServiceImpl implements SalaryService {

    /** 金额/比例统一保留 5 位小数 */
    private static final int SCALE = 5;

    /** 每天工作时长（分钟）8小时 = 480分钟 */
    private static final BigDecimal WORK_MINUTES_PER_DAY = new BigDecimal("480");

    /** 绩效薪资：1 绩效 = 170 * 0.94 */
    private static final BigDecimal PERFORMANCE_UNIT_PRICE = new BigDecimal("170");
    private static final BigDecimal PERFORMANCE_FACTOR = new BigDecimal("0.94");

    /** 加班工资：1 天（8小时）= 136 */
    private static final BigDecimal OVERTIME_DAY_SALARY = new BigDecimal("136");

    // 默认工资配置
    private static final BigDecimal DEFAULT_BASE_SALARY = new BigDecimal("2000");
    private static final BigDecimal DEFAULT_POST_PERFORMANCE = new BigDecimal("500");
    private static final BigDecimal DEFAULT_MEAL_ALLOWANCE = new BigDecimal("200");
    private static final BigDecimal DEFAULT_HOUSING_ALLOWANCE = new BigDecimal("300");
    private static final BigDecimal DEFAULT_FULL_ATTENDANCE_BONUS = new BigDecimal("300");
    private static final BigDecimal DEFAULT_OTHER_BONUS = new BigDecimal("100");
    private static final BigDecimal DEFAULT_PENSION = new BigDecimal("360.32");
    private static final BigDecimal DEFAULT_MEDICAL_INSURANCE = new BigDecimal("90.08");
    private static final BigDecimal DEFAULT_UNEMPLOYMENT_INSURANCE = new BigDecimal("13.51");

    private final SalaryConfigRepository configRepository;
    private final SalaryRecordRepository recordRepository;
    private final PerformanceRecordRepository performanceRecordRepository;
    private final PerformanceOvertimeRepository overtimeRepository;
    private final PerformanceLateRepository lateRepository;

    // ==================== 工资配置 ====================

    @Override
    public SalaryConfigResponse getConfig(Long userId) {
        return SalaryConfigResponse.from(requireOrCreateConfig(userId));
    }

    @Override
    @Transactional
    public SalaryConfigResponse updateConfig(SalaryConfigRequest request, Long userId) {
        SalaryConfig config = requireOrCreateConfig(userId);
        config.setBaseSalary(request.getBaseSalary());
        config.setPostPerformance(request.getPostPerformance());
        config.setMealAllowance(request.getMealAllowance());
        config.setHousingAllowance(request.getHousingAllowance());
        config.setFullAttendanceBonus(request.getFullAttendanceBonus());
        config.setOtherBonus(request.getOtherBonus());
        config.setPension(request.getPension());
        config.setMedicalInsurance(request.getMedicalInsurance());
        config.setUnemploymentInsurance(request.getUnemploymentInsurance());
        configRepository.updateById(config);
        return SalaryConfigResponse.from(config);
    }

    @Override
    @Transactional
    public void createDefault(Long userId) {
        requireOrCreateConfig(userId);
    }

    // ==================== 工资记录 ====================

    @Override
    public SalaryRecordResponse preview(String month, BigDecimal attendanceDays, Long userId) {
        SalaryRecord record = computeRecord(userId, requireMonth(month), attendanceDays);
        return SalaryRecordResponse.from(record);
    }

    @Override
    @Transactional
    public SalaryRecordResponse createRecord(SalaryRecordRequest request, Long userId) {
        String month = request.getMonth().trim();
        long existing = recordRepository.selectCount(new LambdaQueryWrapper<SalaryRecord>()
                .eq(SalaryRecord::getUserId, userId)
                .eq(SalaryRecord::getMonth, month));
        if (existing > 0) {
            throw new BusinessException("该月工资记录已存在");
        }

        SalaryRecord record = computeRecord(userId, month, request.getAttendanceDays());
        recordRepository.insert(record);
        return SalaryRecordResponse.from(record);
    }

    @Override
    public List<SalaryRecordResponse> listRecords(Long userId) {
        LambdaQueryWrapper<SalaryRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SalaryRecord::getUserId, userId)
                .orderByDesc(SalaryRecord::getMonth);
        return recordRepository.selectList(wrapper).stream()
                .map(SalaryRecordResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteRecord(Long id, Long userId) {
        SalaryRecord record = recordRepository.selectById(id);
        if (record == null || !record.getUserId().equals(userId)) {
            throw new BusinessException("工资记录不存在");
        }
        recordRepository.deleteById(id);
    }

    // ==================== 核心计算 ====================

    /** 按月份 + 应出勤天数计算一条工资记录（不落库） */
    private SalaryRecord computeRecord(Long userId, String month, BigDecimal expectedDays) {
        YearMonth ym = parseMonth(month);
        LocalDate start = ym.minusMonths(1).atDay(26);
        LocalDate end = ym.atDay(25);

        // 每日聚合：绩效、加班、迟到（迟到按天）
        Map<LocalDate, BigDecimal> projectMap = performanceRecordRepository
                .selectDailyTotals(userId, start, end).stream()
                .collect(Collectors.toMap(PerformanceRecordRepository.DailyTotal::getWorkDate,
                        PerformanceRecordRepository.DailyTotal::getTotal, (a, b) -> a));
        Map<LocalDate, BigDecimal> overtimeMap = overtimeRepository
                .selectDailyTotals(userId, start, end).stream()
                .collect(Collectors.toMap(PerformanceOvertimeRepository.DailyTotal::getWorkDate,
                        PerformanceOvertimeRepository.DailyTotal::getTotal, (a, b) -> a));
        Map<LocalDate, BigDecimal> lateMap = lateRepository
                .selectDailyTotals(userId, start, end).stream()
                .collect(Collectors.toMap(PerformanceLateRepository.DailyTotal::getWorkDate,
                        PerformanceLateRepository.DailyTotal::getTotal, (a, b) -> a));

        // 实际上班天数 = 绩效记录去重天数
        BigDecimal actualDays = BigDecimal.valueOf(projectMap.size());

        // 加班天数 = 加班天数总和
        BigDecimal overtimeDays = round(overtimeMap.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 迟到分钟（用于出勤比分子）
        Long lateMinutesVal = lateRepository.selectTotalLateMinutes(userId, start, end);
        int lateMinutes = lateMinutesVal == null ? 0 : lateMinutesVal.intValue();

        // 净绩效：与记录绩效「总绩效（净）」完全一致
        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(projectMap.keySet());
        allDates.addAll(overtimeMap.keySet());
        allDates.addAll(lateMap.keySet());
        BigDecimal netPerformance = BigDecimal.ZERO;
        for (LocalDate date : allDates) {
            BigDecimal project = projectMap.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal overtime = overtimeMap.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal late = lateMap.getOrDefault(date, BigDecimal.ZERO);
            netPerformance = netPerformance.add(project.subtract(BigDecimal.ONE).subtract(overtime).add(late));
        }
        netPerformance = round(netPerformance);

        // 应出勤天数（可编辑，默认 = 实际上班天数）
        BigDecimal attendanceDays = expectedDays != null ? expectedDays : actualDays;

        SalaryConfig config = requireOrCreateConfig(userId);

        BigDecimal performanceSalary = round(
                netPerformance.multiply(PERFORMANCE_UNIT_PRICE).multiply(PERFORMANCE_FACTOR));
        BigDecimal overtimeSalary = round(overtimeDays.multiply(OVERTIME_DAY_SALARY));

        BigDecimal ratio = computeRatio(actualDays, lateMinutes, attendanceDays);

        BigDecimal baseSalary = round(config.getBaseSalary().multiply(ratio));
        BigDecimal postPerformance = round(config.getPostPerformance().multiply(ratio));
        BigDecimal mealAllowance = round(config.getMealAllowance().multiply(ratio));
        BigDecimal housingAllowance = round(config.getHousingAllowance().multiply(ratio));

        BigDecimal fullAttendanceBonus = round(config.getFullAttendanceBonus());
        BigDecimal otherBonus = round(config.getOtherBonus());
        BigDecimal pension = round(config.getPension());
        BigDecimal medicalInsurance = round(config.getMedicalInsurance());
        BigDecimal unemploymentInsurance = round(config.getUnemploymentInsurance());

        BigDecimal totalSalary = round(baseSalary.add(postPerformance)
                .add(mealAllowance).add(housingAllowance)
                .add(fullAttendanceBonus).add(otherBonus)
                .add(performanceSalary).add(overtimeSalary)
                .subtract(pension).subtract(medicalInsurance).subtract(unemploymentInsurance));

        return SalaryRecord.builder()
                .userId(userId)
                .month(month)
                .startDate(start)
                .endDate(end)
                .attendanceDays(round(attendanceDays))
                .actualAttendanceDays(actualDays)
                .performanceDays(netPerformance)
                .performanceSalary(performanceSalary)
                .overtimeDays(overtimeDays)
                .overtimeSalary(overtimeSalary)
                .lateMinutes(lateMinutes)
                .attendanceRatio(ratio)
                .baseSalary(baseSalary)
                .postPerformance(postPerformance)
                .mealAllowance(mealAllowance)
                .housingAllowance(housingAllowance)
                .fullAttendanceBonus(fullAttendanceBonus)
                .otherBonus(otherBonus)
                .pension(pension)
                .medicalInsurance(medicalInsurance)
                .unemploymentInsurance(unemploymentInsurance)
                .totalSalary(totalSalary)
                .deleted(0)
                .build();
    }

    /** 出勤比 = (实际上班天数*480 - 迟到分钟) / (应出勤天数*480)，clamp 到 [0,1]，保留 5 位小数 */
    private BigDecimal computeRatio(BigDecimal actualDays, int lateMinutes, BigDecimal expectedDays) {
        if (expectedDays == null || expectedDays.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal actualMinutes = actualDays.multiply(WORK_MINUTES_PER_DAY)
                .subtract(BigDecimal.valueOf(lateMinutes));
        if (actualMinutes.compareTo(BigDecimal.ZERO) < 0) {
            actualMinutes = BigDecimal.ZERO;
        }
        BigDecimal expectedMinutes = expectedDays.multiply(WORK_MINUTES_PER_DAY);
        BigDecimal ratio = actualMinutes.divide(expectedMinutes, SCALE, RoundingMode.HALF_UP);
        if (ratio.compareTo(BigDecimal.ONE) > 0) {
            ratio = BigDecimal.ONE;
        }
        return ratio;
    }

    private BigDecimal round(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private String requireMonth(String month) {
        if (month == null || month.isBlank()) {
            throw new BusinessException("请选择月份");
        }
        return month.trim();
    }

    private YearMonth parseMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            throw new BusinessException("月份格式不正确");
        }
    }

    /** 取出当前用户配置；不存在则用默认值创建（幂等） */
    private SalaryConfig requireOrCreateConfig(Long userId) {
        SalaryConfig config = configRepository.selectOne(new LambdaQueryWrapper<SalaryConfig>()
                .eq(SalaryConfig::getUserId, userId));
        if (config == null) {
            config = defaultConfig(userId);
            configRepository.insert(config);
        }
        return config;
    }

    private SalaryConfig defaultConfig(Long userId) {
        return SalaryConfig.builder()
                .userId(userId)
                .baseSalary(DEFAULT_BASE_SALARY)
                .postPerformance(DEFAULT_POST_PERFORMANCE)
                .mealAllowance(DEFAULT_MEAL_ALLOWANCE)
                .housingAllowance(DEFAULT_HOUSING_ALLOWANCE)
                .fullAttendanceBonus(DEFAULT_FULL_ATTENDANCE_BONUS)
                .otherBonus(DEFAULT_OTHER_BONUS)
                .pension(DEFAULT_PENSION)
                .medicalInsurance(DEFAULT_MEDICAL_INSURANCE)
                .unemploymentInsurance(DEFAULT_UNEMPLOYMENT_INSURANCE)
                .deleted(0)
                .build();
    }
}
