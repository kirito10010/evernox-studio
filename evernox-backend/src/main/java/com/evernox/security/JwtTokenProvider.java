package com.evernox.security;

import com.evernox.config.JwtConfig;
import com.evernox.dto.AuthResponse;
import com.evernox.entity.User;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT Token 提供者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;

    /**
     * 内存黑名单：jti -> token 自身的过期时间（Redis 未启用）
     *
     * 存过期时间是为了能定时清掉已过期条目 —— 只存 jti 的话集合会无界增长，
     * 长跑实例最终会吃满内存。注意进程重启后黑名单清空，已登出的 token 会复活，
     * 这是内存实现的固有代价。
     */
    private static final Map<String, Instant> tokenBlacklist = new ConcurrentHashMap<>();

    /**
     * 生成AccessToken
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getAccessTokenExpiration());

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole())
                .issuer(jwtConfig.getIssuer())
                .issuedAt(now)
                .expiration(expiryDate)
                .id(UUID.randomUUID().toString())
                .signWith(jwtConfig.getPrivateKey())
                .compact();
    }

    /**
     * 生成RefreshToken
     */
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getRefreshTokenExpiration());
        String tokenId = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .issuer(jwtConfig.getIssuer())
                .issuedAt(now)
                .expiration(expiryDate)
                .id(tokenId)
                .signWith(jwtConfig.getPrivateKey())
                .compact();
    }

    /**
     * 生成完整的认证响应
     */
    public AuthResponse generateAuthResponse(User user) {
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtConfig.getAccessTokenExpiration() / 1000)
                .build();
    }

    /**
     * 解析Token获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return Long.parseLong(claims.getSubject());
    }

    /**
     * 解析Token获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("username", String.class);
    }

    /**
     * 解析Token获取角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }

    /**
     * 解析Token
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(jwtConfig.getPublicKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期: {}", e.getMessage());
            throw new JwtTokenExpiredException("Token已过期");
        } catch (SignatureException e) {
            log.warn("Token签名无效: {}", e.getMessage());
            throw new JwtTokenInvalidException("Token签名无效");
        } catch (MalformedJwtException e) {
            log.warn("Token格式错误: {}", e.getMessage());
            throw new JwtTokenInvalidException("Token格式错误");
        } catch (Exception e) {
            log.error("Token解析失败: {}", e.getMessage());
            throw new JwtTokenInvalidException("Token无效");
        }
    }

    /**
     * 验证Token是否有效
     */
    public boolean validateToken(String token) {
        try {
            // 检查Token是否在黑名单中
            if (isTokenBlacklisted(token)) {
                log.warn("Token已被列入黑名单");
                return false;
            }

            // 解析验证Token
            parseToken(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将Token加入黑名单
     */
    public void invalidateToken(String token) {
        try {
            Claims claims = parseToken(token);
            String tokenId = claims.getId();
            Date expiration = claims.getExpiration();
            Instant expiresAt = expiration != null ? expiration.toInstant() : Instant.now().plusSeconds(3600);

            tokenBlacklist.put(tokenId, expiresAt);
            log.info("Token已加入黑名单: {}", tokenId);
        } catch (Exception e) {
            log.error("将Token加入黑名单失败: {}", e.getMessage());
        }
    }

    /**
     * 检查Token是否在黑名单中
     */
    private boolean isTokenBlacklisted(String token) {
        try {
            Claims claims = parseToken(token);
            return tokenBlacklist.containsKey(claims.getId());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 定时清理已过期的黑名单条目：token 本身过期后再拦也没意义
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000L)
    public void cleanupBlacklist() {
        Instant now = Instant.now();
        int before = tokenBlacklist.size();
        tokenBlacklist.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
        int removed = before - tokenBlacklist.size();
        if (removed > 0) {
            log.debug("清理过期黑名单条目: {} 条，剩余 {} 条", removed, tokenBlacklist.size());
        }
    }

    /**
     * Token过期异常
     */
    public static class JwtTokenExpiredException extends RuntimeException {
        public JwtTokenExpiredException(String message) {
            super(message);
        }
    }

    /**
     * Token无效异常
     */
    public static class JwtTokenInvalidException extends RuntimeException {
        public JwtTokenInvalidException(String message) {
            super(message);
        }
    }
}
