package com.smartwms.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartwms.enums.ResultCode;
import com.smartwms.security.JwtAuthenticationFilter;
import com.smartwms.vo.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 过滤器链配置（《系统安全架构设计》第 2 节）：
 * - 免认证白名单仅 3 个：POST /api/auth/login、POST /api/auth/refresh、POST /api/auth/register；
 * - 其余全部接口需 JWT 认证（401 统一 JSON 信封）；无权限 403 → 40301；
 * - 无状态会话（JWT），禁用 CSRF。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/register").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(this::writeUnauthorized)
                        .accessDeniedHandler(this::writeForbidden))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 未认证/令牌过期 → HTTP 401 + body.code=401。 */
    private void writeUnauthorized(jakarta.servlet.http.HttpServletRequest req,
                                   HttpServletResponse res, Exception e) throws java.io.IOException {
        writeJson(res, HttpServletResponse.SC_UNAUTHORIZED, ResultCode.UNAUTHORIZED);
    }

    /** 已认证但无权限 → HTTP 403 + body.code=40301。 */
    private void writeForbidden(jakarta.servlet.http.HttpServletRequest req,
                                HttpServletResponse res, org.springframework.security.access.AccessDeniedException e)
            throws java.io.IOException {
        writeJson(res, HttpServletResponse.SC_FORBIDDEN, ResultCode.NO_PERM);
    }

    private void writeJson(HttpServletResponse res, int status, ResultCode code) throws java.io.IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(res.getWriter(), Result.of(code.getCode(), code.getMessage()));
    }
}
