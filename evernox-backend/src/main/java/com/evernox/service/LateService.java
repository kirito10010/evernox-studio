package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.LateRequest;
import com.evernox.dto.LateResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 迟到记录服务（纯私有，按 userId 隔离）
 */
public interface LateService {

    LateResponse createLate(LateRequest request, Long userId);

    IPage<LateResponse> listLate(Long userId, LocalDate startDate, LocalDate endDate, int page, int size);

    void deleteLate(Long id, Long userId);

    void batchDeleteLate(List<Long> ids, Long userId);
}
