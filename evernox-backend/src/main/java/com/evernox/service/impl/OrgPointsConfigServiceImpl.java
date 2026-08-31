package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.entity.OrgPointsConfig;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgPointsConfigRepository;
import com.evernox.service.OrgPointsConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 组织积分换算比配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgPointsConfigServiceImpl implements OrgPointsConfigService {

    private final OrgPointsConfigRepository configRepository;

    @Override
    @SuppressWarnings("null")
    public OrgPointsConfig get(Long organizationId) {
        OrgPointsConfig config = configRepository.selectOne(new LambdaQueryWrapper<OrgPointsConfig>()
                .eq(OrgPointsConfig::getOrganizationId, organizationId));
        if (config == null) {
            config = defaultConfig(organizationId);
            configRepository.insert(config);
            log.info("初始化组织积分换算比默认配置: organizationId={}", organizationId);
        }
        return config;
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public OrgPointsConfig save(Long organizationId, OrgPointsConfig config) {
        validate(config);
        OrgPointsConfig existing = configRepository.selectOne(new LambdaQueryWrapper<OrgPointsConfig>()
                .eq(OrgPointsConfig::getOrganizationId, organizationId));
        config.setOrganizationId(organizationId);
        if (existing == null) {
            config.setId(null);
            configRepository.insert(config);
        } else {
            config.setId(existing.getId());
            configRepository.updateById(config);
        }
        log.info("保存组织积分换算比配置: organizationId={}", organizationId);
        return configRepository.selectById(config.getId());
    }

    private void validate(OrgPointsConfig config) {
        requireNonNegative(config.getNinjaBattlePoints(), "忍战次数积分");
        requireNonNegative(config.getTotalPowerPoints(), "总战力积分");
        requireNonNegative(config.getPowerIncreasePoints(), "战力增幅积分");
        requireNonNegative(config.getCopperPoints(), "铜币贡献积分");
        requireNonNegative(config.getBeastPoints(), "通灵兽献祭积分");
        requireNonNegative(config.getRenegadePoints(), "叛忍积分");
        requireNonNegative(config.getRenegadeLeaderBonus(), "叛忍车头额外积分");
        config.setNinjaBattleEnabled(normalize(config.getNinjaBattleEnabled()));
        config.setTotalPowerEnabled(normalize(config.getTotalPowerEnabled()));
        config.setPowerIncreaseEnabled(normalize(config.getPowerIncreaseEnabled()));
        config.setCopperEnabled(normalize(config.getCopperEnabled()));
        config.setBeastEnabled(normalize(config.getBeastEnabled()));
        config.setRenegadeEnabled(normalize(config.getRenegadeEnabled()));
        config.setRenegadeLeaderEnabled(normalize(config.getRenegadeLeaderEnabled()));
        config.setNinjaBattleVisible(normalize(config.getNinjaBattleVisible()));
        config.setTotalPowerVisible(normalize(config.getTotalPowerVisible()));
        config.setPowerIncreaseVisible(normalize(config.getPowerIncreaseVisible()));
        config.setCopperVisible(normalize(config.getCopperVisible()));
        config.setBeastVisible(normalize(config.getBeastVisible()));
        config.setRenegadeVisible(normalize(config.getRenegadeVisible()));
        config.setRenegadeLeaderVisible(normalize(config.getRenegadeLeaderVisible()));
    }

    private void requireNonNegative(BigDecimal value, String label) {
        if (value != null && value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(label + "不能小于0");
        }
    }

    private Integer normalize(Integer value) {
        return Integer.valueOf(1).equals(value) ? 1 : 0;
    }

    private OrgPointsConfig defaultConfig(Long organizationId) {
        return OrgPointsConfig.builder()
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
    }
}
