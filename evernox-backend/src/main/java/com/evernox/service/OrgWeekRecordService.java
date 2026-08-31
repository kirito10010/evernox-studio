package com.evernox.service;

import com.evernox.dto.OrgWeekRecordResponse;
import com.evernox.dto.OrgWeekRecordUpdateRequest;

import java.time.LocalDate;
import java.util.List;

/**
 * 组织每周成员记录服务
 */
public interface OrgWeekRecordService {

    /** 历史周列表（去重 week_date，倒序） */
    List<LocalDate> listWeeks(Long organizationId);

    /** 指定组织、指定周记录列表 */
    List<OrgWeekRecordResponse> listRecords(Long organizationId, LocalDate weekDate);

    /** 一键生成（weekDate 为 null 时用本周周日），返回新建数量 */
    int generate(Long organizationId, LocalDate weekDate);

    /** 计算指定周本周积分/总积分，返回更新数量 */
    int calculate(Long organizationId, LocalDate weekDate);

    /** 手动编辑单条记录的活动数据字段 */
    void updateRecord(Long id, OrgWeekRecordUpdateRequest request);

    /** 为单条记录设置礼包并重算扣除后总积分 */
    OrgWeekRecordResponse setPackage(Long id, Long packageId);

    /** 清除单条记录已设置的礼包 */
    void clearPackage(Long id);

    /** 删除整周批次 */
    void deleteWeek(Long organizationId, LocalDate weekDate);
}
