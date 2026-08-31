package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.evernox.dto.OrgWeekRecordResponse;
import com.evernox.dto.OrgWeekRecordUpdateRequest;
import com.evernox.entity.OrgMember;
import com.evernox.entity.OrgOrganization;
import com.evernox.entity.OrgPointsConfig;
import com.evernox.entity.OrgRewardPackage;
import com.evernox.entity.OrgWeekRecord;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgMemberRepository;
import com.evernox.repository.OrgOrganizationRepository;
import com.evernox.repository.OrgRewardPackageRepository;
import com.evernox.repository.OrgWeekRecordRepository;
import com.evernox.service.OrgPointsConfigService;
import com.evernox.service.OrgWeekRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * 组织每周成员记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgWeekRecordServiceImpl implements OrgWeekRecordService {

    private static final int SCALE = 5;

    private final OrgMemberRepository memberRepository;
    private final OrgOrganizationRepository organizationRepository;
    private final OrgWeekRecordRepository recordRepository;
    private final OrgRewardPackageRepository packageRepository;
    private final OrgPointsConfigService pointsConfigService;

    @Override
    @SuppressWarnings("null")
    public List<LocalDate> listWeeks(Long organizationId) {
        List<OrgWeekRecord> records = recordRepository.selectList(
                new QueryWrapper<OrgWeekRecord>()
                        .select("DISTINCT week_date")
                        .eq("organization_id", organizationId)
                        .orderByDesc("week_date"));
        return records.stream()
                .map(OrgWeekRecord::getWeekDate)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    @SuppressWarnings("null")
    public List<OrgWeekRecordResponse> listRecords(Long organizationId, LocalDate weekDate) {
        if (weekDate == null) {
            return List.of();
        }
        List<OrgWeekRecord> records = recordRepository.selectList(new LambdaQueryWrapper<OrgWeekRecord>()
                .eq(OrgWeekRecord::getOrganizationId, organizationId)
                .eq(OrgWeekRecord::getWeekDate, weekDate)
                .orderByAsc(OrgWeekRecord::getId));
        return records.stream().map(OrgWeekRecordResponse::from).toList();
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public int generate(Long organizationId, LocalDate weekDate) {
        if (organizationId == null) {
            throw new BusinessException("请选择组织");
        }
        OrgOrganization org = organizationRepository.selectById(organizationId);
        if (org == null) {
            throw new BusinessException("组织不存在");
        }
        LocalDate target = weekDate != null ? weekDate : computeSunday(LocalDate.now());
        OrgPointsConfig config = pointsConfigService.get(organizationId);
        List<OrgMember> members = memberRepository.selectList(new LambdaQueryWrapper<OrgMember>()
                .eq(OrgMember::getOrganizationId, organizationId)
                .eq(OrgMember::getStatus, 1)
                .orderByAsc(OrgMember::getId));
        int created = 0;
        for (OrgMember member : members) {
            Long exists = recordRepository.selectCount(new LambdaQueryWrapper<OrgWeekRecord>()
                    .eq(OrgWeekRecord::getWeekDate, target)
                    .eq(OrgWeekRecord::getMemberId, member.getId()));
            if (exists != null && exists > 0) {
                continue;
            }
            OrgWeekRecord record = OrgWeekRecord.builder()
                    .organizationId(organizationId)
                    .organizationName(org.getName())
                    .weekDate(target)
                    .memberId(member.getId())
                    .memberName(member.getName())
                    .position(member.getPosition())
                    .isRenegadeLeader(0)
                    .lastWeekPoints(carryOver(member.getId(), target, config))
                    .build();
            recordRepository.insert(record);
            created++;
        }
        log.info("一键生成周记录: organizationId={}, weekDate={}, created={}", organizationId, target, created);
        return created;
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public int calculate(Long organizationId, LocalDate weekDate) {
        if (weekDate == null) {
            throw new BusinessException("缺少周日期");
        }
        OrgPointsConfig config = pointsConfigService.get(organizationId);
        List<OrgWeekRecord> records = recordRepository.selectList(new LambdaQueryWrapper<OrgWeekRecord>()
                .eq(OrgWeekRecord::getOrganizationId, organizationId)
                .eq(OrgWeekRecord::getWeekDate, weekDate));
        int updated = 0;
        for (OrgWeekRecord record : records) {
            applyCalculation(record, config);
            recordRepository.updateById(record);
            updated++;
        }
        log.info("计算周积分: organizationId={}, weekDate={}, updated={}", organizationId, weekDate, updated);
        return updated;
    }

    @Override
    @Transactional
    public void updateRecord(Long id, OrgWeekRecordUpdateRequest request) {
        OrgWeekRecord record = recordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }
        record.setNinjaBattleCount(request.getNinjaBattleCount());
        record.setTotalPower(request.getTotalPower());
        record.setPowerIncrease(request.getPowerIncrease());
        record.setCopperContribution(request.getCopperContribution());
        record.setBeastSacrifice(request.getBeastSacrifice());
        record.setRenegadeCount(request.getRenegadeCount());
        record.setIsRenegadeLeader(request.getIsRenegadeLeader() != null
                && request.getIsRenegadeLeader() == 1 ? 1 : 0);
        recordRepository.updateById(record);
        log.info("手动编辑周记录: id={}", id);
    }

    @Override
    @Transactional
    public OrgWeekRecordResponse setPackage(Long id, Long packageId) {
        OrgWeekRecord record = recordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }
        OrgRewardPackage pkg = packageRepository.selectById(packageId);
        if (pkg == null) {
            throw new BusinessException("礼包不存在");
        }
        record.setRewardPackageId(pkg.getId());
        record.setRewardPackageName(pkg.getName());
        record.setDeductionRatio(pkg.getDeductionRatio());
        if (record.getTotalPoints() == null) {
            applyCalculation(record, pointsConfigService.get(record.getOrganizationId()));
        }
        applyAfterDeduction(record);
        recordRepository.updateById(record);
        log.info("设置礼包并重算扣除后积分: recordId={}, package={}", id, pkg.getName());
        return OrgWeekRecordResponse.from(record);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public void clearPackage(Long id) {
        OrgWeekRecord record = recordRepository.selectById(id);
        if (record == null) {
            throw new BusinessException("记录不存在");
        }
        // updateById 默认忽略 null 字段，需用显式 set(null) 才能真正清空列
        recordRepository.update(null, new LambdaUpdateWrapper<OrgWeekRecord>()
                .eq(OrgWeekRecord::getId, id)
                .set(OrgWeekRecord::getRewardPackageId, null)
                .set(OrgWeekRecord::getRewardPackageName, null)
                .set(OrgWeekRecord::getDeductionRatio, null)
                .set(OrgWeekRecord::getPointsAfterDeduction, null));
        log.info("清除周记录礼包: recordId={}", id);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public void deleteWeek(Long organizationId, LocalDate weekDate) {
        if (weekDate == null) {
            return;
        }
        int deleted = recordRepository.delete(new LambdaQueryWrapper<OrgWeekRecord>()
                .eq(OrgWeekRecord::getOrganizationId, organizationId)
                .eq(OrgWeekRecord::getWeekDate, weekDate));
        log.info("删除整周批次: organizationId={}, weekDate={}, deleted={}", organizationId, weekDate, deleted);
    }

    // ==================== 私有方法 ====================

    @SuppressWarnings("null")
    private BigDecimal carryOver(Long memberId, LocalDate weekDate, OrgPointsConfig config) {
        OrgWeekRecord prev = recordRepository.selectOne(new LambdaQueryWrapper<OrgWeekRecord>()
                .eq(OrgWeekRecord::getMemberId, memberId)
                .lt(OrgWeekRecord::getWeekDate, weekDate)
                .orderByDesc(OrgWeekRecord::getWeekDate)
                .last("LIMIT 1"));
        if (prev == null) {
            return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal base;
        if (prev.getRewardPackageId() != null) {
            // 已领礼包：按扣除后总积分继承
            base = prev.getPointsAfterDeduction() == null ? BigDecimal.ZERO : prev.getPointsAfterDeduction();
        } else {
            // 未领礼包：继承总积分（未扣除）并额外加减调整值
            BigDecimal total = prev.getTotalPoints() == null ? BigDecimal.ZERO : prev.getTotalPoints();
            BigDecimal adjustment = config == null || config.getNoPackageAdjustment() == null
                    ? BigDecimal.ZERO : config.getNoPackageAdjustment();
            base = total.add(adjustment);
        }
        return base.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private void applyCalculation(OrgWeekRecord record, OrgPointsConfig config) {
        BigDecimal sum = BigDecimal.ZERO;
        sum = sum.add(term(record.getNinjaBattleCount(), config.getNinjaBattlePoints(), config.getNinjaBattleEnabled()));
        sum = sum.add(term(record.getTotalPower(), config.getTotalPowerPoints(), config.getTotalPowerEnabled()));
        sum = sum.add(term(record.getPowerIncrease(), config.getPowerIncreasePoints(), config.getPowerIncreaseEnabled()));
        sum = sum.add(term(record.getCopperContribution(), config.getCopperPoints(), config.getCopperEnabled()));
        sum = sum.add(term(record.getBeastSacrifice(), config.getBeastPoints(), config.getBeastEnabled()));
        sum = sum.add(term(record.getRenegadeCount(), config.getRenegadePoints(), config.getRenegadeEnabled()));
        if (Integer.valueOf(1).equals(config.getRenegadeLeaderEnabled())
                && Integer.valueOf(1).equals(record.getIsRenegadeLeader())) {
            sum = sum.add(config.getRenegadeLeaderBonus() == null ? BigDecimal.ZERO : config.getRenegadeLeaderBonus());
        }
        BigDecimal thisWeek = sum.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal lastWeek = record.getLastWeekPoints() == null
                ? BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)
                : record.getLastWeekPoints().setScale(SCALE, RoundingMode.HALF_UP);
        record.setThisWeekPoints(thisWeek);
        record.setTotalPoints(lastWeek.add(thisWeek).setScale(SCALE, RoundingMode.HALF_UP));
    }

    private void applyAfterDeduction(OrgWeekRecord record) {
        if (record.getTotalPoints() == null) {
            record.setPointsAfterDeduction(null);
            return;
        }
        BigDecimal ratio = record.getDeductionRatio() == null ? BigDecimal.ZERO : record.getDeductionRatio();
        BigDecimal after = record.getTotalPoints()
                .multiply(BigDecimal.ONE.subtract(ratio))
                .setScale(SCALE, RoundingMode.HALF_UP);
        record.setPointsAfterDeduction(after);
    }

    private BigDecimal term(Integer count, BigDecimal points, Integer enabled) {
        if (!Integer.valueOf(1).equals(enabled) || count == null || count == 0) {
            return BigDecimal.ZERO;
        }
        if (points == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(count).multiply(points);
    }

    private LocalDate computeSunday(LocalDate date) {
        return date.plusDays(7 - date.getDayOfWeek().getValue());
    }
}
