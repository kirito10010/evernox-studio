package com.evernox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理员账号自动注册配置
 *
 * 来源于外置 config/application.yml 的 evernox.admin.*，
 * 优先级高于 jar 内的 application.yml，且不会被提交到代码仓库。
 */
@Data
@Component
@ConfigurationProperties(prefix = "evernox.admin")
public class AdminProperties {

    /** 管理员用户名 */
    private String username;

    /** 管理员密码（明文，仅存于本机外置配置，启动时用 Argon2id 编码后入库） */
    private String password;

    /** 管理员邮箱 */
    private String email;
}
