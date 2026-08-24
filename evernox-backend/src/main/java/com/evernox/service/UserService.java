package com.evernox.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.evernox.entity.User;

/**
 * 用户服务接口
 */
public interface UserService extends IService<User> {

    /**
     * 根据用户名查询用户
     */
    User findByUsername(String username);

    /**
     * 根据邮箱查询用户
     */
    User findByEmail(String email);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 更新最后登录时间
     */
    void updateLastLoginTime(Long userId);

    /**
     * 更新用户状态
     */
    void updateStatus(Long userId, Integer status);

    /**
     * 更新用户积分
     */
    void updatePoints(Long userId, Integer points);

    /**
     * 更新用户密码（已 Argon2id 编码）
     */
    void updatePassword(Long userId, String encodedPassword);
}
