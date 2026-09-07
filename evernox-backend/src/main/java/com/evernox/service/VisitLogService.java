package com.evernox.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.evernox.dto.VisitLogItemResponse;
import com.evernox.dto.VisitOverviewResponse;
import com.evernox.dto.VisitTrendItem;
import com.evernox.dto.VisitUserRankItem;

import java.util.List;

/**
 * 平台访问日志服务
 */
public interface VisitLogService {

    /** 记录登录事件（admin 跳过） */
    void recordLogin(Long userId, String ip, String userAgent);

    /** 记录访问事件（admin 跳过） */
    void recordVisit(Long userId, String ip, String userAgent, String path);

    VisitOverviewResponse overview();

    List<VisitTrendItem> trend(int days);

    List<VisitUserRankItem> rank(int top);

    IPage<VisitLogItemResponse> recent(int page, int size, String type, String username);

    /** 清理 90 天前数据 */
    void cleanup();
}
