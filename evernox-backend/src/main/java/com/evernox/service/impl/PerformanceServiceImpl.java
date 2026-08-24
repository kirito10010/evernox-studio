package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.evernox.dto.PerformanceChartResponse;
import com.evernox.dto.PerformanceProjectRequest;
import com.evernox.dto.PerformanceProjectResponse;
import com.evernox.dto.PerformanceRecordRequest;
import com.evernox.dto.PerformanceRecordResponse;
import com.evernox.entity.PerformanceLate;
import com.evernox.entity.PerformanceOvertime;
import com.evernox.entity.PerformanceProject;
import com.evernox.entity.PerformanceRecord;
import com.evernox.exception.BusinessException;
import com.evernox.repository.PerformanceLateRepository;
import com.evernox.repository.PerformanceOvertimeRepository;
import com.evernox.repository.PerformanceProjectRepository;
import com.evernox.repository.PerformanceRecordRepository;
import com.evernox.service.PerformanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 绩效服务实现（纯私有数据，严格按 userId 隔离）
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class PerformanceServiceImpl implements PerformanceService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_CHART_DAYS = 366;
    /** 工序类型: 质检 */
    private static final int PROCESS_INSPECT = 1;

    private final PerformanceProjectRepository projectRepository;
    private final PerformanceRecordRepository recordRepository;
    private final PerformanceOvertimeRepository overtimeRepository;
    private final PerformanceLateRepository lateRepository;

    // ==================== 项目配置 ====================

    @Override
    @Transactional
    public PerformanceProjectResponse createProject(PerformanceProjectRequest request, Long userId) {
        String name = request.getName().trim();
        ensureNameUnique(name, userId, null);

        PerformanceProject project = PerformanceProject.builder()
                .userId(userId)
                .name(name)
                .workQuota(request.getWorkQuota())
                .inspectQuota(request.getInspectQuota())
                .deleted(0)
                .build();
        projectRepository.insert(project);
        return PerformanceProjectResponse.from(project);
    }

    @Override
    public List<PerformanceProjectResponse> listProjects(Long userId) {
        LambdaQueryWrapper<PerformanceProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PerformanceProject::getUserId, userId)
                .orderByDesc(PerformanceProject::getId);
        return projectRepository.selectList(wrapper).stream()
                .map(PerformanceProjectResponse::from)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PerformanceProjectResponse updateProject(Long id, PerformanceProjectRequest request, Long userId) {
        PerformanceProject project = requireOwnedProject(id, userId);
        String name = request.getName().trim();
        ensureNameUnique(name, userId, id);

        project.setName(name);
        project.setWorkQuota(request.getWorkQuota());
        project.setInspectQuota(request.getInspectQuota());
        projectRepository.updateById(project);
        return PerformanceProjectResponse.from(project);
    }

    @Override
    @Transactional
    public void deleteProject(Long id, Long userId) {
        requireOwnedProject(id, userId);
        long referenced = recordRepository.selectCount(new LambdaQueryWrapper<PerformanceRecord>()
                .eq(PerformanceRecord::getUserId, userId)
                .eq(PerformanceRecord::getProjectId, id));
        if (referenced > 0) {
            throw new BusinessException("该项目下还有绩效记录，请先删除相关记录");
        }
        projectRepository.deleteById(id);
    }

    // ==================== 绩效记录 ====================

    @Override
    @Transactional
    public PerformanceRecordResponse createRecord(PerformanceRecordRequest request, Long userId) {
        PerformanceProject project = requireOwnedProjectForRecord(request.getProjectId(), userId);
        BigDecimal quota = resolveQuota(project, request.getProcessType());
        BigDecimal performanceDays = request.getActualWorkload()
                .divide(quota, 5, RoundingMode.HALF_UP);

        PerformanceRecord record = PerformanceRecord.builder()
                .userId(userId)
                .projectId(project.getId())
                .workDate(request.getWorkDate())
                .processType(request.getProcessType())
                .quota(quota)
                .actualWorkload(request.getActualWorkload())
                .performanceDays(performanceDays)
                .deleted(0)
                .build();
        recordRepository.insert(record);
        return PerformanceRecordResponse.from(record, project.getName());
    }

    @Override
    @Transactional
    public PerformanceRecordResponse updateRecord(Long id, PerformanceRecordRequest request, Long userId) {
        PerformanceRecord record = requireOwnedRecord(id, userId);
        PerformanceProject project = requireOwnedProjectForRecord(request.getProjectId(), userId);
        BigDecimal quota = resolveQuota(project, request.getProcessType());
        BigDecimal performanceDays = request.getActualWorkload()
                .divide(quota, 5, RoundingMode.HALF_UP);

        record.setProjectId(project.getId());
        record.setWorkDate(request.getWorkDate());
        record.setProcessType(request.getProcessType());
        record.setQuota(quota);
        record.setActualWorkload(request.getActualWorkload());
        record.setPerformanceDays(performanceDays);
        recordRepository.updateById(record);
        return PerformanceRecordResponse.from(record, project.getName());
    }

    @Override
    @Transactional
    public void deleteRecord(Long id, Long userId) {
        requireOwnedRecord(id, userId);
        recordRepository.deleteById(id);
    }

    @Override
    public IPage<PerformanceRecordResponse> listRecords(Long userId, Long projectId, Integer processType,
                                                        LocalDate startDate, LocalDate endDate, int page, int size) {
        LambdaQueryWrapper<PerformanceRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PerformanceRecord::getUserId, userId)
                .eq(projectId != null, PerformanceRecord::getProjectId, projectId)
                .eq(processType != null, PerformanceRecord::getProcessType, processType)
                .ge(startDate != null, PerformanceRecord::getWorkDate, startDate)
                .le(endDate != null, PerformanceRecord::getWorkDate, endDate)
                .orderByDesc(PerformanceRecord::getWorkDate)
                .orderByDesc(PerformanceRecord::getId);

        IPage<PerformanceRecord> raw = recordRepository.selectPage(newPage(page, size), wrapper);

        Set<Long> projectIds = raw.getRecords().stream()
                .map(PerformanceRecord::getProjectId)
                .collect(Collectors.toSet());
        Map<Long, String> nameMap = projectNameMap(projectIds);

        Page<PerformanceRecordResponse> result = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        result.setRecords(raw.getRecords().stream()
                .map(r -> PerformanceRecordResponse.from(r, nameMap.get(r.getProjectId())))
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public List<String> months(Long userId) {
        return recordRepository.selectDistinctMonths(userId);
    }

    @Override
    public PerformanceChartResponse chart(Long userId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null) {
            throw new BusinessException("请选择开始日期");
        }
        if (endDate == null) {
            endDate = startDate;
        }
        if (endDate.isBefore(startDate)) {
            throw new BusinessException("结束日期不能早于开始日期");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) > MAX_CHART_DAYS) {
            throw new BusinessException("查询范围过大，最多 " + MAX_CHART_DAYS + " 天");
        }

        // 三张表的每日聚合
        Map<LocalDate, BigDecimal> projectMap = recordRepository
                .selectDailyTotals(userId, startDate, endDate).stream()
                .collect(Collectors.toMap(PerformanceRecordRepository.DailyTotal::getWorkDate,
                        PerformanceRecordRepository.DailyTotal::getTotal, (a, b) -> a));
        Map<LocalDate, BigDecimal> overtimeMap = overtimeRepository
                .selectDailyTotals(userId, startDate, endDate).stream()
                .collect(Collectors.toMap(PerformanceOvertimeRepository.DailyTotal::getWorkDate,
                        PerformanceOvertimeRepository.DailyTotal::getTotal, (a, b) -> a));
        Map<LocalDate, BigDecimal> lateMap = lateRepository
                .selectDailyTotals(userId, startDate, endDate).stream()
                .collect(Collectors.toMap(PerformanceLateRepository.DailyTotal::getWorkDate,
                        PerformanceLateRepository.DailyTotal::getTotal, (a, b) -> a));

        // 合并所有有记录的日期（项目/加班/迟到）
        Set<LocalDate> allDates = new TreeSet<>();
        allDates.addAll(projectMap.keySet());
        allDates.addAll(overtimeMap.keySet());
        allDates.addAll(lateMap.keySet());

        // 每天净绩效 = 项目绩效 - 1 - 加班天数 + 迟到天数
        List<PerformanceChartResponse.Point> points = new ArrayList<>();
        BigDecimal totalDays = BigDecimal.ZERO;
        for (LocalDate date : allDates) {
            BigDecimal project = projectMap.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal overtime = overtimeMap.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal late = lateMap.getOrDefault(date, BigDecimal.ZERO);
            BigDecimal net = project.subtract(BigDecimal.ONE).subtract(overtime).add(late);
            points.add(PerformanceChartResponse.Point.builder()
                    .date(date)
                    .total(net)
                    .build());
            totalDays = totalDays.add(net);
        }

        long count = recordRepository.selectCount(new LambdaQueryWrapper<PerformanceRecord>()
                .eq(PerformanceRecord::getUserId, userId)
                .between(PerformanceRecord::getWorkDate, startDate, endDate))
                + overtimeRepository.selectCount(new LambdaQueryWrapper<PerformanceOvertime>()
                .eq(PerformanceOvertime::getUserId, userId)
                .between(PerformanceOvertime::getWorkDate, startDate, endDate))
                + lateRepository.selectCount(new LambdaQueryWrapper<PerformanceLate>()
                .eq(PerformanceLate::getUserId, userId)
                .between(PerformanceLate::getWorkDate, startDate, endDate));

        return PerformanceChartResponse.builder()
                .points(points)
                .totalDays(totalDays)
                .workDays(allDates.size())
                .count(count)
                .build();
    }

    // ==================== 内部工具 ====================

    /** 按工序类型取定额：0作业→workQuota，1质检→inspectQuota */
    private BigDecimal resolveQuota(PerformanceProject project, Integer processType) {
        if (processType != null && processType == PROCESS_INSPECT) {
            return project.getInspectQuota();
        }
        return project.getWorkQuota();
    }

    private void ensureNameUnique(String name, Long userId, Long excludeId) {
        LambdaQueryWrapper<PerformanceProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PerformanceProject::getUserId, userId)
                .eq(PerformanceProject::getName, name)
                .ne(excludeId != null, PerformanceProject::getId, excludeId);
        if (projectRepository.selectCount(wrapper) > 0) {
            throw new BusinessException("项目名称已存在");
        }
    }

    private Map<Long, String> projectNameMap(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<PerformanceProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(PerformanceProject::getId, ids);
        return projectRepository.selectList(wrapper).stream()
                .collect(Collectors.toMap(PerformanceProject::getId, PerformanceProject::getName, (a, b) -> a));
    }

    /** 取出并校验项目归属；不存在或非本人一律按「不存在」处理，避免泄露他人数据 */
    private PerformanceProject requireOwnedProjectForRecord(Long projectId, Long userId) {
        PerformanceProject project = projectRepository.selectById(projectId);
        if (project == null || !project.getUserId().equals(userId)) {
            throw new BusinessException("项目不存在");
        }
        return project;
    }

    private PerformanceProject requireOwnedProject(Long id, Long userId) {
        PerformanceProject project = projectRepository.selectById(id);
        if (project == null) {
            throw new BusinessException("项目不存在");
        }
        if (!project.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该项目");
        }
        return project;
    }

    private PerformanceRecord requireOwnedRecord(Long id, Long userId) {
        PerformanceRecord record = recordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException("绩效记录不存在");
        }
        if (!record.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权操作该绩效记录");
        }
        return record;
    }

    private Page<PerformanceRecord> newPage(int page, int size) {
        return new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }
}
