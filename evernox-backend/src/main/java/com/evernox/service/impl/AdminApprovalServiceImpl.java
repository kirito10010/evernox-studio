package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.evernox.common.NoteStatus;
import com.evernox.common.SiteStatus;
import com.evernox.common.UserRole;
import com.evernox.dto.ApprovalSummaryResponse;
import com.evernox.entity.Note;
import com.evernox.entity.QuizQuestion;
import com.evernox.entity.SiteLink;
import com.evernox.entity.User;
import com.evernox.repository.NoteRepository;
import com.evernox.repository.QuizQuestionRepository;
import com.evernox.repository.SiteLinkRepository;
import com.evernox.repository.UserRepository;
import com.evernox.service.AdminApprovalService;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 管理员待审批服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class AdminApprovalServiceImpl implements AdminApprovalService {

    private final SiteLinkRepository siteLinkRepository;
    private final NoteRepository noteRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public ApprovalSummaryResponse getSummary() {
        Long site = siteLinkRepository.selectCount(new LambdaQueryWrapper<SiteLink>()
                .eq(SiteLink::getStatus, SiteStatus.PENDING));
        Long note = noteRepository.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getStatus, NoteStatus.PENDING));
        Long quiz = quizQuestionRepository.selectCount(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getStatus, QuizQuestionServiceImpl.STATUS_PENDING));
        return ApprovalSummaryResponse.builder()
                .sitePending(site)
                .notePending(note)
                .quizPending(quiz)
                .build();
    }

    @Override
    public void notifyPending(String type) {
        List<User> admins = userRepository.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRole, UserRole.ADMIN));
        if (admins.isEmpty()) {
            log.warn("无管理员账号，跳过审批提醒: type={}", type);
            return;
        }
        for (User admin : admins) {
            String email = admin.getEmail();
            if (email == null || email.isBlank()) {
                log.warn("管理员无邮箱，跳过审批提醒: userId={}, type={}", admin.getId(), type);
                continue;
            }
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(Objects.requireNonNull(from));
                helper.setTo(email);
                helper.setSubject("【EverNox】有新的待审批内容：" + type);
                helper.setText("有新的「" + type + "」内容待审批，请登录后台处理。", false);
                mailSender.send(message);
                log.info("已发送审批提醒邮件: type={}, to={}", type, email);
            } catch (Exception e) {
                log.error("审批提醒邮件发送失败: type={}, to={}", type, email, e);
            }
        }
    }
}
