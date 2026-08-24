package com.evernox.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * JWT配置
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    /**
     * AccessToken有效期 (毫秒)，默认1小时
     */
    private long accessTokenExpiration = 3600000L;

    /**
     * RefreshToken有效期 (毫秒)，默认7天
     */
    private long refreshTokenExpiration = 604800000L;

    /**
     * 签发者
     */
    private String issuer = "evernox";

    /**
     * 密钥文件存储路径
     */
    @Value("${jwt.key-path:jwt-keys}")
    private String keyPath;

    /**
     * RSA密钥对 (RS256非对称加密)
     */
    private KeyPair keyPair;

    @PostConstruct
    public void init() {
        try {
            Path keyDir = Paths.get(keyPath);
            Path privateKeyFile = keyDir.resolve("private.key");
            Path publicKeyFile = keyDir.resolve("public.key");

            if (Files.exists(privateKeyFile) && Files.exists(publicKeyFile)) {
                // 从文件加载密钥对
                keyPair = loadKeyPair(privateKeyFile, publicKeyFile);
                log.info("JWT密钥对从文件加载成功");
            } else {
                // 生成新密钥对
                keyPair = generateKeyPair();
                // 保存到文件
                Files.createDirectories(keyDir);
                saveKeyPair(keyPair, privateKeyFile, publicKeyFile);
                log.info("JWT密钥对生成并保存成功");
            }
        } catch (Exception e) {
            log.warn("JWT密钥持久化失败，使用内存模式: {}", e.getMessage());
            keyPair = generateKeyPair();
        }
    }

    /**
     * 生成RSA密钥对
     */
    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("生成RSA密钥对失败", e);
        }
    }

    /**
     * 从文件加载密钥对
     */
    private KeyPair loadKeyPair(Path privateKeyFile, Path publicKeyFile) throws Exception {
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile);
        byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile);

        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);

        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        return new KeyPair(publicKey, privateKey);
    }

    /**
     * 保存密钥对到文件
     */
    private void saveKeyPair(KeyPair keyPair, Path privateKeyFile, Path publicKeyFile) throws Exception {
        Files.write(privateKeyFile, keyPair.getPrivate().getEncoded());
        Files.write(publicKeyFile, keyPair.getPublic().getEncoded());
        log.info("JWT密钥已保存到: {}", keyPath);
    }

    /**
     * 获取私钥
     */
    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    /**
     * 获取公钥
     */
    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }
}
