package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evernox.dto.AdminOrgOverviewResponse;
import com.evernox.entity.OrgMember;
import com.evernox.entity.OrgOrganization;
import com.evernox.entity.OrgUserMember;
import com.evernox.entity.User;
import com.evernox.repository.OrgMemberRepository;
import com.evernox.repository.OrgOrganizationRepository;
import com.evernox.repository.OrgUserMemberRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.AdminOrgOverviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员组织总览实现：只读，不做任何修改
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOrgOverviewServiceImpl implements AdminOrgOverviewService {

    private final OrgOrganizationRepository organizationRepository;
    private final OrgUserMemberRepository userMemberRepository;
    private final OrgMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Override
    public List<AdminOrgOverviewResponse> listAll() {
        // 用字符串列名的 QueryWrapper：LambdaQueryWrapper 只认方法引用，
        // 传普通 lambda 会在运行时解析不出字段（can not find lambda cache）
        List<OrgOrganization> orgs = organizationRepository.selectList(
                new QueryWrapper<OrgOrganization>().orderByAsc("id"));
        if (orgs.isEmpty()) {
            return List.of();
        }

        List<Long> orgIds = new ArrayList<>(orgs.size());
        List<Long> ownerIds = new ArrayList<>();
        for (OrgOrganization org : orgs) {
            orgIds.add(org.getId());
            Long ownerId = org.getOwnerId();
            if (ownerId != null && !ownerIds.contains(ownerId)) {
                ownerIds.add(ownerId);
            }
        }

        Map<Long, User> owners = new HashMap<>();
        if (!ownerIds.isEmpty()) {
            for (User user : userRepository.selectBatchIds(ownerIds)) {
                owners.put(user.getId(), user);
            }
        }

        // 已加入的平台用户
        List<OrgUserMember> joined = userMemberRepository.selectList(
                new QueryWrapper<OrgUserMember>()
                        .in("organization_id", orgIds)
                        .eq("status", 1)
                        .orderByAsc("id"));

        List<Long> userIds = new ArrayList<>();
        Map<Long, List<OrgUserMember>> joinedByOrg = new HashMap<>();
        for (OrgUserMember member : joined) {
            Long userId = member.getUserId();
            if (userId != null && !userIds.contains(userId)) {
                userIds.add(userId);
            }
            Long orgId = member.getOrganizationId();
            if (orgId != null) {
                List<OrgUserMember> list = joinedByOrg.get(orgId);
                if (list == null) {
                    list = new ArrayList<>();
                    joinedByOrg.put(orgId, list);
                }
                list.add(member);
            }
        }

        Map<Long, User> users = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (User user : userRepository.selectBatchIds(userIds)) {
                users.put(user.getId(), user);
            }
        }

        // 游戏内成员数
        List<OrgMember> members = memberRepository.selectList(
                new QueryWrapper<OrgMember>()
                        .in("organization_id", orgIds)
                        .eq("status", 1));

        Map<Long, Integer> memberCounts = new HashMap<>();
        for (OrgMember member : members) {
            Long orgId = member.getOrganizationId();
            if (orgId == null) {
                continue;
            }
            memberCounts.put(orgId, memberCounts.getOrDefault(orgId, 0) + 1);
        }

        List<AdminOrgOverviewResponse> result = new ArrayList<>(orgs.size());
        for (OrgOrganization org : orgs) {
            User owner = org.getOwnerId() == null ? null : owners.get(org.getOwnerId());
            List<AdminOrgOverviewResponse.JoinedUser> joinedUsers = new ArrayList<>();
            List<OrgUserMember> orgJoinList = joinedByOrg.get(org.getId());
            if (orgJoinList != null) {
                for (OrgUserMember member : orgJoinList) {
                    User user = users.get(member.getUserId());
                    joinedUsers.add(AdminOrgOverviewResponse.JoinedUser.builder()
                            .userId(member.getUserId())
                            .username(user == null ? null : user.getUsername())
                            .email(user == null ? null : user.getEmail())
                            .appliedAt(member.getAppliedAt())
                            .reviewedAt(member.getReviewedAt())
                            .build());
                }
            }
            result.add(AdminOrgOverviewResponse.builder()
                    .id(org.getId())
                    .name(org.getName())
                    .ownerId(org.getOwnerId())
                    .ownerUsername(owner == null ? null : owner.getUsername())
                    .ownerEmail(owner == null ? null : owner.getEmail())
                    .ownerRole(owner == null ? null : owner.getRole())
                    .createdAt(org.getCreatedAt())
                    .joinedUserCount(joinedUsers.size())
                    .memberCount(memberCounts.getOrDefault(org.getId(), 0))
                    .users(joinedUsers)
                    .build());
        }
        return result;
    }
}
