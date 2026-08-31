package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.OrgOrganizationResponse;
import com.evernox.dto.OrgWeekRecordResponse;
import com.evernox.entity.OrgPointsConfig;
import com.evernox.exception.BusinessException;
import com.evernox.security.JwtTokenProvider;
import com.evernox.service.OrgMembershipService;
import com.evernox.service.OrgOrganizationService;
import com.evernox.service.OrgPointsConfigService;
import com.evernox.service.OrgWeekRecordService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 组织积分看板（普通用户只读，需加入组织后可见）
 */
@RestController
@RequestMapping("/org/points")
@RequiredArgsConstructor
public class OrgPointsController {

    private final OrgOrganizationService organizationService;
    private final OrgWeekRecordService weekRecordService;
    private final OrgPointsConfigService pointsConfigService;
    private final OrgMembershipService membershipService;
    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/organizations")
    public Result<List<OrgOrganizationResponse>> organizations() {
        return Result.success(organizationService.list());
    }

    @GetMapping("/weeks")
    public Result<List<LocalDate>> weeks(@RequestParam Long organizationId, HttpServletRequest http) {
        requireView(http, organizationId);
        return Result.success(weekRecordService.listWeeks(organizationId));
    }

    @GetMapping("/records")
    public Result<List<OrgWeekRecordResponse>> records(
            @RequestParam Long organizationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate,
            HttpServletRequest http) {
        requireView(http, organizationId);
        return Result.success(weekRecordService.listRecords(organizationId, weekDate));
    }

    @GetMapping("/config")
    public Result<OrgPointsConfig> config(@RequestParam Long organizationId, HttpServletRequest http) {
        requireView(http, organizationId);
        return Result.success(pointsConfigService.get(organizationId));
    }

    private void requireView(HttpServletRequest request, Long organizationId) {
        if (!membershipService.canView(getUserId(request), organizationId)) {
            throw new BusinessException("无权限访问该组织，请先申请加入");
        }
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
