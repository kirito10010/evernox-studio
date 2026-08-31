package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.OrgMemberRequest;
import com.evernox.dto.OrgMemberResponse;
import com.evernox.entity.OrgMember;
import com.evernox.entity.OrgOrganization;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgMemberRepository;
import com.evernox.repository.OrgOrganizationRepository;
import com.evernox.service.OrgMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 组织成员服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgMemberServiceImpl implements OrgMemberService {

    private final OrgMemberRepository memberRepository;
    private final OrgOrganizationRepository organizationRepository;

    @Override
    @SuppressWarnings("null")
    public List<OrgMemberResponse> list() {
        List<OrgMember> members = memberRepository.selectList(new LambdaQueryWrapper<OrgMember>()
                .orderByAsc(OrgMember::getId));
        Map<Long, String> orgNames = organizationRepository.selectList(null).stream()
                .collect(Collectors.toMap(OrgOrganization::getId, OrgOrganization::getName));
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
