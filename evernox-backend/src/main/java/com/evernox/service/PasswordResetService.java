package com.evernox.service;

import com.evernox.dto.PasswordResetConfirmRequest;

/**
 * 邮箱找回密码服务
 */
public interface PasswordResetService {

    /**
     * 发送密码重置验证码
     *
     * 对未注册邮箱同样走成功路径（不发送邮件），避免暴露邮箱是否已注册。
     */
    void sendCode(String email);

    /**
     * 校验验证码并重置密码
     */
    void confirm(PasswordResetConfirmRequest request);
}
