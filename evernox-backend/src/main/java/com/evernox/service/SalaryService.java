package com.evernox.service;

import com.evernox.dto.SalaryConfigRequest;
import com.evernox.dto.SalaryConfigResponse;
import com.evernox.dto.SalaryRecordRequest;
import com.evernox.dto.SalaryRecordResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工资服务（纯私有，按 userId 隔离）
 */
public interface SalaryService {

    /** 获取当前用户工资配置（不存在则用默认值创建） */
    SalaryConfigResponse getConfig(Long userId);

    /** 更新当前用户工资配置 */
    SalaryConfigResponse updateConfig(SalaryConfigRequest request, Long userId);

    /** 为注册用户创建默认工资配置（幂等） */
    void createDefault(Long userId);

    /** 计算工资预览（不落库），attendanceDays 为空时用绩效记录的去重天数 */
    SalaryRecordResponse preview(String month, BigDecimal attendanceDays, Long userId);

    /** 保存工资记录（每用户每月唯一） */
    SalaryRecordResponse createRecord(SalaryRecordRequest request, Long userId);

    /** 工资记录列表（按月份倒序） */
    List<SalaryRecordResponse> listRecords(Long userId);

    /** 删除工资记录 */
    void deleteRecord(Long id, Long userId);
}
