package com.evernox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 图片落盘编解码配置
 *
 * 服务端统一密钥，浏览器端不持有任何密钥。
 * 警告: 库中一旦有图片数据，以下三项都不可再修改，否则已存文件无法解码。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "evernox.codec")
public class CodecConfig {

    /** 编解码主密码 */
    private String secret;

    /** 密钥派生盐（Base64 编码） */
    private String salt;

    /** PBKDF2 迭代次数 */
    private int iterations = 100000;
}
