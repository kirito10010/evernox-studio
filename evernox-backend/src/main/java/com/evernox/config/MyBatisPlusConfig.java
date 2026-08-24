package com.evernox.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置
 *
 * 分页能力由 PaginationInnerInterceptor 改写 SQL 实现，未注册时 selectPage 不会追加
 * LIMIT、也不会执行 count，表现为返回全表数据且 total 恒为 0（即前端分页控件失效）。
 */
@Configuration
public class MyBatisPlusConfig {

    /** 单页上限，与各 Service 层的 MAX_PAGE_SIZE 对齐，兜住被放大的 size 参数 */
    private static final long MAX_PAGE_SIZE = 100L;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 越界页返回空集：若回落到首页，jumper 输入错误页码时会静默拿到错位数据
        pagination.setOverflow(false);
        pagination.setMaxLimit(MAX_PAGE_SIZE);
        interceptor.addInnerInterceptor(pagination);

        return interceptor;
    }
}
