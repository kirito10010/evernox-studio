package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.evernox.dto.OrgRenegadeLeaderImportResponse;
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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织每周成员记录服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgWeekRecordServiceImpl implements OrgWeekRecordService {

    private static final int SCALE = 5;
    /** 车头名单 txt 大小上限 */
    private static final long MAX_RENEGADE_FILE_SIZE = 1024 * 1024;

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

        // 先算战力增幅：上周(weekDate-7天)同成员总战力差值
        LocalDate prevWeek = weekDate.minusWeeks(1);
        Map<Long, OrgWeekRecord> prevByMember = recordRepository.selectList(
                        new LambdaQueryWrapper<OrgWeekRecord>()
                                .eq(OrgWeekRecord::getOrganizationId, organizationId)
                                .eq(OrgWeekRecord::getWeekDate, prevWeek))
                .stream()
                .filter(r -> r.getMemberId() != null)
                .collect(Collectors.toMap(OrgWeekRecord::getMemberId, r -> r, (a, b) -> a));

        int updated = 0;
        for (OrgWeekRecord record : records) {
            OrgWeekRecord prev = record.getMemberId() == null ? null : prevByMember.get(record.getMemberId());
            if (prev != null && record.getTotalPower() != null && prev.getTotalPower() != null) {
                record.setPowerIncrease(record.getTotalPower() - prev.getTotalPower());
            } else {
                record.setPowerIncrease(null);
            }
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

    @Override
    @Transactional
    @SuppressWarnings("null")
    public OrgRenegadeLeaderImportResponse importRenegadeLeaders(Long organizationId, LocalDate weekDate,
                                                                 MultipartFile file) {
        if (organizationId == null || weekDate == null) {
            throw new BusinessException("请先选择组织和周");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的 txt 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename != null && !filename.toLowerCase().endsWith(".txt")) {
            throw new BusinessException("仅支持 .txt 文件");
        }
        if (file.getSize() > MAX_RENEGADE_FILE_SIZE) {
            throw new BusinessException("文件过大，请控制在 1MB 以内");
        }

        List<OrgWeekRecord> records = recordRepository.selectList(new LambdaQueryWrapper<OrgWeekRecord>()
                .eq(OrgWeekRecord::getOrganizationId, organizationId)
                .eq(OrgWeekRecord::getWeekDate, weekDate));
        if (records.isEmpty()) {
            throw new BusinessException("该周暂无记录，请先生成或导入数据");
        }

        List<NamedLine> lines = readRenegadeLines(file);
        if (lines.isEmpty()) {
            throw new BusinessException("文件内容为空");
        }

        Map<String, List<OrgWeekRecord>> byName = records.stream()
                .filter(r -> r.getMemberName() != null)
                .collect(Collectors.groupingBy(OrgWeekRecord::getMemberName));

        Set<Long> matchedIds = new HashSet<>();
        List<OrgRenegadeLeaderImportResponse.UnmatchedItem> unmatched = new ArrayList<>();
        for (NamedLine line : lines) {
            List<OrgWeekRecord> hits = byName.get(line.name());
            if (hits == null) {
                unmatched.add(OrgRenegadeLeaderImportResponse.UnmatchedItem.builder()
                        .line(line.lineNo())
                        .name(line.name())
                        .build());
            } else {
                hits.forEach(r -> matchedIds.add(r.getId()));
            }
        }

        boolean allMatched = unmatched.isEmpty();
        int imported = 0;
        if (allMatched) {
            // 全量覆盖本周车头标记：名单内为车头，名单外恢复为否
            recordRepository.update(null, new LambdaUpdateWrapper<OrgWeekRecord>()
                    .eq(OrgWeekRecord::getOrganizationId, organizationId)
                    .eq(OrgWeekRecord::getWeekDate, weekDate)
                    .in(OrgWeekRecord::getId, matchedIds)
                    .set(OrgWeekRecord::getIsRenegadeLeader, 1));
            recordRepository.update(null, new LambdaUpdateWrapper<OrgWeekRecord>()
                    .eq(OrgWeekRecord::getOrganizationId, organizationId)
                    .eq(OrgWeekRecord::getWeekDate, weekDate)
                    .notIn(OrgWeekRecord::getId, matchedIds)
                    .set(OrgWeekRecord::getIsRenegadeLeader, 0));
            imported = matchedIds.size();
            log.info("导入叛忍车头成功: organizationId={}, weekDate={}, count={}",
                    organizationId, weekDate, imported);
        } else {
            log.info("导入叛忍车头失败: organizationId={}, weekDate={}, matched={}, unmatched={}",
                    organizationId, weekDate, lines.size() - unmatched.size(), unmatched.size());
        }

        return OrgRenegadeLeaderImportResponse.builder()
                .allMatched(allMatched)
                .totalLines(lines.size())
                .matchedCount(lines.size() - unmatched.size())
                .unmatchedCount(unmatched.size())
                .importedCount(imported)
                .unmatchedItems(unmatched)
                .build();
    }

    // ==================== 私有方法 ====================

    /**
     * 读取 txt：优先按 BOM 判断编码，无 BOM 时先严格按 UTF-8 解码，失败再按 GBK（Windows 记事本 ANSI）。
     * 忽略空行，保留文件中的真实行号。
     */
    private List<NamedLine> readRenegadeLines(MultipartFile file) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException("读取文件失败");
        }
        String[] raw = decodeText(bytes).split("\\r\\n|\\n|\\r", -1);
        List<NamedLine> lines = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            String name = raw[i].strip();
            if (!name.isEmpty()) {
                lines.add(new NamedLine(i + 1, name));
            }
        }
        return lines;
    }

    private String decodeText(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, Charset.forName("GBK"));
        }
    }

    private record NamedLine(int lineNo, String name) {
    }

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
