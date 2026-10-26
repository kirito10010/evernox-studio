package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.OrgDistributionPlanRequest;
import com.evernox.dto.OrgDistributionPlanResponse;
import com.evernox.dto.OrgDistributionRunResponse;
import com.evernox.dto.OrgWeekRecordResponse;
import com.evernox.entity.OrgDistributionForcedPlayer;
import com.evernox.entity.OrgDistributionPlan;
import com.evernox.entity.OrgDistributionPlanItem;
import com.evernox.entity.OrgMember;
import com.evernox.entity.OrgRewardPackage;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgDistributionForcedPlayerRepository;
import com.evernox.repository.OrgDistributionPlanItemRepository;
import com.evernox.repository.OrgDistributionPlanRepository;
import com.evernox.repository.OrgMemberRepository;
import com.evernox.repository.OrgRewardPackageRepository;
import com.evernox.service.OrgDistributionService;
import com.evernox.service.OrgWeekRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组织一键发包方案服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class OrgDistributionServiceImpl implements OrgDistributionService {

    private static final Set<String> RANK_FIELDS = Set.of(
            "totalPoints", "thisWeekPoints", "powerIncrease", "totalPower",
            "copperContribution", "beastSacrifice", "renegadeCount");

    private final OrgDistributionPlanRepository planRepository;
    private final OrgDistributionPlanItemRepository itemRepository;
    private final OrgDistributionForcedPlayerRepository forcedRepository;
    private final OrgRewardPackageRepository packageRepository;
    private final OrgMemberRepository memberRepository;
    private final OrgWeekRecordService weekRecordService;

    // ==================== 方案 CRUD ====================

    @Override
    public List<OrgDistributionPlanResponse> list(Long organizationId) {
        List<OrgDistributionPlan> plans = planRepository.selectList(
                new LambdaQueryWrapper<OrgDistributionPlan>()
                        .eq(OrgDistributionPlan::getOrganizationId, organizationId)
                        .orderByAsc(OrgDistributionPlan::getId));
        if (plans.isEmpty()) {
            return List.of();
        }
        List<Long> planIds = plans.stream().map(OrgDistributionPlan::getId).toList();
        return buildResponses(plans, planIds);
    }

    @Override
    @Transactional
    public OrgDistributionPlanResponse create(Long organizationId, OrgDistributionPlanRequest request) {
        validate(organizationId, request, null);
        OrgDistributionPlan plan = OrgDistributionPlan.builder()
                .organizationId(organizationId)
                .name(request.getName().trim())
                .rankField(normalizeRankField(request.getRankField()))
                .build();
        planRepository.insert(plan);
        replaceItems(plan.getId(), request.getItems());
        replaceForced(plan.getId(), request.getForced());
        log.info("新增一键发包方案: id={}, organizationId={}, name={}", plan.getId(), organizationId, plan.getName());
        return detail(plan.getId());
    }

    @Override
    @Transactional
    public OrgDistributionPlanResponse update(Long id, OrgDistributionPlanRequest request) {
        OrgDistributionPlan plan = requirePlan(id);
        validate(plan.getOrganizationId(), request, id);
        plan.setName(request.getName().trim());
        plan.setRankField(normalizeRankField(request.getRankField()));
        planRepository.updateById(plan);
        itemRepository.delete(new LambdaQueryWrapper<OrgDistributionPlanItem>()
                .eq(OrgDistributionPlanItem::getPlanId, id));
        forcedRepository.delete(new LambdaQueryWrapper<OrgDistributionForcedPlayer>()
                .eq(OrgDistributionForcedPlayer::getPlanId, id));
        replaceItems(id, request.getItems());
        replaceForced(id, request.getForced());
        log.info("更新一键发包方案: id={}, name={}", id, plan.getName());
        return detail(id);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        OrgDistributionPlan plan = requirePlan(id);
        itemRepository.delete(new LambdaQueryWrapper<OrgDistributionPlanItem>()
                .eq(OrgDistributionPlanItem::getPlanId, id));
        forcedRepository.delete(new LambdaQueryWrapper<OrgDistributionForcedPlayer>()
                .eq(OrgDistributionForcedPlayer::getPlanId, id));
        planRepository.deleteById(id);
        log.info("删除一键发包方案: id={}, name={}", id, plan.getName());
    }

    // ==================== 执行发包 ====================

    @Override
    @Transactional
    public OrgDistributionRunResponse run(Long id, LocalDate weekDate) {
        if (weekDate == null) {
            throw new BusinessException("请选择要发包的周");
        }
        OrgDistributionPlan plan = requirePlan(id);
        List<OrgDistributionPlanItem> items = itemRepository.selectList(
                new LambdaQueryWrapper<OrgDistributionPlanItem>()
                        .eq(OrgDistributionPlanItem::getPlanId, id)
                        .orderByAsc(OrgDistributionPlanItem::getSortOrder)
                        .orderByAsc(OrgDistributionPlanItem::getId));
        if (items.isEmpty()) {
            throw new BusinessException("方案未配置礼包");
        }

        Map<Long, OrgRewardPackage> packageMap = loadPackages(
                items.stream().map(OrgDistributionPlanItem::getPackageId).distinct().toList());
        // 礼包可能已被删除：跳过失效条目，不因历史方案报错
        List<OrgDistributionPlanItem> validItems = items.stream()
                .filter(item -> packageMap.containsKey(item.getPackageId()))
                .toList();
        if (validItems.isEmpty()) {
            throw new BusinessException("方案配置的礼包都已被删除，请重新配置");
        }

        List<OrgWeekRecordResponse> records = weekRecordService.listRecords(plan.getOrganizationId(), weekDate);
        if (records.isEmpty()) {
            throw new BusinessException("该周暂无记录，请先生成或导入数据");
        }

        Map<Long, OrgWeekRecordResponse> byMember = records.stream()
                .filter(r -> r.getMemberId() != null)
                .collect(Collectors.toMap(OrgWeekRecordResponse::getMemberId, Function.identity(), (a, b) -> a));

        // 剩余名额
        Map<Long, Integer> remaining = new LinkedHashMap<>();
        for (OrgDistributionPlanItem item : validItems) {
            remaining.put(item.getPackageId(), item.getQuantity() == null ? 0 : item.getQuantity());
        }

        Map<Long, Long> assigned = new HashMap<>(); // recordId -> packageId
        Map<Long, Integer> assignedPackageCount = new LinkedHashMap<>();
        List<String> skippedForcedNames = new ArrayList<>();
        int forcedCount = 0;

        // 1. 必发玩家
        List<OrgDistributionForcedPlayer> forcedList = forcedRepository.selectList(
                new LambdaQueryWrapper<OrgDistributionForcedPlayer>()
                        .eq(OrgDistributionForcedPlayer::getPlanId, id)
                        .orderByAsc(OrgDistributionForcedPlayer::getId));
        for (OrgDistributionForcedPlayer forced : forcedList) {
            OrgWeekRecordResponse record = byMember.get(forced.getMemberId());
            Integer left = remaining.get(forced.getPackageId());
            if (record == null || !isEligible(record) || left == null || left <= 0) {
                skippedForcedNames.add(resolveMemberName(forced.getMemberId(), record));
                continue;
            }
            assigned.put(record.getId(), forced.getPackageId());
            remaining.put(forced.getPackageId(), left - 1);
            assignedPackageCount.merge(forced.getPackageId(), 1, Integer::sum);
            forcedCount++;
        }

        // 2. 排名发放
        Comparator<OrgWeekRecordResponse> comparator = Comparator
                .comparing((OrgWeekRecordResponse r) -> metric(r, plan.getRankField()),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> r.getTotalPoints(), Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(OrgWeekRecordResponse::getMemberName,
                        Comparator.nullsLast(Comparator.naturalOrder()));
        List<OrgWeekRecordResponse> ranked = records.stream()
                .filter(this::isEligible)
                .filter(r -> !assigned.containsKey(r.getId()))
                .sorted(comparator)
                .toList();

        for (OrgDistributionPlanItem item : validItems) {
            int left = remaining.getOrDefault(item.getPackageId(), 0);
            for (OrgWeekRecordResponse record : ranked) {
                if (left <= 0) {
                    break;
                }
                if (assigned.containsKey(record.getId())) {
                    continue;
                }
                assigned.put(record.getId(), item.getPackageId());
                assignedPackageCount.merge(item.getPackageId(), 1, Integer::sum);
                left--;
            }
            remaining.put(item.getPackageId(), left);
        }

        // 3. 写回：分配的设置礼包，未分配的清空旧礼包
        int assignedCount = 0;
        for (OrgWeekRecordResponse record : records) {
            Long packageId = assigned.get(record.getId());
            if (packageId != null) {
                weekRecordService.setPackage(record.getId(), packageId);
                assignedCount++;
            } else if (record.getRewardPackageId() != null) {
                weekRecordService.clearPackage(record.getId());
            }
        }

        int zeroBattleCount = (int) records.stream().filter(r -> !isEligible(r)).count();
        List<OrgDistributionRunResponse.PackageSummary> summary = assignedPackageCount.entrySet().stream()
                .map(e -> OrgDistributionRunResponse.PackageSummary.builder()
                        .packageName(packageMap.get(e.getKey()) == null
                                ? "已删除礼包" : packageMap.get(e.getKey()).getName())
                        .count(e.getValue())
                        .build())
                .toList();

        log.info("一键发包完成: planId={}, organizationId={}, weekDate={}, assigned={}, forced={}, zeroBattle={}",
                id, plan.getOrganizationId(), weekDate, assignedCount, forcedCount, zeroBattleCount);

        return OrgDistributionRunResponse.builder()
                .planName(plan.getName())
                .assignedCount(assignedCount)
                .forcedCount(forcedCount)
                .rankedCount(assignedCount - forcedCount)
                .skippedZeroBattleCount(zeroBattleCount)
                .skippedForcedNames(skippedForcedNames)
                .packageSummary(summary)
                .build();
    }

    // ==================== 私有方法 ====================

    private OrgDistributionPlan requirePlan(Long id) {
        OrgDistributionPlan plan = planRepository.selectById(id);
        if (plan == null) {
            throw new BusinessException("发包方案不存在");
        }
        return plan;
    }

    private OrgDistributionPlanResponse detail(Long id) {
        OrgDistributionPlan plan = requirePlan(id);
        return buildResponses(List.of(plan), List.of(id)).get(0);
    }

    private List<OrgDistributionPlanResponse> buildResponses(List<OrgDistributionPlan> plans, List<Long> planIds) {
        List<OrgDistributionPlanItem> items = itemRepository.selectList(
                new LambdaQueryWrapper<OrgDistributionPlanItem>()
                        .in(OrgDistributionPlanItem::getPlanId, planIds)
                        .orderByAsc(OrgDistributionPlanItem::getSortOrder)
                        .orderByAsc(OrgDistributionPlanItem::getId));
        List<OrgDistributionForcedPlayer> forced = forcedRepository.selectList(
                new LambdaQueryWrapper<OrgDistributionForcedPlayer>()
                        .in(OrgDistributionForcedPlayer::getPlanId, planIds)
                        .orderByAsc(OrgDistributionForcedPlayer::getId));

        Set<Long> packageIds = new HashSet<>();
        items.forEach(i -> packageIds.add(i.getPackageId()));
        forced.forEach(f -> packageIds.add(f.getPackageId()));
        Map<Long, OrgRewardPackage> packageMap = loadPackages(new ArrayList<>(packageIds));

        List<Long> memberIds = forced.stream().map(OrgDistributionForcedPlayer::getMemberId).distinct().toList();
        Map<Long, OrgMember> memberMap = memberIds.isEmpty()
                ? Map.of()
                : memberRepository.selectBatchIds(memberIds).stream()
                        .collect(Collectors.toMap(OrgMember::getId, Function.identity(), (a, b) -> a));

        Map<Long, List<OrgDistributionPlanItem>> itemsByPlan = items.stream()
                .collect(Collectors.groupingBy(OrgDistributionPlanItem::getPlanId));
        Map<Long, List<OrgDistributionForcedPlayer>> forcedByPlan = forced.stream()
                .collect(Collectors.groupingBy(OrgDistributionForcedPlayer::getPlanId));

        return plans.stream().map(plan -> OrgDistributionPlanResponse.builder()
                .id(plan.getId())
                .organizationId(plan.getOrganizationId())
                .name(plan.getName())
                .rankField(plan.getRankField())
                .items(itemsByPlan.getOrDefault(plan.getId(), List.of()).stream()
                        .map(item -> OrgDistributionPlanResponse.Item.builder()
                                .id(item.getId())
                                .packageId(item.getPackageId())
                                .packageName(packageName(packageMap, item.getPackageId()))
                                .quantity(item.getQuantity())
                                .sortOrder(item.getSortOrder())
                                .build())
                        .toList())
                .forced(forcedByPlan.getOrDefault(plan.getId(), List.of()).stream()
                        .map(f -> {
                            OrgMember member = memberMap.get(f.getMemberId());
                            return OrgDistributionPlanResponse.Forced.builder()
                                    .id(f.getId())
                                    .memberId(f.getMemberId())
                                    .memberName(member == null ? "未知成员" : member.getName())
                                    .packageId(f.getPackageId())
                                    .packageName(packageName(packageMap, f.getPackageId()))
                                    .build();
                        })
                        .toList())
                .build()).toList();
    }

    private String packageName(Map<Long, OrgRewardPackage> packageMap, Long packageId) {
        OrgRewardPackage pkg = packageMap.get(packageId);
        return pkg == null ? "已删除礼包" : pkg.getName();
    }

    private Map<Long, OrgRewardPackage> loadPackages(List<Long> packageIds) {
        if (packageIds.isEmpty()) {
            return Map.of();
        }
        return packageRepository.selectBatchIds(packageIds).stream()
                .collect(Collectors.toMap(OrgRewardPackage::getId, Function.identity(), (a, b) -> a));
    }

    private void replaceItems(Long planId, List<OrgDistributionPlanRequest.Item> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        int order = 0;
        for (OrgDistributionPlanRequest.Item item : items) {
            itemRepository.insert(OrgDistributionPlanItem.builder()
                    .planId(planId)
                    .packageId(item.getPackageId())
                    .quantity(item.getQuantity())
                    .sortOrder(order++)
                    .build());
        }
    }

    private void replaceForced(Long planId, List<OrgDistributionPlanRequest.Forced> forced) {
        if (forced == null || forced.isEmpty()) {
            return;
        }
        for (OrgDistributionPlanRequest.Forced f : forced) {
            forcedRepository.insert(OrgDistributionForcedPlayer.builder()
                    .planId(planId)
                    .memberId(f.getMemberId())
                    .packageId(f.getPackageId())
                    .build());
        }
    }

    /**
     * 保存校验：礼包归属、必发归属、必发数量不超过礼包名额
     */
    private void validate(Long organizationId, OrgDistributionPlanRequest request, Long excludePlanId) {
        if (request == null || !StringUtils.hasText(request.getName())) {
            throw new BusinessException("方案名称不能为空");
        }
        if (request.getName().trim().length() > 100) {
            throw new BusinessException("方案名称不能超过100个字符");
        }
        if (StringUtils.hasText(request.getRankField()) && !RANK_FIELDS.contains(request.getRankField())) {
            throw new BusinessException("排名依据不合法");
        }

        List<OrgDistributionPlanRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new BusinessException("请至少添加一个礼包");
        }
        Map<Long, Integer> quantityByPackage = new HashMap<>();
        for (OrgDistributionPlanRequest.Item item : items) {
            if (item.getPackageId() == null) {
                throw new BusinessException("礼包不能为空");
            }
            if (item.getQuantity() == null || item.getQuantity() < 1) {
                throw new BusinessException("礼包数量必须大于0");
            }
            if (quantityByPackage.put(item.getPackageId(), item.getQuantity()) != null) {
                throw new BusinessException("同一个礼包不能重复添加");
            }
        }
        Map<Long, OrgRewardPackage> packageMap = loadPackages(new ArrayList<>(quantityByPackage.keySet()));
        if (packageMap.size() != quantityByPackage.size()) {
            throw new BusinessException("存在不属于当前组织的礼包");
        }
        packageMap.values().forEach(pkg -> {
            if (!organizationId.equals(pkg.getOrganizationId())) {
                throw new BusinessException("存在不属于当前组织的礼包");
            }
        });

        List<OrgDistributionPlanRequest.Forced> forced = request.getForced();
        if (forced == null || forced.isEmpty()) {
            return;
        }
        Set<Long> memberIds = new HashSet<>();
        Map<Long, Integer> forcedCountByPackage = new HashMap<>();
        for (OrgDistributionPlanRequest.Forced f : forced) {
            if (f.getMemberId() == null || f.getPackageId() == null) {
                throw new BusinessException("必发玩家与礼包不能为空");
            }
            if (!memberIds.add(f.getMemberId())) {
                throw new BusinessException("同一玩家不能重复设置为必发");
            }
            if (!quantityByPackage.containsKey(f.getPackageId())) {
                throw new BusinessException("必发礼包必须先在方案礼包队列中配置");
            }
            forcedCountByPackage.merge(f.getPackageId(), 1, Integer::sum);
        }
        forcedCountByPackage.forEach((packageId, count) -> {
            if (count > quantityByPackage.get(packageId)) {
                OrgRewardPackage pkg = packageMap.get(packageId);
                throw new BusinessException((pkg == null ? "礼包" : pkg.getName()) + " 的必发人数超过配置数量");
            }
        });

        List<OrgMember> members = memberRepository.selectBatchIds(memberIds);
        if (members.size() != memberIds.size()) {
            throw new BusinessException("存在不属于当前组织的必发玩家");
        }
        for (OrgMember member : members) {
            if (!organizationId.equals(member.getOrganizationId())) {
                throw new BusinessException("存在不属于当前组织的必发玩家");
            }
            if (member.getStatus() == null || member.getStatus() != 1) {
                throw new BusinessException("必发玩家「" + member.getName() + "」已不在组织中");
            }
        }
        log.debug("发包方案校验通过: organizationId={}, excludePlanId={}", organizationId, excludePlanId);
    }

    private String normalizeRankField(String rankField) {
        return StringUtils.hasText(rankField) ? rankField : "totalPoints";
    }

    /** 忍战次数为 0 或未录入都不参与发包 */
    private boolean isEligible(OrgWeekRecordResponse record) {
        return record.getNinjaBattleCount() != null && record.getNinjaBattleCount() > 0;
    }

    private String resolveMemberName(Long memberId, OrgWeekRecordResponse record) {
        if (record != null) {
            return record.getMemberName();
        }
        OrgMember member = memberRepository.selectById(memberId);
        return member == null ? "未知成员" : member.getName();
    }

    private BigDecimal metric(OrgWeekRecordResponse record, String rankField) {
        String field = normalizeRankField(rankField);
        return switch (field) {
            case "thisWeekPoints" -> record.getThisWeekPoints();
            case "powerIncrease" -> toDecimal(record.getPowerIncrease());
            case "totalPower" -> toDecimal(record.getTotalPower());
            case "copperContribution" -> toDecimal(record.getCopperContribution());
            case "beastSacrifice" -> toDecimal(record.getBeastSacrifice());
            case "renegadeCount" -> toDecimal(record.getRenegadeCount());
            default -> record.getTotalPoints();
        };
    }

    private BigDecimal toDecimal(Integer value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
