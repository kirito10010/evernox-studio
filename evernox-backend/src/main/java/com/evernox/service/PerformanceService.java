package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.PerformanceChartResponse;
import com.evernox.dto.PerformanceProjectRequest;
import com.evernox.dto.PerformanceProjectResponse;
import com.evernox.dto.PerformanceRecordRequest;
import com.evernox.dto.PerformanceRecordResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 绩效服务（纯私有数据，严格按 userId 隔离）
 */
public interface PerformanceService {

    // ==================== 项目配置 ====================

    PerformanceProjectResponse createProject(PerformanceProjectRequest request, Long userId);

    List<PerformanceProjectResponse> listProjects(Long userId);

    PerformanceProjectResponse updateProject(Long id, PerformanceProjectRequest request, Long userId);

    void deleteProject(Long id, Long userId);

    // ==================== 绩效记录 ====================

    PerformanceRecordResponse createRecord(PerformanceRecordRequest request, Long userId);

    PerformanceRecordResponse updateRecord(Long id, PerformanceRecordRequest request, Long userId);

    void deleteRecord(Long id, Long userId);

    IPage<PerformanceRecordResponse> listRecords(Long userId, Long projectId, Integer processType,
                                                 LocalDate startDate, LocalDate endDate, int page, int size);

    List<String> months(Long userId);

    PerformanceChartResponse chart(Long userId, LocalDate startDate, LocalDate endDate);
}
