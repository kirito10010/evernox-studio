package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.evernox.dto.OrgMemberBatchResponse;
import com.evernox.dto.OrgMemberRequest;
import com.evernox.dto.OrgMemberResponse;
import com.evernox.entity.OrgDistributionForcedPlayer;
import com.evernox.entity.OrgMember;
import com.evernox.entity.OrgOrganization;
import com.evernox.entity.OrgWeekRecord;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgDistributionForcedPlayerRepository;
import com.evernox.repository.OrgMemberRepository;
import com.evernox.repository.OrgOrganizationRepository;
import com.evernox.repository.OrgWeekRecordRepository;
import com.evernox.service.OrgMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 组织成员服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgMemberServiceImpl implements OrgMemberService {

    /** 单次批量操作上限，避免超大 IN 列表 */
    private static final int BATCH_LIMIT = 500;

    private final OrgMemberRepository memberRepository;
    private final OrgOrganizationRepository organizationRepository;
    private final OrgWeekRecordRepository weekRecordRepository;
    private final OrgDistributionForcedPlayerRepository forcedPlayerRepository;

    @Override
    @SuppressWarnings("null")
    public List<OrgMemberResponse> listByOwner(Long ownerId) {
        if (ownerId == null) {
            return List.of();
        }
        // 先取自己名下的组织，再只查这些组织的成员，避免看到别人的成员名单
        List<OrgOrganization> orgs = organizationRepository.selectList(
                new LambdaQueryWrapper<OrgOrganization>().eq(OrgOrganization::getOwnerId, ownerId));
        if (orgs.isEmpty()) {
            return List.of();
        }
        Map<Long, String> orgNames = orgs.stream()
                .collect(Collectors.toMap(OrgOrganization::getId, OrgOrganization::getName));
        List<OrgMember> members = memberRepository.selectList(new LambdaQueryWrapper<OrgMember>()
                .in(OrgMember::getOrganizationId, orgNames.keySet())
                .orderByAsc(OrgMember::getId));
        return members.stream().map(m -> {
            OrgMemberResponse resp = OrgMemberResponse.from(m);
            resp.setOrganizationName(orgNames.get(m.getOrganizationId()));
            return resp;
        }).toList();
    }

    @Override
    @Transactional
    public OrgMemberResponse create(OrgMemberRequest request) {
        requireOrganization(request.getOrganizationId());
        String name = request.getName().trim();
        if (existsByName(name, null)) {
            throw new BusinessException("玩家名已存在: " + name);
        }
        OrgMember member = OrgMember.builder()
                .organizationId(request.getOrganizationId())
                .name(name)
                .position(request.getPosition())
                .status(1)
                .build();
        memberRepository.insert(member);
        log.info("新增组织成员: id={}, name={}, organizationId={}", member.getId(), name, request.getOrganizationId());
        OrgMemberResponse resp = OrgMemberResponse.from(member);
        resp.setOrganizationName(resolveOrgName(request.getOrganizationId()));
        return resp;
    }

    @Override
    @Transactional
    public OrgMemberResponse update(Long id, OrgMemberRequest request) {
        requireOrganization(request.getOrganizationId());
        OrgMember member = memberRepository.selectById(id);
        if (member == null) {
            throw new BusinessException("成员不存在");
        }
        String name = request.getName().trim();
        if (existsByName(name, id)) {
            throw new BusinessException("玩家名已存在: " + name);
        }
        member.setOrganizationId(request.getOrganizationId());
        member.setName(name);
        member.setPosition(request.getPosition());
        memberRepository.updateById(member);
        log.info("更新组织成员: id={}, name={}", id, name);
        OrgMemberResponse resp = OrgMemberResponse.from(member);
        resp.setOrganizationName(resolveOrgName(request.getOrganizationId()));
        return resp;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        OrgMember member = memberRepository.selectById(id);
        if (member == null) {
            throw new BusinessException("成员不存在");
        }
        int s = status != null && status == 0 ? 0 : 1;
        member.setStatus(s);
        memberRepository.updateById(member);
        log.info("切换成员状态: id={}, status={}", id, s);
    }

    // ==================== 批量操作 ====================

    @Override
    @Transactional
    @SuppressWarnings("null")
    public OrgMemberBatchResponse batchUpdateStatus(List<Long> ids, Integer status, Long ownerId) {
        List<OrgMember> members = requireOwnedMembers(ids, ownerId);
        int target = status != null && status == 0 ? 0 : 1;
        List<Long> memberIds =         members.stream().map(m -> m.getId()).toList();
        memberRepository.update(null, new LambdaUpdateWrapper<OrgMember>()
                .in(OrgMember::getId, memberIds)
                .set(OrgMember::getStatus, target));
        log.info("批量切换成员状态: ownerId={}, status={}, count={}", ownerId, target, memberIds.size());
        return batchResult(members);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public OrgMemberBatchResponse batchMoveOrganization(List<Long> ids, Long organizationId, Long ownerId) {
        List<OrgMember> members = requireOwnedMembers(ids, ownerId);
        OrgOrganization target = organizationId == null ? null : organizationRepository.selectById(organizationId);
        if (target == null || ownerId == null || !ownerId.equals(target.getOwnerId())) {
            throw new BusinessException(403, "无权操作该组织");
        }
        List<Long> memberIds =         members.stream().map(m -> m.getId()).toList();
        memberRepository.update(null, new LambdaUpdateWrapper<OrgMember>()
                .in(OrgMember::getId, memberIds)
                .set(OrgMember::getOrganizationId, organizationId));
        log.info("批量移动成员: ownerId={}, organizationId={}, count={}", ownerId, organizationId, memberIds.size());
        return batchResult(members);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public OrgMemberBatchResponse batchUpdatePosition(List<Long> ids, String position, Long ownerId) {
        List<OrgMember> members = requireOwnedMembers(ids, ownerId);
        String value = position == null ? null : position.trim();
        if (value != null && value.isEmpty()) {
            value = null;
        }
        List<Long> memberIds =         members.stream().map(m -> m.getId()).toList();
        memberRepository.update(null, new LambdaUpdateWrapper<OrgMember>()
                .in(OrgMember::getId, memberIds)
                .set(OrgMember::getPosition, value));
        log.info("批量设置成员职务: ownerId={}, position={}, count={}", ownerId, value, memberIds.size());
        return batchResult(members);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public OrgMemberBatchResponse batchDelete(List<Long> ids, Long ownerId) {
        List<OrgMember> members = requireOwnedMembers(ids, ownerId);
        List<Long> memberIds =         members.stream().map(m -> m.getId()).toList();

        // 周记录保留玩家名/职务快照，只断开成员引用（schema.sql 中的既定约定）
        weekRecordRepository.update(null, new LambdaUpdateWrapper<OrgWeekRecord>()
                .in(OrgWeekRecord::getMemberId, memberIds)
                .set(OrgWeekRecord::getMemberId, null));

        // 一键发包的「必发玩家」引用会失效，直接删除，避免方案编辑时报「必发玩家不存在」
        forcedPlayerRepository.delete(new LambdaQueryWrapper<OrgDistributionForcedPlayer>()
                .in(OrgDistributionForcedPlayer::getMemberId, memberIds));

        memberRepository.deleteBatchIds(memberIds);
        log.info("批量删除成员: ownerId={}, count={}, names={}", ownerId, memberIds.size(),
                members.stream().map(m -> m.getName()).toList());
        return batchResult(members);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public OrgMemberBatchResponse clearOrganizationMembers(Long organizationId, Long ownerId) {
        if (organizationId == null) {
            throw new BusinessException("请选择组织");
        }
        List<Long> ids = memberRepository.selectList(new LambdaQueryWrapper<OrgMember>()
                        .eq(OrgMember::getOrganizationId, organizationId))
                .stream().map(m -> m.getId()).toList();
        if (ids.isEmpty()) {
            return OrgMemberBatchResponse.builder().affected(0).names(List.of()).build();
        }
        return batchDelete(ids, ownerId);
    }

    /**
     * 批量操作统一前置校验：非空、去重、数量上限、成员存在、所属组织全部属于当前账号。
     */
    @SuppressWarnings("null")
    private List<OrgMember> requireOwnedMembers(List<Long> ids, Long ownerId) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("请先选择成员");
        }
        List<Long> distinctIds = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new BusinessException("请先选择成员");
        }
        if (distinctIds.size() > BATCH_LIMIT) {
            throw new BusinessException("单次最多操作 " + BATCH_LIMIT + " 名成员");
        }
        if (ownerId == null) {
            throw new BusinessException(403, "无权操作该成员");
        }
        Set<Long> ownedOrgIds = organizationRepository.selectList(
                        new LambdaQueryWrapper<OrgOrganization>().eq(OrgOrganization::getOwnerId, ownerId))
                .stream().map(o -> o.getId()).collect(Collectors.toSet());
        if (ownedOrgIds.isEmpty()) {
            throw new BusinessException(403, "无权操作该成员");
        }
        List<OrgMember> members = memberRepository.selectBatchIds(distinctIds);
        if (members.size() != distinctIds.size()) {
            throw new BusinessException("部分成员不存在，请刷新后重试");
        }
        for (OrgMember member : members) {
            if (!ownedOrgIds.contains(member.getOrganizationId())) {
                throw new BusinessException(403, "无权操作该成员");
            }
        }
        return members;
    }

    private OrgMemberBatchResponse batchResult(List<OrgMember> members) {
        return OrgMemberBatchResponse.builder()
                .affected(members.size())
                .names(members.stream().map(m -> m.getName()).toList())
                .build();
    }

    private void requireOrganization(Long organizationId) {
        if (organizationId == null || organizationRepository.selectById(organizationId) == null) {
            throw new BusinessException("所属组织不存在");
        }
    }

    private String resolveOrgName(Long organizationId) {
        OrgOrganization org = organizationRepository.selectById(organizationId);
        return org == null ? null : org.getName();
    }

    @SuppressWarnings("null")
    private boolean existsByName(String name, Long excludeId) {
        return memberRepository.selectCount(new LambdaQueryWrapper<OrgMember>()
                .eq(OrgMember::getName, name)
                .ne(excludeId != null, OrgMember::getId, excludeId)) > 0;
    }
}
