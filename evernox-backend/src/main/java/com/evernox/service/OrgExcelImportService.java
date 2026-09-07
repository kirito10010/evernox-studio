package com.evernox.service;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.OrgImportResponse;
import com.evernox.dto.OrgMemberImportApplyRequest;
import com.evernox.dto.OrgMemberImportApplyResponse;
import com.evernox.dto.OrgMemberImportCandidate;
import com.evernox.dto.OrgMemberImportPreviewResponse;
import com.evernox.dto.OrgMemberResponse;
import com.evernox.dto.OrgMemberImportUpdateCandidate;
import com.evernox.entity.OrgMember;
import com.evernox.entity.OrgWeekRecord;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgMemberRepository;
import com.evernox.repository.OrgOrganizationRepository;
import com.evernox.repository.OrgWeekRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织积分 Excel 导入服务
 *
 * 按表头名称自动识别列，玩家名称为匹配键，其余识别为活动字段并填充。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgExcelImportService {

    private final OrgWeekRecordRepository recordRepository;
    private final OrgMemberRepository memberRepository;
    private final OrgOrganizationRepository organizationRepository;

    /** 表头字段类型 */
    private enum Field {
        NAME, POSITION, NINJA_BATTLE, TOTAL_POWER, COPPER, BEAST, RENEGADE, RENEGADE_LEADER
    }

    private static final Map<String, Field> HEADER_MAP = new HashMap<>();
    static {
        // 玩家名（成员导入 / 周记录导入共用）
        HEADER_MAP.put("成员", Field.NAME);
        HEADER_MAP.put("角色名字", Field.NAME);
        HEADER_MAP.put("名称", Field.NAME);
        HEADER_MAP.put("玩家名", Field.NAME);
        // 职务（成员导入）
        HEADER_MAP.put("职务", Field.POSITION);
        HEADER_MAP.put("职位", Field.POSITION);
        // 周记录活动字段
        HEADER_MAP.put("参战次数", Field.NINJA_BATTLE);
        HEADER_MAP.put("战斗力", Field.TOTAL_POWER);
        HEADER_MAP.put("捐献贡献", Field.COPPER);
        HEADER_MAP.put("献祭通灵查克拉", Field.BEAST);
        HEADER_MAP.put("缉拿叛忍数", Field.RENEGADE);
        HEADER_MAP.put("车头", Field.RENEGADE_LEADER);
    }

    @Transactional
    @SuppressWarnings("null")
    public OrgImportResponse importExcel(MultipartFile file, Long organizationId, LocalDate weekDate) {
        if (organizationId == null) {
            throw new BusinessException("请选择组织");
        }
        LocalDate target = weekDate != null ? weekDate : computeSunday(LocalDate.now());
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }

        List<Map<String, Object>> rows = parse(file);

        // 用第一行表头确定列映射
        Map<String, Field> headers = resolveHeaders(rows.isEmpty() ? Map.of() : rows.get(0));
        if (!headers.containsValue(Field.NAME)) {
            throw new BusinessException("未找到玩家名称列，请确保表头包含「玩家名称」");
        }

        // 本周记录按名字建索引
        Map<String, OrgWeekRecord> recordByName = new HashMap<>();
        for (OrgWeekRecord r : recordRepository.selectList(new LambdaQueryWrapper<OrgWeekRecord>()
                .eq(OrgWeekRecord::getOrganizationId, organizationId)
                .eq(OrgWeekRecord::getWeekDate, target))) {
            recordByName.put(r.getMemberName(), r);
        }

        List<String> importedNames = new ArrayList<>();
        List<String> unmatchedNames = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String name = null;
            for (Map.Entry<String, Field> e : headers.entrySet()) {
                if (e.getValue() == Field.NAME) {
                    name = str(row.get(e.getKey()));
                    break;
                }
            }
            if (name == null || name.isBlank()) {
                continue;
            }
            OrgWeekRecord record = recordByName.get(name);
            if (record == null) {
                unmatchedNames.add(name);
                continue;
            }
            apply(record, row, headers);
            recordRepository.updateById(record);
            importedNames.add(name);
        }
        // 导入后统计该组织本周仍为空数据的成员
        List<String> emptyNames = recordRepository.selectList(new LambdaQueryWrapper<OrgWeekRecord>()
                .eq(OrgWeekRecord::getOrganizationId, organizationId)
                .eq(OrgWeekRecord::getWeekDate, target)).stream()
                .filter(OrgExcelImportService::isEmptyRecord)
                .map(OrgWeekRecord::getMemberName)
                .toList();
        log.info("组织积分导入: weekDate={}, imported={}, unmatched={}, empty={}",
                target, importedNames.size(), unmatchedNames.size(), emptyNames.size());
        return OrgImportResponse.builder()
                .importedNames(importedNames)
                .unmatchedNames(unmatchedNames)
                .emptyNames(emptyNames)
                .build();
    }

    @SuppressWarnings("null")
    public OrgMemberImportPreviewResponse previewMembers(MultipartFile file, Long organizationId) {
        if (organizationId == null) {
            throw new BusinessException("请选择组织");
        }
        if (organizationRepository.selectById(organizationId) == null) {
            throw new BusinessException("所属组织不存在");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要导入的 Excel 文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 格式的 Excel 文件");
        }

        List<Map<String, Object>> rows = parse(file);
        Map<String, Field> headers = resolveHeaders(rows.isEmpty() ? Map.of() : rows.get(0));
        String nameKey = null;
        String positionKey = null;
        for (Map.Entry<String, Field> e : headers.entrySet()) {
            if (e.getValue() == Field.NAME && nameKey == null) {
                nameKey = e.getKey();
            } else if (e.getValue() == Field.POSITION && positionKey == null) {
                positionKey = e.getKey();
            }
        }
        if (nameKey == null) {
            throw new BusinessException("未找到玩家名称列，请确保表头包含「玩家名称」");
        }

        // Excel 去重取第一次出现的名字/职务
        LinkedHashMap<String, String> excelNames = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String name = str(row.get(nameKey));
            if (name.isBlank() || name.length() > 50) {
                continue;
            }
            String position = positionKey == null ? null : str(row.get(positionKey));
            if (position != null && position.isBlank()) {
                position = null;
            }
            if (position != null && position.length() > 50) {
                continue;
            }
            excelNames.putIfAbsent(name, position);
        }

        List<OrgMember> members = memberRepository.selectList(
                new LambdaQueryWrapper<OrgMember>().eq(OrgMember::getOrganizationId, organizationId));
        Map<String, OrgMember> memberByName = members.stream()
                .collect(Collectors.toMap(OrgMember::getName, m -> m, (a, b) -> a));

        List<OrgMemberImportCandidate> toAdd = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        List<OrgMemberResponse> toRestore = new ArrayList<>();
        List<OrgMemberImportUpdateCandidate> toUpdate = new ArrayList<>();
        for (Map.Entry<String, String> e : excelNames.entrySet()) {
            String name = e.getKey();
            String excelPos = e.getValue();
            OrgMember member = memberByName.get(name);
            if (member == null) {
                toAdd.add(OrgMemberImportCandidate.builder().name(name).position(excelPos).build());
                continue;
            }
            if (member.getStatus() != null && member.getStatus() == 0) {
                // 已离开成员再次出现 → 恢复候选（仅恢复状态，职务不变）
                toRestore.add(OrgMemberResponse.from(member));
                continue;
            }
            if (excelPos == null) {
                // Excel 未提供职务：不触发替换，视为无变动
                unchanged.add(name);
                continue;
            }
            String oldPos = normalize(member.getPosition());
            if (excelPos.equals(oldPos)) {
                unchanged.add(name);
            } else {
                toUpdate.add(OrgMemberImportUpdateCandidate.builder()
                        .memberId(member.getId())
                        .name(name)
                        .oldPosition(member.getPosition())
                        .newPosition(excelPos)
                        .build());
            }
        }

        List<OrgMemberResponse> toLeave = members.stream()
                .filter(m -> m.getStatus() != null && m.getStatus() == 1)
                .filter(m -> !excelNames.containsKey(m.getName()))
                .map(OrgMemberResponse::from)
                .toList();

        log.info("组织成员导入预览: organizationId={}, toAdd={}, unchanged={}, toRestore={}, toUpdate={}, toLeave={}",
                organizationId, toAdd.size(), unchanged.size(), toRestore.size(), toUpdate.size(), toLeave.size());
        return OrgMemberImportPreviewResponse.builder()
                .toAdd(toAdd)
                .unchangedNames(unchanged)
                .toRestore(toRestore)
                .toUpdate(toUpdate)
                .toLeave(toLeave)
                .build();
    }

    @Transactional
    @SuppressWarnings("null")
    public OrgMemberImportApplyResponse applyMembers(OrgMemberImportApplyRequest request) {
        Long organizationId = request.getOrganizationId();
        if (organizationId == null) {
            throw new BusinessException("请选择组织");
        }
        if (organizationRepository.selectById(organizationId) == null) {
            throw new BusinessException("所属组织不存在");
        }

        Set<String> existing = memberRepository.selectList(
                        new LambdaQueryWrapper<OrgMember>().eq(OrgMember::getOrganizationId, organizationId))
                .stream().map(OrgMember::getName).collect(Collectors.toSet());

        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        if (request.getAdd() != null) {
            for (OrgMemberImportCandidate c : request.getAdd()) {
                String name = c.getName() == null ? "" : c.getName().trim();
                String position = c.getPosition();
                if (name.isBlank() || name.length() > 50 || existing.contains(name)) {
                    skipped.add(name);
                    continue;
                }
                memberRepository.insert(OrgMember.builder()
                        .organizationId(organizationId)
                        .name(name)
                        .position(position)
                        .status(1)
                        .build());
                existing.add(name);
                added.add(name);
            }
        }

        List<String> updated = new ArrayList<>();
        if (request.getUpdates() != null) {
            for (OrgMemberImportUpdateCandidate u : request.getUpdates()) {
                if (u.getMemberId() == null) {
                    continue;
                }
                OrgMember m = memberRepository.selectById(u.getMemberId());
                if (m == null || !organizationId.equals(m.getOrganizationId())) {
                    continue;
                }
                m.setPosition(u.getNewPosition());
                memberRepository.updateById(m);
                updated.add(m.getName());
            }
        }

        List<String> restored = new ArrayList<>();
        if (request.getRestoreIds() != null) {
            for (Long id : request.getRestoreIds()) {
                OrgMember m = memberRepository.selectById(id);
                if (m == null || !organizationId.equals(m.getOrganizationId())) {
                    continue;
                }
                m.setStatus(1);
                memberRepository.updateById(m);
                restored.add(m.getName());
            }
        }

        List<String> left = new ArrayList<>();
        if (request.getLeaveIds() != null) {
            for (Long id : request.getLeaveIds()) {
                OrgMember m = memberRepository.selectById(id);
                if (m == null || !organizationId.equals(m.getOrganizationId())) {
                    continue;
                }
                m.setStatus(0);
                memberRepository.updateById(m);
                left.add(m.getName());
            }
        }

        log.info("组织成员导入应用: organizationId={}, added={}, updated={}, restored={}, left={}, skipped={}",
                organizationId, added.size(), updated.size(), restored.size(), left.size(), skipped.size());
        return OrgMemberImportApplyResponse.builder()
                .addedNames(added)
                .updatedNames(updated)
                .restoredNames(restored)
                .leftNames(left)
                .skippedNames(skipped)
                .build();
    }

    /** 六个活动字段全空视为「空数据」 */
    private static boolean isEmptyRecord(OrgWeekRecord r) {
        return r.getNinjaBattleCount() == null
                && r.getTotalPower() == null
                && r.getPowerIncrease() == null
                && r.getCopperContribution() == null
                && r.getBeastSacrifice() == null
                && r.getRenegadeCount() == null;
    }

    private void apply(OrgWeekRecord record, Map<String, Object> row, Map<String, Field> headers) {
        for (Map.Entry<String, Field> e : headers.entrySet()) {
            Object v = row.get(e.getKey());
            switch (e.getValue()) {
                case NINJA_BATTLE -> record.setNinjaBattleCount(toInt(v));
                case TOTAL_POWER -> record.setTotalPower(toInt(v));
                case COPPER -> record.setCopperContribution(toInt(v));
                case BEAST -> record.setBeastSacrifice(toInt(v));
                case RENEGADE -> record.setRenegadeCount(toInt(v));
                case RENEGADE_LEADER -> record.setIsRenegadeLeader(toBool(v));
                default -> { }
            }
        }
    }

    private Map<String, Field> resolveHeaders(Map<String, Object> firstRow) {
        Map<String, Field> result = new HashMap<>();
        for (String header : firstRow.keySet()) {
            String normalized = header == null ? "" : header.replaceAll("\\s+", "");
            Field field = HEADER_MAP.get(normalized);
            if (field != null) {
                result.put(header, field);
            }
        }
        return result;
    }

    private List<Map<String, Object>> parse(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            ExcelReader reader = ExcelUtil.getReader(in);
            return reader.readAll();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excel 解析失败: {}", e.getMessage(), e);
            throw new BusinessException("Excel 格式不正确，请使用第一行为表头的 .xlsx 文件");
        }
    }

    private String str(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof Number n) {
            return n.doubleValue() == Math.floor(n.doubleValue()) && !Double.isInfinite(n.doubleValue())
                    ? String.valueOf(n.longValue())
                    : String.valueOf(n.doubleValue());
        }
        return String.valueOf(o).trim();
    }

    private Integer toInt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer toBool(Object v) {
        if (v == null) {
            return null;
        }
        String s = v.toString().trim();
        if (s.isEmpty()) {
            return null;
        }
        return ("1".equals(s) || "是".equals(s) || "true".equalsIgnoreCase(s)
                || "√".equals(s) || "车头".equals(s)) ? 1 : 0;
    }

    private LocalDate computeSunday(LocalDate date) {
        return date.plusDays(7 - date.getDayOfWeek().getValue());
    }

    private String normalize(String s) {
        return s == null ? null : s.trim().isEmpty() ? null : s.trim();
    }
}
