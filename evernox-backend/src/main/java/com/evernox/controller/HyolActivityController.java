package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.HyolActivitySyncStatus;
import com.evernox.dto.HyolActivityTimelineResponse;
import com.evernox.service.HyolActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 火影忍者OL 活动时间轴控制器
 */
@RestController
@RequestMapping("/hyol/activity")
@RequiredArgsConstructor
public class HyolActivityController {

    private final HyolActivityService activityService;

    /** 时间轴数据（公开只读）：names 为活动全名多选，keyword 为名称模糊匹配 */
    @GetMapping("/timeline")
    public Result<HyolActivityTimelineResponse> timeline(
            @RequestParam(required = false) List<String> names,
            @RequestParam(required = false) String keyword) {
        return Result.success(activityService.timeline(names, keyword));
    }

    /** 同步进度（前端轮询） */
    @GetMapping("/sync/status")
    public Result<HyolActivitySyncStatus> syncStatus() {
        return Result.success(activityService.getSyncStatus());
    }

    /** 触发同步：默认增量，full=true 时忽略已入库公告全量重扫 */
    @PostMapping("/sync")
    @PreAuthorize("hasRole('admin')")
    public Result<HyolActivitySyncStatus> sync(@RequestParam(defaultValue = "false") boolean full) {
        return Result.success("已开始同步", activityService.startSync(full));
    }
}
