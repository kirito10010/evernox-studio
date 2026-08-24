package com.evernox.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.evernox.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 用户数据访问层
 */
@Mapper
public interface UserRepository extends BaseMapper<User> {

    /**
     * 更新最后登录时间
     */
    @Update("UPDATE user SET last_login_at = #{lastLoginAt} WHERE id = #{userId}")
    int updateLastLoginTime(@Param("userId") Long userId, @Param("lastLoginAt") LocalDateTime lastLoginAt);

    /**
     * 更新积分
     */
    @Update("UPDATE user SET points = points + #{points} WHERE id = #{userId}")
    int updatePoints(@Param("userId") Long userId, @Param("points") Integer points);

    /**
     * 更新用户状态
     */
    @Update("UPDATE user SET status = #{status} WHERE id = #{userId}")
    int updateStatus(@Param("userId") Long userId, @Param("status") Integer status);

    /**
     * 更新用户密码（已编码）
     */
    @Update("UPDATE user SET password = #{password} WHERE id = #{userId}")
    int updatePassword(@Param("userId") Long userId, @Param("password") String password);
}
