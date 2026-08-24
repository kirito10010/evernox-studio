package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.OvertimeRequest;
import com.evernox.dto.OvertimeResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 加班记录服务（纯私有，按 userId 隔离）
 */
public interface OvertimeService {

    OvertimeResponse createOvertime(OvertimeRequest request, Long userId);

    IPage<OvertimeResponse> listOvertime(Long userId, LocalDate startDate, LocalDate endDate, int page, int size);

    OvertimeResponse updateOvertime(Long id, OvertimeRequest request, Long userId);

    void deleteOvertime(Long id, Long userId);

    void batchDeleteOvertime(List<Long> ids, Long userId);
}
