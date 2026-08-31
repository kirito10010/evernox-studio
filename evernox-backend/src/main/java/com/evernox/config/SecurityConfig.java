package com.evernox.config;

import com.evernox.security.Argon2idPasswordEncoder;
import com.evernox.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;

/**
 * Spring Security配置
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final Argon2idPasswordEncoder argon2idPasswordEncoder;

    /**
     * 安全过滤器链
     */
    @Bean
    @SuppressWarnings("null")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF (使用JWT不需要CSRF保护)
            .csrf(AbstractHttpConfigurer::disable)
            
            // 禁用Session (使用JWT无状态认证)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            
            // 配置请求授权
            .authorizeHttpRequests(auth -> auth
                // 公开接口 - 注册和登录
                .requestMatchers("/auth/register", "/auth/login", "/auth/refresh").permitAll()

                // 公开接口 - 邮箱找回密码
                .requestMatchers("/auth/password-reset/**").permitAll()
                
                // 公开接口 - 图床公开资源
                .requestMatchers("/image/public", "/album/public").permitAll()
                // 只放行纯数字 ID：Ant 的 * 会把 /image/list、/image/stats、/image/storage 一并放行
                .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "/image/\\d+")).permitAll()
                .requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "/image/\\d+/file")).permitAll()

                // SSE 长连接：EventSource 无法带 Authorization 头，token 走 query 参数，控制器自行校验
                .requestMatchers("/announcement/stream").permitAll()

                // 官方公告本地图片：正文 <img src> 直接引用，无鉴权头，需公开
                .requestMatchers("/hyol/announcement/image/**").permitAll()

                // 忍者图鉴本地图片：头像/技能图标 <img src> 直接引用，无鉴权头，需公开
                .requestMatchers("/hyol/ninja/image/**").permitAll()
                
                // 静态资源和文档
                .requestMatchers("/", "/index.html", "/favicon.ico", "/*.css", "/*.js", "/assets/**").permitAll()
                
                // Swagger文档 (如果有)
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                
                // 健康检查
                .requestMatchers("/actuator/health").permitAll()
                
                // OPTIONS请求
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // 管理员接口：URL 级防线，与控制器类级 @PreAuthorize 形成纵深
                // 必须写在 anyRequest() 之前，Spring Security 先命中先生效
                // 组织积分管理开放给 admin + super_member
                .requestMatchers("/admin/org/**").hasAnyRole("admin", "super_member")
                .requestMatchers("/admin/**").hasRole("admin")

                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            
            // 添加JWT过滤器
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            
            // 异常处理
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未授权，请先登录\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(403);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":403,\"message\":\"无权限访问\"}");
                })
            );
        
        return http.build();
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 密码编码器
     */
    @Bean
    public org.springframework.security.crypto.password.PasswordEncoder passwordEncoder() {
        return argon2idPasswordEncoder;
    }
}
