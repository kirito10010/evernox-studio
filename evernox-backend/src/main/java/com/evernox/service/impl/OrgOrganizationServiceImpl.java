package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.OrgOrganizationRequest;
import com.evernox.dto.OrgOrganizationResponse;
import com.evernox.entity.OrgMember;
import com.evernox.entity.OrgOrganization;
import com.evernox.entity.OrgPointsConfig;
import com.evernox.entity.OrgRewardPackage;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgMemberRepository;
import com.evernox.repository.OrgOrganizationRepository;
import com.evernox.repository.OrgPointsConfigRepository;
import com.evernox.repository.OrgRewardPackageRepository;
import com.evernox.service.OrgOrganizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgOrganizationServiceImpl implements OrgOrganizationService {

    private final OrgOrganizationRepository organizationRepository;
    private final OrgPointsConfigRepository pointsConfigRepository;
    private final OrgRewardPackageRepository packageRepository;
    private final OrgMemberRepository memberRepository;

    @Override
    @SuppressWarnings("null")
    public List<OrgOrganizationResponse> list(Long ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        // 组织数据按账号隔离：超级会员 / 管理员都只能看到自己创建的组织
        List<OrgOrganization> orgs = organizationRepository.selectList(
                new LambdaQueryWrapper<OrgOrganization>()
                        .eq(OrgOrganization::getOwnerId, ownerId)
                        .orderByAsc(OrgOrganization::getId));
        return withMemberCounts(orgs);
    }

    @Override
    @SuppressWarnings("null")
    public List<OrgOrganizationResponse> listAll() {
        List<OrgOrganization> orgs = organizationRepository.selectList(
                new LambdaQueryWrapper<OrgOrganization>().orderByAsc(OrgOrganization::getId));
        return withMemberCounts(orgs);
    }

    /** 批量统计成员数（含已离开），供前端展示与删除前置判断 */
    @SuppressWarnings("null")
    private List<OrgOrganizationResponse> withMemberCounts(List<OrgOrganization> orgs) {
        if (orgs.isEmpty()) {
            return List.of();
        }
        List<Long> orgIds = orgs.stream().map(OrgOrganization::getId).toList();
        Map<Long, Long> counts = memberRepository.selectList(new LambdaQueryWrapper<OrgMember>()
                        .in(OrgMember::getOrganizationId, orgIds))
                .stream()
                .collect(Collectors.groupingBy(OrgMember::getOrganizationId, Collectors.counting()));
        return orgs.stream().map(o -> {
            OrgOrganizationResponse resp = OrgOrganizationResponse.from(o);
            resp.setMemberCount(counts.getOrDefault(o.getId(), 0L));
            return resp;
        }).toList();
    }

    @Override
    @Transactional
    public OrgOrganizationResponse create(OrgOrganizationRequest request, Long ownerId) {
        String name = request.getName().trim();
        if (existsByName(name, null)) {
            throw new BusinessException("组织名称已存在: " + name);
        }
        OrgOrganization org = OrgOrganization.builder().name(name).ownerId(ownerId).build();
        organizationRepository.insert(org);
        seedDefaults(org.getId());
        log.info("新增组织: id={}, name={}, ownerId={}", org.getId(), name, ownerId);
        return OrgOrganizationResponse.from(org);
    }

    @Override
    @Transactional
    public OrgOrganizationResponse update(Long id, OrgOrganizationRequest request, Long ownerId) {
        OrgOrganization org = requireOwned(id, ownerId);
        String name = request.getName().trim();
        if (existsByName(name, id)) {
            throw new BusinessException("组织名称已存在: " + name);
        }
        org.setName(name);
        organizationRepository.updateById(org);
        log.info("更新组织: id={}, name={}, ownerId={}", id, name, ownerId);
        return OrgOrganizationResponse.from(org);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public void delete(Long id, Long ownerId) {
        requireOwned(id, ownerId);
        Long memberCount = memberRepository.selectCount(
                new LambdaQueryWrapper<OrgMember>().eq(OrgMember::getOrganizationId, id));
        if (memberCount != null && memberCount > 0) {
            throw new BusinessException("该组织下仍有 " + memberCount
                    + " 名成员（含已离开），请先在「成员管理」批量删除后再试");
        }
        organizationRepository.deleteById(id);
        log.info("删除组织: id={}, ownerId={}", id, ownerId);
    }

    /** 组织必须存在且属于当前账号 */
    private OrgOrganization requireOwned(Long id, Long ownerId) {
        OrgOrganization org = organizationRepository.selectById(id);
        if (org == null) {
            throw new BusinessException("组织不存在");
        }
        if (ownerId == null || !ownerId.equals(org.getOwnerId())) {
            throw new BusinessException(403, "无权操作该组织");
        }
        return org;
    }

    /** 创建组织时初始化默认换算比与三个默认礼包 */
    private void seedDefaults(Long organizationId) {
        OrgPointsConfig config = OrgPointsConfig.builder()
                .organizationId(organizationId)
                .ninjaBattlePoints(new BigDecimal("20")).ninjaBattleEnabled(1)
                .totalPowerPoints(new BigDecimal("0.00005")).totalPowerEnabled(1)
                .powerIncreasePoints(new BigDecimal("0")).powerIncreaseEnabled(1)
                .copperPoints(new BigDecimal("0.02")).copperEnabled(1)
                .beastPoints(new BigDecimal("0.01")).beastEnabled(1)
                .renegadePoints(new BigDecimal("3")).renegadeEnabled(1)
                .renegadeLeaderBonus(new BigDecimal("50")).renegadeLeaderEnabled(1)
                .noPackageAdjustment(BigDecimal.ZERO)
                .ninjaBattleVisible(1).totalPowerVisible(1)
                .powerIncreaseVisible(1).copperVisible(1).beastVisible(1)
                .renegadeVisible(1).renegadeLeaderVisible(1)
                .build();
        pointsConfigRepository.insert(config);
        packageRepository.insert(pkg(organizationId, "本服-传说功勋礼包", "0.65", 1));
        packageRepository.insert(pkg(organizationId, "本服-英雄功勋礼包", "0.55", 2));
        packageRepository.insert(pkg(organizationId, "本服-精英功勋礼包", "0.45", 3));
        packageRepository.insert(pkg(organizationId, "跨服-传说功勋礼包", "0.75", 4));
        packageRepository.insert(pkg(organizationId, "跨服-英雄功勋礼包", "0.65", 5));
        packageRepository.insert(pkg(organizationId, "跨服-精英功勋礼包", "0.55", 6));
        log.info("初始化组织默认换算比与礼包: organizationId={}", organizationId);
    }

    private OrgRewardPackage pkg(Long organizationId, String name, String ratio, int sortOrder) {
        return OrgRewardPackage.builder()
                .organizationId(organizationId)
                .name(name)
                .deductionRatio(new BigDecimal(ratio))
                .sortOrder(sortOrder)
                .build();
    }

    @SuppressWarnings("null")
    private boolean existsByName(String name, Long excludeId) {
        return organizationRepository.selectCount(new LambdaQueryWrapper<OrgOrganization>()
                .eq(OrgOrganization::getName, name)
                .ne(excludeId != null, OrgOrganization::getId, excludeId)) > 0;
    }
}
