package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.dto.OrgMembershipApplicationResponse;
import com.evernox.dto.OrgMembershipResponse;
import com.evernox.entity.OrgOrganization;
import com.evernox.entity.OrgUserMember;
import com.evernox.entity.User;
import com.evernox.exception.BusinessException;
import com.evernox.repository.OrgOrganizationRepository;
import com.evernox.repository.OrgUserMemberRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.OrgMembershipService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 组织成员关系服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrgMembershipServiceImpl implements OrgMembershipService {

    private final OrgUserMemberRepository memberRepository;
    private final OrgOrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    @SuppressWarnings("null")
    public List<OrgMembershipResponse> listMine(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<OrgUserMember> members = memberRepository.selectList(new LambdaQueryWrapper<OrgUserMember>()
                .eq(OrgUserMember::getUserId, userId)
                .orderByAsc(OrgUserMember::getId));
        Map<Long, OrgOrganization> orgs = organizationRepository.selectList(null).stream()
                .collect(Collectors.toMap(OrgOrganization::getId, Function.identity()));
        List<OrgMembershipResponse> result = new ArrayList<>();
        for (OrgUserMember m : members) {
            OrgOrganization org = orgs.get(m.getOrganizationId());
            result.add(OrgMembershipResponse.builder()
                    .id(m.getId())
                    .organizationId(m.getOrganizationId())
                    .organizationName(org == null ? null : org.getName())
                    .status(m.getStatus())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public void apply(Long userId, Long organizationId) {
        if (userId == null) {
            throw new BusinessException("请先登录");
        }
        OrgOrganization org = organizationRepository.selectById(organizationId);
        if (org == null) {
            throw new BusinessException("组织不存在");
        }
        OrgUserMember existing = memberRepository.selectOne(new LambdaQueryWrapper<OrgUserMember>()
                .eq(OrgUserMember::getOrganizationId, organizationId)
                .eq(OrgUserMember::getUserId, userId));
        if (existing != null) {
            if (Integer.valueOf(1).equals(existing.getStatus())) {
                throw new BusinessException("你已加入该组织");
            }
            if (Integer.valueOf(0).equals(existing.getStatus())) {
                throw new BusinessException("已提交申请，请等待审批");
            }
        }
        // 一个用户同时只能有一个待审批或已加入的组织
        Long others = memberRepository.selectCount(new LambdaQueryWrapper<OrgUserMember>()
                .eq(OrgUserMember::getUserId, userId)
                .in(OrgUserMember::getStatus, 0, 1)
                .ne(OrgUserMember::getOrganizationId, organizationId));
        if (others != null && others > 0) {
            throw new BusinessException("你已申请或已加入其他组织");
        }

        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            // 被拒绝后重新申请
            existing.setStatus(0);
            existing.setAppliedAt(now);
            existing.setReviewedAt(null);
            existing.setReviewedBy(null);
            memberRepository.updateById(existing);
        } else {
            memberRepository.insert(OrgUserMember.builder()
                    .organizationId(organizationId)
                    .userId(userId)
                    .status(0)
                    .appliedAt(now)
                    .build());
        }
        log.info("组织加入申请: userId={}, organizationId={}", userId, organizationId);
        notifyOwner(org);
    }

    @Override
    @Transactional
    @SuppressWarnings("null")
    public void leave(Long userId, Long organizationId) {
        OrgUserMember existing = memberRepository.selectOne(new LambdaQueryWrapper<OrgUserMember>()
                .eq(OrgUserMember::getOrganizationId, organizationId)
                .eq(OrgUserMember::getUserId, userId));
        if (existing == null || !Integer.valueOf(1).equals(existing.getStatus())) {
            throw new BusinessException("你尚未加入该组织");
        }
        memberRepository.deleteById(existing.getId());
        log.info("退出组织: userId={}, organizationId={}", userId, organizationId);
    }

    @Override
    @SuppressWarnings("null")
    public boolean canView(Long userId, Long organizationId) {
        if (userId == null || organizationId == null) {
            return false;
        }
        User user = userRepository.selectById(userId);
        if (user != null && "admin".equals(user.getRole())) {
            return true;
        }
        Long count = memberRepository.selectCount(new LambdaQueryWrapper<OrgUserMember>()
                .eq(OrgUserMember::getOrganizationId, organizationId)
                .eq(OrgUserMember::getUserId, userId)
                .eq(OrgUserMember::getStatus, 1));
        return count != null && count > 0;
    }

    @Override
    @SuppressWarnings("null")
    public List<OrgMembershipApplicationResponse> listApplications(Long adminId) {
        if (adminId == null) {
            return List.of();
        }
        List<Long> orgIds = organizationRepository.selectList(new LambdaQueryWrapper<OrgOrganization>()
                .eq(OrgOrganization::getOwnerId, adminId)).stream()
                .map(OrgOrganization::getId)
                .toList();
        if (orgIds.isEmpty()) {
            return List.of();
        }
        Map<Long, OrgOrganization> orgs = organizationRepository.selectList(null).stream()
                .collect(Collectors.toMap(OrgOrganization::getId, Function.identity()));
        List<OrgUserMember> applications = memberRepository.selectList(new LambdaQueryWrapper<OrgUserMember>()
                .in(OrgUserMember::getOrganizationId, orgIds)
                .eq(OrgUserMember::getStatus, 0)
                .orderByDesc(OrgUserMember::getId));

        List<OrgMembershipApplicationResponse> result = new ArrayList<>();
        for (OrgUserMember a : applications) {
            OrgOrganization org = orgs.get(a.getOrganizationId());
            User applicant = userRepository.selectById(a.getUserId());
            result.add(OrgMembershipApplicationResponse.builder()
                    .id(a.getId())
                    .organizationId(a.getOrganizationId())
                    .organizationName(org == null ? null : org.getName())
                    .userId(a.getUserId())
                    .username(applicant == null ? null : applicant.getUsername())
                    .email(applicant == null ? null : applicant.getEmail())
                    .appliedAt(a.getAppliedAt())
                    .build());
        }
        return result;
    }

    @Override
    @Transactional
    public void approve(Long adminId, Long id) {
        OrgUserMember app = requireOwned(adminId, id);
        app.setStatus(1);
        app.setReviewedAt(LocalDateTime.now());
        app.setReviewedBy(adminId);
        memberRepository.updateById(app);
        log.info("审批通过组织加入申请: id={}, adminId={}", id, adminId);
    }

    @Override
    @Transactional
    public void reject(Long adminId, Long id) {
        OrgUserMember app = requireOwned(adminId, id);
        app.setStatus(2);
        app.setReviewedAt(LocalDateTime.now());
        app.setReviewedBy(adminId);
        memberRepository.updateById(app);
        log.info("拒绝组织加入申请: id={}, adminId={}", id, adminId);
    }

    private OrgUserMember requireOwned(Long adminId, Long id) {
        OrgUserMember app = memberRepository.selectById(id);
        if (app == null) {
            throw new BusinessException("申请不存在");
        }
        OrgOrganization org = organizationRepository.selectById(app.getOrganizationId());
        if (org == null || !Objects.equals(org.getOwnerId(), adminId)) {
            throw new BusinessException("无权限审批该申请");
        }
        return app;
    }

    private void notifyOwner(OrgOrganization org) {
        if (org.getOwnerId() == null) {
            log.warn("组织无创建者，跳过申请提醒: organizationId={}", org.getId());
            return;
        }
        User owner = userRepository.selectById(org.getOwnerId());
        String email = owner == null ? null : owner.getEmail();
        if (email == null || email.isBlank()) {
            log.warn("组织创建者无邮箱，跳过申请提醒: organizationId={}", org.getId());
            return;
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(Objects.requireNonNull(from));
            helper.setTo(email);
            helper.setSubject("【EverNox】组织加入申请提醒");
            helper.setText("有用户申请加入您创建的组织「" + org.getName() + "」，请前往后台审批。", false);
            mailSender.send(message);
            log.info("已发送组织申请提醒邮件: owner={}", email);
        } catch (Exception e) {
            log.error("组织申请提醒邮件发送失败: to={}", email, e);
        }
    }
}
