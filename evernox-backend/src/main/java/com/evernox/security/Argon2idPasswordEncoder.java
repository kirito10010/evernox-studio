package com.evernox.security;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id 密码编码器
 * 
 * Argon2id 是目前最安全的密码哈希算法，在2015年密码哈希竞赛中获胜。
 * 相比 BCrypt 和 PBKDF2，Argon2id 更抗GPU破解和侧信道攻击。
 * 
 * 配置参数：
 * - 内存消耗: 64MB (65536 KB)
 * - 迭代次数: 3
 * - 并行度: 4
 * - 输出长度: 32字节
 * - 盐值长度: 16字节
 */
@Component
public class Argon2idPasswordEncoder implements PasswordEncoder {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int ITERATIONS = 3;
    private static final int MEMORY = 65536; // 64MB
    private static final int PARALLELISM = 4;

    private final Argon2 argon2;

    public Argon2idPasswordEncoder() {
        this.argon2 = Argon2Factory.create(
            Argon2Factory.Argon2Types.ARGON2id,
            SALT_LENGTH,
            HASH_LENGTH
        );
    }

    @Override
    public String encode(CharSequence rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("密码不能为空");
        }
        
        char[] password = rawPassword.toString().toCharArray();
        
        try {
            return argon2.hash(ITERATIONS, MEMORY, PARALLELISM, password);
        } finally {
            // 清除内存中的密码
            argon2.wipeArray(password);
        }
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null) {
            return false;
        }
        
        char[] password = rawPassword.toString().toCharArray();
        
        try {
            return argon2.verify(encodedPassword, password);
        } finally {
            // 清除内存中的密码
            argon2.wipeArray(password);
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        // 总是使用最新的加密方式
        return true;
    }
}
