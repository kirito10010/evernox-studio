package com.evernox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全相关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "evernox.security")
public class SecurityProperties {

    /**
     * 允许跨域的前端来源白名单
     *
     * 不能用 "*"：配合 allowCredentials(true) 时 Spring 会回显请求方 Origin，
     * 等于允许任意站点带凭证调用本服务的接口。
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5211"));
}
