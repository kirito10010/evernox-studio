package com.evernox.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    // 成功
    SUCCESS(200, "操作成功"),

    // 客户端错误 4xx
    FAIL(400, "操作失败"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权，请先登录"),
    TOKEN_EXPIRED(401, "Token已过期，请重新登录"),
    TOKEN_INVALID(401, "Token无效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),

    // 业务错误 5xx
    INTERNAL_ERROR(500, "服务器内部错误"),
    
    // 用户相关 1xxx
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户已存在"),
    USERNAME_ALREADY_EXISTS(1003, "用户名已被使用"),
    EMAIL_ALREADY_EXISTS(1004, "邮箱已被使用"),
    PASSWORD_ERROR(1005, "密码错误"),
    USER_DISABLED(1006, "账号已被禁用"),
    LOGIN_ERROR(1007, "登录失败"),
    REGISTER_ERROR(1008, "注册失败"),

    // 找回密码相关 1xxx
    RESET_CODE_INVALID(1009, "验证码无效或已过期"),
    RESET_CODE_ATTEMPTS_EXCEEDED(1010, "验证码错误次数过多，请重新发送"),
    RESET_CODE_RATE_LIMITED(1011, "发送过于频繁，请稍后再试"),
    MAIL_SEND_FAILED(1012, "邮件发送失败，请稍后再试");

    private final Integer code;
    private final String message;
}
