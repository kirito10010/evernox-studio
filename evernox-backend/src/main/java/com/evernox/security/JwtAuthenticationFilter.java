package com.evernox.security;

import com.evernox.common.Result;
import com.evernox.common.ResultCode;
import com.evernox.common.UserRole;
import com.evernox.entity.User;
import com.evernox.repository.UserRepository;
import com.evernox.security.JwtTokenProvider.JwtTokenExpiredException;
import com.evernox.security.JwtTokenProvider.JwtTokenInvalidException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        
        try {
            String token = resolveToken(request);
            
            if (StringUtils.hasText(token)) {
                if (jwtTokenProvider.validateToken(token)) {
                    Long userId = jwtTokenProvider.getUserIdFromToken(token);

                    // 角色与状态一律以库为准：token 里的 role 可能是降级前的旧值，
                    // 账号被禁用或删除后旧 token 也必须立刻失效（access token 有效期 1 小时）
                    User user = userId == null ? null : userRepository.selectById(userId);
                    if (user == null) {
                        handleAuthenticationException(response, ResultCode.UNAUTHORIZED);
                        return;
                    }
                    if (user.getStatus() == null || user.getStatus() != 1) {
                        handleAuthenticationException(response, ResultCode.USER_DISABLED);
                        return;
                    }

                    String role = UserRole.effective(user.getRole(), user.getSuperMemberExpiresAt());

                    // 创建认证信息
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    
                    // 设置用户详情
                    authentication.setDetails(new UserPrincipal(userId, user.getUsername(), role));
                    
                    // 设置到Security上下文
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    
                    log.debug("JWT认证成功: userId={}, username={}, role={}", userId, user.getUsername(), role);
                }
            }
        } catch (JwtTokenExpiredException e) {
            handleAuthenticationException(response, ResultCode.TOKEN_EXPIRED);
            return;
        } catch (JwtTokenInvalidException e) {
            handleAuthenticationException(response, ResultCode.TOKEN_INVALID);
            return;
        } catch (Exception e) {
            log.error("JWT认证失败: {}", e.getMessage());
        }
        
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头中解析Token
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    /**
     * 处理认证异常
     */
    private void handleAuthenticationException(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        
        Result<Void> result = Result.fail(resultCode);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    /**
     * 用户主体信息
     */
    public record UserPrincipal(Long userId, String username, String role) {}
}
