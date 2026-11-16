package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.OrgDistributionPlanRequest;
import com.evernox.dto.OrgDistributionPlanResponse;
import com.evernox.dto.OrgDistributionRunResponse;
import com.evernox.dto.OrgImportResponse;
import com.evernox.dto.OrgRenegadeLeaderImportResponse;
import com.evernox.dto.OrgMemberImportApplyRequest;
import com.evernox.dto.OrgMemberImportApplyResponse;
import com.evernox.dto.OrgMemberImportPreviewResponse;
import com.evernox.dto.OrgMemberBatchMoveRequest;
import com.evernox.dto.OrgMemberBatchPositionRequest;
import com.evernox.dto.OrgMemberBatchResponse;
import com.evernox.dto.OrgMemberIdsRequest;
import com.evernox.dto.OrgMemberRequest;
import com.evernox.dto.OrgMemberResponse;
import com.evernox.dto.OrgMembershipApplicationResponse;
import com.evernox.dto.OrgOrganizationRequest;
import com.evernox.dto.OrgOrganizationResponse;
import com.evernox.dto.OrgRewardPackageRequest;
import com.evernox.dto.OrgWeekRecordResponse;
import com.evernox.dto.OrgWeekRecordUpdateRequest;
import com.evernox.entity.OrgPointsConfig;
import com.evernox.entity.OrgRewardPackage;
import com.evernox.security.JwtTokenProvider;
import com.evernox.security.SecurityUtils;
import com.evernox.service.OrgExcelImportService;
import com.evernox.service.OrgDistributionService;
import com.evernox.service.OrgMemberService;
import com.evernox.service.OrgMembershipService;
import com.evernox.service.OrgOrganizationService;
import com.evernox.service.OrgOwnerGuard;
import com.evernox.service.OrgPointsConfigService;
import com.evernox.service.OrgRewardPackageService;
import com.evernox.service.OrgWeekRecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 组织积分与功勋礼包管理控制器（仅管理员）
 */
@RestController
@RequestMapping("/admin/org")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('admin','super_member')")
public class AdminOrgController {

    private final OrgOrganizationService organizationService;
    private final OrgMemberService memberService;
    private final OrgRewardPackageService packageService;
    private final OrgPointsConfigService pointsConfigService;
    private final OrgWeekRecordService weekRecordService;
    private final OrgDistributionService distributionService;
    private final OrgExcelImportService excelImportService;
    private final OrgMembershipService membershipService;
    private final OrgOwnerGuard ownerGuard;
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== 组织 ====================

    @GetMapping("/organizations")
    public Result<List<OrgOrganizationResponse>> organizations() {
        // 组织按账号隔离：只返回自己创建的组织
        return Result.success(organizationService.list(SecurityUtils.currentUserId()));
    }

    @PostMapping("/organizations")
    public Result<OrgOrganizationResponse> createOrganization(@Valid @RequestBody OrgOrganizationRequest request,
                                                              HttpServletRequest http) {
        return Result.success("添加成功", organizationService.create(request, getUserId(http)));
    }

    @PutMapping("/organizations/{id}")
    public Result<OrgOrganizationResponse> updateOrganization(@PathVariable Long id,
                                                              @Valid @RequestBody OrgOrganizationRequest request) {
        return Result.success("更新成功",
                organizationService.update(id, request, SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/organizations/{id}")
    public Result<Void> deleteOrganization(@PathVariable Long id) {
        organizationService.delete(id, SecurityUtils.currentUserId());
        return Result.success("删除成功", null);
    }

    // ==================== 成员 ====================

    @GetMapping("/members")
    public Result<List<OrgMemberResponse>> members() {
        return Result.success(memberService.listByOwner(SecurityUtils.currentUserId()));
    }

    @PostMapping("/members")
    public Result<OrgMemberResponse> createMember(@Valid @RequestBody OrgMemberRequest request) {
        ownerGuard.requireOrg(request.getOrganizationId(), SecurityUtils.currentUserId());
        return Result.success("添加成功", memberService.create(request));
    }

    @PutMapping("/members/{id}")
    public Result<OrgMemberResponse> updateMember(@PathVariable Long id,
                                                  @Valid @RequestBody OrgMemberRequest request) {
        Long ownerId = SecurityUtils.currentUserId();
        ownerGuard.requireMember(id, ownerId);
        ownerGuard.requireOrg(request.getOrganizationId(), ownerId);
        return Result.success("更新成功", memberService.update(id, request));
    }

    @PutMapping("/members/{id}/status")
    public Result<Void> updateMemberStatus(@PathVariable Long id, @RequestParam Integer status) {
        ownerGuard.requireMember(id, SecurityUtils.currentUserId());
        memberService.updateStatus(id, status);
        return Result.success("操作成功", null);
    }

    // ==================== 成员批量操作 ====================

    @PutMapping("/members/batch/status")
    public Result<OrgMemberBatchResponse> batchMemberStatus(
            @RequestParam Integer status, @Valid @RequestBody OrgMemberIdsRequest request) {
        return Result.success("操作成功",
                memberService.batchUpdateStatus(request.getIds(), status, SecurityUtils.currentUserId()));
    }

    @PutMapping("/members/batch/organization")
    public Result<OrgMemberBatchResponse> batchMemberOrganization(
            @Valid @RequestBody OrgMemberBatchMoveRequest request) {
        Long ownerId = SecurityUtils.currentUserId();
        ownerGuard.requireOrg(request.getOrganizationId(), ownerId);
        return Result.success("移动完成",
                memberService.batchMoveOrganization(request.getIds(), request.getOrganizationId(), ownerId));
    }

    @PutMapping("/members/batch/position")
    public Result<OrgMemberBatchResponse> batchMemberPosition(
            @Valid @RequestBody OrgMemberBatchPositionRequest request) {
        return Result.success("保存成功",
                memberService.batchUpdatePosition(request.getIds(), request.getPosition(), SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/members/batch")
    public Result<OrgMemberBatchResponse> batchDeleteMembers(
            @Valid @RequestBody OrgMemberIdsRequest request) {
        return Result.success("删除成功",
                memberService.batchDelete(request.getIds(), SecurityUtils.currentUserId()));
    }

    @DeleteMapping("/organizations/{id}/members")
    public Result<OrgMemberBatchResponse> clearOrganizationMembers(@PathVariable Long id) {
        Long ownerId = SecurityUtils.currentUserId();
        ownerGuard.requireOrg(id, ownerId);
        return Result.success("已清空成员", memberService.clearOrganizationMembers(id, ownerId));
    }

    @PostMapping("/members/import/preview")
    public Result<OrgMemberImportPreviewResponse> previewMembers(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long organizationId) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success(excelImportService.previewMembers(file, organizationId));
    }

    @PostMapping("/members/import/apply")
    public Result<OrgMemberImportApplyResponse> applyMembers(@RequestBody OrgMemberImportApplyRequest request) {
        Long ownerId = SecurityUtils.currentUserId();
        ownerGuard.requireOrg(request.getOrganizationId(), ownerId);
        ownerGuard.requireMembersInOrg(request.getRestoreIds(), request.getOrganizationId(), ownerId);
        ownerGuard.requireMembersInOrg(request.getLeaveIds(), request.getOrganizationId(), ownerId);
        return Result.success("导入完成", excelImportService.applyMembers(request));
    }

    // ==================== 加入审批 ====================

    @GetMapping("/applications")
    public Result<List<OrgMembershipApplicationResponse>> applications(HttpServletRequest http) {
        return Result.success(membershipService.listApplications(getUserId(http)));
    }

    @GetMapping("/applications/count")
    public Result<Long> applicationCount(HttpServletRequest http) {
        return Result.success(membershipService.countApplications(getUserId(http)));
    }

    @PostMapping("/applications/{id}/approve")
    public Result<Void> approveApplication(@PathVariable Long id, HttpServletRequest http) {
        membershipService.approve(getUserId(http), id);
        return Result.success("已通过", null);
    }

    @PostMapping("/applications/{id}/reject")
    public Result<Void> rejectApplication(@PathVariable Long id, HttpServletRequest http) {
        membershipService.reject(getUserId(http), id);
        return Result.success("已拒绝", null);
    }

    // ==================== 积分换算比 ====================

    @GetMapping("/points-config")
    public Result<OrgPointsConfig> getPointsConfig(@RequestParam Long organizationId) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success(pointsConfigService.get(organizationId));
    }

    @PutMapping("/points-config")
    public Result<OrgPointsConfig> savePointsConfig(@RequestParam Long organizationId,
                                                    @RequestBody OrgPointsConfig config) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success("保存成功", pointsConfigService.save(organizationId, config));
    }

    // ==================== 奖励礼包 ====================

    @GetMapping("/packages")
    public Result<List<OrgRewardPackage>> packages(@RequestParam Long organizationId) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success(packageService.list(organizationId));
    }

    @PostMapping("/packages")
    public Result<OrgRewardPackage> createPackage(@RequestParam Long organizationId,
                                                  @Valid @RequestBody OrgRewardPackageRequest request) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success("添加成功", packageService.create(organizationId, request));
    }

    @PutMapping("/packages/{id}")
    public Result<OrgRewardPackage> updatePackage(@PathVariable Long id,
                                                  @Valid @RequestBody OrgRewardPackageRequest request) {
        ownerGuard.requirePackage(id, SecurityUtils.currentUserId());
        return Result.success("更新成功", packageService.update(id, request));
    }

    @DeleteMapping("/packages/{id}")
    public Result<Void> deletePackage(@PathVariable Long id) {
        ownerGuard.requirePackage(id, SecurityUtils.currentUserId());
        packageService.delete(id);
        return Result.success("删除成功", null);
    }

    // ==================== 一键发包方案 ====================

    @GetMapping("/distribution/plans")
    public Result<List<OrgDistributionPlanResponse>> distributionPlans(@RequestParam Long organizationId) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success(distributionService.list(organizationId));
    }

    @PostMapping("/distribution/plans")
    public Result<OrgDistributionPlanResponse> createDistributionPlan(
            @RequestParam Long organizationId,
            @Valid @RequestBody OrgDistributionPlanRequest request) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success("保存成功", distributionService.create(organizationId, request));
    }

    @PutMapping("/distribution/plans/{id}")
    public Result<OrgDistributionPlanResponse> updateDistributionPlan(
            @PathVariable Long id,
            @Valid @RequestBody OrgDistributionPlanRequest request) {
        ownerGuard.requirePlan(id, SecurityUtils.currentUserId());
        return Result.success("保存成功", distributionService.update(id, request));
    }

    @DeleteMapping("/distribution/plans/{id}")
    public Result<Void> deleteDistributionPlan(@PathVariable Long id) {
        ownerGuard.requirePlan(id, SecurityUtils.currentUserId());
        distributionService.delete(id);
        return Result.success("删除成功", null);
    }

    @PostMapping("/distribution/plans/{id}/run")
    public Result<OrgDistributionRunResponse> runDistribution(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        ownerGuard.requirePlan(id, SecurityUtils.currentUserId());
        return Result.success("发包完成", distributionService.run(id, weekDate));
    }

    // ==================== 周记录 ====================

    @GetMapping("/weeks")
    public Result<List<LocalDate>> weeks(@RequestParam Long organizationId) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success(weekRecordService.listWeeks(organizationId));
    }

    @GetMapping("/records")
    public Result<List<OrgWeekRecordResponse>> records(
            @RequestParam Long organizationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success(weekRecordService.listRecords(organizationId, weekDate));
    }

    @PostMapping("/records/generate")
    public Result<Integer> generate(
            @RequestParam Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success("生成成功", weekRecordService.generate(organizationId, weekDate));
    }

    @PostMapping("/records/calculate")
    public Result<Integer> calculate(
            @RequestParam Long organizationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success("计算完成", weekRecordService.calculate(organizationId, weekDate));
    }

    @PostMapping("/records/import")
    public Result<OrgImportResponse> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success("导入完成", excelImportService.importExcel(file, organizationId, weekDate));
    }

    @PostMapping("/records/renegade-leaders/import")
    public Result<OrgRenegadeLeaderImportResponse> importRenegadeLeaders(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long organizationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        return Result.success("导入完成",
                weekRecordService.importRenegadeLeaders(organizationId, weekDate, file));
    }

    @PutMapping("/records/{id}/package")
    public Result<OrgWeekRecordResponse> setPackage(@PathVariable Long id, @RequestParam Long packageId) {
        Long ownerId = SecurityUtils.currentUserId();
        ownerGuard.requireRecord(id, ownerId);
        ownerGuard.requirePackage(packageId, ownerId);
        return Result.success("设置成功", weekRecordService.setPackage(id, packageId));
    }

    @DeleteMapping("/records/{id}/package")
    public Result<Void> clearPackage(@PathVariable Long id) {
        ownerGuard.requireRecord(id, SecurityUtils.currentUserId());
        weekRecordService.clearPackage(id);
        return Result.success("已清除礼包", null);
    }

    @PutMapping("/records/{id}")
    public Result<Void> updateRecord(@PathVariable Long id,
                                     @RequestBody OrgWeekRecordUpdateRequest request) {
        ownerGuard.requireRecord(id, SecurityUtils.currentUserId());
        weekRecordService.updateRecord(id, request);
        return Result.success("保存成功", null);
    }

    @DeleteMapping("/records/{organizationId}/{weekDate}")
    public Result<Void> deleteWeek(
            @PathVariable Long organizationId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        ownerGuard.requireOrg(organizationId, SecurityUtils.currentUserId());
        weekRecordService.deleteWeek(organizationId, weekDate);
        return Result.success("删除成功", null);
    }

    private Long getUserId(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) {
            return null;
        }
        return jwtTokenProvider.getUserIdFromToken(auth.substring(7));
    }
}
