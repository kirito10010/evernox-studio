package com.evernox.controller;

import com.evernox.common.Result;
import com.evernox.dto.OrgImportResponse;
import com.evernox.dto.OrgMemberImportResponse;
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
import com.evernox.service.OrgExcelImportService;
import com.evernox.service.OrgMemberService;
import com.evernox.service.OrgMembershipService;
import com.evernox.service.OrgOrganizationService;
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
    private final OrgExcelImportService excelImportService;
    private final OrgMembershipService membershipService;
    private final JwtTokenProvider jwtTokenProvider;

    // ==================== 组织 ====================

    @GetMapping("/organizations")
    public Result<List<OrgOrganizationResponse>> organizations() {
        return Result.success(organizationService.list());
    }

    @PostMapping("/organizations")
    public Result<OrgOrganizationResponse> createOrganization(@Valid @RequestBody OrgOrganizationRequest request,
                                                              HttpServletRequest http) {
        return Result.success("添加成功", organizationService.create(request, getUserId(http)));
    }

    @PutMapping("/organizations/{id}")
    public Result<OrgOrganizationResponse> updateOrganization(@PathVariable Long id,
                                                              @Valid @RequestBody OrgOrganizationRequest request) {
        return Result.success("更新成功", organizationService.update(id, request));
    }

    @DeleteMapping("/organizations/{id}")
    public Result<Void> deleteOrganization(@PathVariable Long id) {
        organizationService.delete(id);
        return Result.success("删除成功", null);
    }

    // ==================== 成员 ====================

    @GetMapping("/members")
    public Result<List<OrgMemberResponse>> members() {
        return Result.success(memberService.list());
    }

    @PostMapping("/members")
    public Result<OrgMemberResponse> createMember(@Valid @RequestBody OrgMemberRequest request) {
        return Result.success("添加成功", memberService.create(request));
    }

    @PutMapping("/members/{id}")
    public Result<OrgMemberResponse> updateMember(@PathVariable Long id,
                                                  @Valid @RequestBody OrgMemberRequest request) {
        return Result.success("更新成功", memberService.update(id, request));
    }

    @PutMapping("/members/{id}/status")
    public Result<Void> updateMemberStatus(@PathVariable Long id, @RequestParam Integer status) {
        memberService.updateStatus(id, status);
        return Result.success("操作成功", null);
    }

    @PostMapping("/members/import")
    public Result<OrgMemberImportResponse> importMembers(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long organizationId) {
        return Result.success("导入完成", excelImportService.importMembers(file, organizationId));
    }

    // ==================== 加入审批 ====================

    @GetMapping("/applications")
    public Result<List<OrgMembershipApplicationResponse>> applications(HttpServletRequest http) {
        return Result.success(membershipService.listApplications(getUserId(http)));
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
        return Result.success(pointsConfigService.get(organizationId));
    }

    @PutMapping("/points-config")
    public Result<OrgPointsConfig> savePointsConfig(@RequestParam Long organizationId,
                                                    @RequestBody OrgPointsConfig config) {
        return Result.success("保存成功", pointsConfigService.save(organizationId, config));
    }

    // ==================== 奖励礼包 ====================

    @GetMapping("/packages")
    public Result<List<OrgRewardPackage>> packages(@RequestParam Long organizationId) {
        return Result.success(packageService.list(organizationId));
    }

    @PostMapping("/packages")
    public Result<OrgRewardPackage> createPackage(@RequestParam Long organizationId,
                                                  @Valid @RequestBody OrgRewardPackageRequest request) {
        return Result.success("添加成功", packageService.create(organizationId, request));
    }

    @PutMapping("/packages/{id}")
    public Result<OrgRewardPackage> updatePackage(@PathVariable Long id,
                                                  @Valid @RequestBody OrgRewardPackageRequest request) {
        return Result.success("更新成功", packageService.update(id, request));
    }

    @DeleteMapping("/packages/{id}")
    public Result<Void> deletePackage(@PathVariable Long id) {
        packageService.delete(id);
        return Result.success("删除成功", null);
    }

    // ==================== 周记录 ====================

    @GetMapping("/weeks")
    public Result<List<LocalDate>> weeks(@RequestParam Long organizationId) {
        return Result.success(weekRecordService.listWeeks(organizationId));
    }

    @GetMapping("/records")
    public Result<List<OrgWeekRecordResponse>> records(
            @RequestParam Long organizationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        return Result.success(weekRecordService.listRecords(organizationId, weekDate));
    }

    @PostMapping("/records/generate")
    public Result<Integer> generate(
            @RequestParam Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        return Result.success("生成成功", weekRecordService.generate(organizationId, weekDate));
    }

    @PostMapping("/records/calculate")
    public Result<Integer> calculate(
            @RequestParam Long organizationId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        return Result.success("计算完成", weekRecordService.calculate(organizationId, weekDate));
    }

    @PostMapping("/records/import")
    public Result<OrgImportResponse> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long organizationId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
        return Result.success("导入完成", excelImportService.importExcel(file, organizationId, weekDate));
    }

    @PutMapping("/records/{id}/package")
    public Result<OrgWeekRecordResponse> setPackage(@PathVariable Long id, @RequestParam Long packageId) {
        return Result.success("设置成功", weekRecordService.setPackage(id, packageId));
    }

    @DeleteMapping("/records/{id}/package")
    public Result<Void> clearPackage(@PathVariable Long id) {
        weekRecordService.clearPackage(id);
        return Result.success("已清除礼包", null);
    }

    @PutMapping("/records/{id}")
    public Result<Void> updateRecord(@PathVariable Long id,
                                     @RequestBody OrgWeekRecordUpdateRequest request) {
        weekRecordService.updateRecord(id, request);
        return Result.success("保存成功", null);
    }

    @DeleteMapping("/records/{organizationId}/{weekDate}")
    public Result<Void> deleteWeek(
            @PathVariable Long organizationId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekDate) {
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
