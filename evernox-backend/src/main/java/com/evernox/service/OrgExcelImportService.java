package com.evernox.service;

import cn.hutool.poi.excel.ExcelReader;
import cn.hutool.poi.excel.ExcelUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.OrgImportResponse;
import com.evernox.dto.OrgMemberImportResponse;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
        NAME, POSITION, NINJA_BATTLE, TOTAL_POWER, POWER_INCREASE, COPPER, BEAST, RENEGADE, RENEGADE_LEADER
    }

    private static final Map<String, Field> HEADER_MAP = new HashMap<>();
    static {
        HEADER_MAP.put("玩家名称", Field.NAME);
        HEADER_MAP.put("玩家名", Field.NAME);
        HEADER_MAP.put("成员", Field.NAME);
        HEADER_MAP.put("名字", Field.NAME);
        HEADER_MAP.put("玩家", Field.NAME);
        HEADER_MAP.put("职务", Field.POSITION);
        HEADER_MAP.put("职位", Field.POSITION);
        HEADER_MAP.put("忍战次数", Field.NINJA_BATTLE);
        HEADER_MAP.put("忍战活动次数", Field.NINJA_BATTLE);
        HEADER_MAP.put("忍战", Field.NINJA_BATTLE);
        HEADER_MAP.put("总战力", Field.TOTAL_POWER);
        HEADER_MAP.put("战力", Field.TOTAL_POWER);
        HEADER_MAP.put("战力增幅", Field.POWER_INCREASE);
        HEADER_MAP.put("战力增长", Field.POWER_INCREASE);
        HEADER_MAP.put("战力增加", Field.POWER_INCREASE);
        HEADER_MAP.put("铜币贡献", Field.COPPER);
        HEADER_MAP.put("铜币捐献", Field.COPPER);
        HEADER_MAP.put("铜币", Field.COPPER);
        HEADER_MAP.put("通灵兽献祭", Field.BEAST);
        HEADER_MAP.put("通灵兽", Field.BEAST);
        HEADER_MAP.put("通灵", Field.BEAST);
        HEADER_MAP.put("叛忍次数", Field.RENEGADE);
        HEADER_MAP.put("叛忍", Field.RENEGADE);
        HEADER_MAP.put("叛忍车头", Field.RENEGADE_LEADER);
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

    @Transactional
    @SuppressWarnings("null")
    public OrgMemberImportResponse importMembers(MultipartFile file, Long organizationId) {
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

        List<String> importedNames = new ArrayList<>();
        List<String> skippedNames = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            String name = str(row.get(nameKey));
            if (name.isEmpty() || seen.contains(name)) {
                continue;
            }
            seen.add(name);
            String position = positionKey == null ? null : str(row.get(positionKey));
            if (position != null && position.isEmpty()) {
                position = null;
            }
            if (name.length() > 50 || (position != null && position.length() > 50)
                    || memberRepository.selectCount(new LambdaQueryWrapper<OrgMember>()
                            .eq(OrgMember::getName, name)) > 0) {
                skippedNames.add(name);
                continue;
            }
            OrgMember member = OrgMember.builder()
                    .organizationId(organizationId)
                    .name(name)
                    .position(position)
                    .status(1)
                    .build();
            memberRepository.insert(member);
            importedNames.add(name);
        }
        log.info("组织成员导入: organizationId={}, imported={}, skipped={}",
                organizationId, importedNames.size(), skippedNames.size());
        return OrgMemberImportResponse.builder()
                .importedNames(importedNames)
                .skippedNames(skippedNames)
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
                case POWER_INCREASE -> record.setPowerIncrease(toInt(v));
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
}
