package com.evernox.common;

/**
 * 用户角色常量
 *
 * 数据库中统一使用小写值，便于直接阅读。
 */
public final class UserRole {

    /** 管理员 */
    public static final String ADMIN = "admin";

    /** 超级会员 */
    public static final String SUPER_MEMBER = "super_member";

    /** 普通成员 */
    public static final String MEMBER = "member";

    private UserRole() {
    }
}
