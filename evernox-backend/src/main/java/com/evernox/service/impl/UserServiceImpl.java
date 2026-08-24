package com.evernox.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.evernox.entity.User;
import com.evernox.repository.UserRepository;
import com.evernox.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserRepository, User> implements UserService {

    private final UserRepository userRepository;

    @Override
    @SuppressWarnings("null")
    public User findByUsername(String username) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
    }

    @Override
    @SuppressWarnings("null")
    public User findByEmail(String email) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email));
    }

    @Override
    @SuppressWarnings("null")
    public boolean existsByUsername(String username) {
        return count(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) > 0;
    }

    @Override
    @SuppressWarnings("null")
    public boolean existsByEmail(String email) {
        return count(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email)) > 0;
    }

    @Override
    @Transactional
    public void updateLastLoginTime(Long userId) {
        userRepository.updateLastLoginTime(userId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public void updateStatus(Long userId, Integer status) {
        userRepository.updateStatus(userId, status);
        log.info("用户状态已更新: userId={}, status={}", userId, status);
    }

    @Override
    @Transactional
    public void updatePoints(Long userId, Integer points) {
        userRepository.updatePoints(userId, points);
        log.info("用户积分已更新: userId={}, points={}", userId, points);
    }

    @Override
    @Transactional
    public void updatePassword(Long userId, String encodedPassword) {
        userRepository.updatePassword(userId, encodedPassword);
        log.info("用户密码已更新: userId={}", userId);
    }
}
