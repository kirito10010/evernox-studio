package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.OrgRewardPackageRequest;
import com.evernox.entity.OrgRewardPackage;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgRewardPackageRepository;
import com.evernox.service.OrgRewardPackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 组织奖励礼包服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgRewardPackageServiceImpl implements OrgRewardPackageService {

    private final OrgRewardPackageRepository packageRepository;

    @Override
    @SuppressWarnings("null")
    public List<OrgRewardPackage> list(Long organizationId) {
        return packageRepository.selectList(new LambdaQueryWrapper<OrgRewardPackage>()
                .eq(OrgRewardPackage::getOrganizationId, organizationId)
                .orderByAsc(OrgRewardPackage::getSortOrder)
                .orderByAsc(OrgRewardPackage::getId));
    }

    @Override
    @Transactional
    public OrgRewardPackage create(Long organizationId, OrgRewardPackageRequest request) {
        OrgRewardPackage pkg = OrgRewardPackage.builder()
                .organizationId(organizationId)
                .name(request.getName().trim())
                .deductionRatio(request.getDeductionRatio())
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .build();
        packageRepository.insert(pkg);
        log.info("新增奖励礼包: id={}, organizationId={}, name={}", pkg.getId(), organizationId, pkg.getName());
        return pkg;
    }

    @Override
    @Transactional
    public OrgRewardPackage update(Long id, OrgRewardPackageRequest request) {
        OrgRewardPackage pkg = packageRepository.selectById(id);
        if (pkg == null) {
            throw new BusinessException("礼包不存在");
        }
        pkg.setName(request.getName().trim());
        pkg.setDeductionRatio(request.getDeductionRatio());
        if (request.getSortOrder() != null) {
            pkg.setSortOrder(request.getSortOrder());
        }
        packageRepository.updateById(pkg);
        log.info("更新奖励礼包: id={}, name={}", id, pkg.getName());
        return pkg;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        packageRepository.deleteById(id);
        log.info("删除奖励礼包: id={}", id);
    }
}
