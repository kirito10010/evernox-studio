package com.evernox.service;

import com.evernox.entity.OrgDistributionPlan;
import com.evernox.entity.OrgMember;
import com.evernox.entity.OrgOrganization;
import com.evernox.entity.OrgRewardPackage;
import com.evernox.entity.OrgWeekRecord;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgDistributionPlanRepository;
import com.evernox.repository.OrgMemberRepository;
import com.evernox.repository.OrgOrganizationRepository;
import com.evernox.repository.OrgRewardPackageRepository;
import com.evernox.repository.OrgWeekRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 组织数据归属校验：组织、成员、礼包、周记录、发包方案都必须属于当前账号。
 *
 * 管理端 controller 在调用业务前先过这一层，防止超级会员/管理员通过改 id 操作别人的数据。
 */
@Component
@RequiredArgsConstructor
public class OrgOwnerGuard {

    private final OrgOrganizationRepository organizationRepository;
    private final OrgMemberRepository memberRepository;
    private final OrgRewardPackageRepository packageRepository;
    private final OrgWeekRecordRepository weekRecordRepository;
    private final OrgDistributionPlanRepository planRepository;

    public void requireOrg(Long organizationId, Long ownerId) {
        if (organizationId == null) {
            throw new BusinessException("组织ID不能为空");
        }
        OrgOrganization org = organizationRepository.selectById(organizationId);
        checkOrg(org, ownerId);
    }

    public void requireMember(Long memberId, Long ownerId) {
        OrgMember member = memberRepository.selectById(memberId);
        if (member == null) {
            throw new BusinessException("成员不存在");
        }
        requireOrg(member.getOrganizationId(), ownerId);
    }

    public void requirePackage(Long packageId, Long ownerId) {
        OrgRewardPackage pkg = packageRepository.selectById(packageId);
        if (pkg == null) {
            throw new BusinessException("礼包不存在");
        }
        requireOrg(pkg.getOrganizationId(), ownerId);
    }

    public void requireRecord(Long recordId, Long ownerId) {
        OrgWeekRecord record = weekRecordRepository.selectById(recordId);
        if (record == null) {
            throw new BusinessException("周记录不存在");
        }
        requireOrg(record.getOrganizationId(), ownerId);
    }

    public void requirePlan(Long planId, Long ownerId) {
        OrgDistributionPlan plan = planRepository.selectById(planId);
        if (plan == null) {
            throw new BusinessException("发包方案不存在");
        }
        requireOrg(plan.getOrganizationId(), ownerId);
    }

    /** 批量校验成员 ID 确实属于指定组织（Excel 导入恢复/离开时防止越权） */
    public void requireMembersInOrg(java.util.List<Long> memberIds, Long organizationId, Long ownerId) {
        requireOrg(organizationId, ownerId);
        if (memberIds == null || memberIds.isEmpty()) {
            return;
        }
        for (Long memberId : memberIds) {
            OrgMember member = memberRepository.selectById(memberId);
            if (member != null && !organizationId.equals(member.getOrganizationId())) {
                throw new BusinessException(403, "存在不属于该组织的成员");
            }
        }
    }

    private void checkOrg(OrgOrganization org, Long ownerId) {
        if (org == null) {
            throw new BusinessException("组织不存在");
        }
        if (ownerId == null || !ownerId.equals(org.getOwnerId())) {
            throw new BusinessException(403, "无权操作该组织");
        }
    }
}
