package com.smartwms.framework.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：
 * 1. 解析 {@code Authorization: Bearer <accessToken>}（统一规范：Bearer 后仅一个空格）；
 * 2. 校验签名与有效期，加载角色/权限/仓库范围到 LoginUser 并写入 SecurityContext；
 * 3. 令牌缺失/非法/用户失效时不写入上下文，由认证入口统一返回 401（免认证白名单见 SecurityConfig）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final LoginUserLoader loginUserLoader;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(BEARER_PREFIX.length());
            try {
                LoginUser basic = jwtUtil.parseAccessToken(token);
                LoginUser loginUser = loginUserLoader.loadLoginUser(basic.getId(), basic.getUsername());
                if (loginUser != null) {
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            loginUser, null, buildAuthorities(loginUser));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (JwtException e) {
                // 令牌过期/非法/refresh 冒用：保持未认证，由认证入口统一返回 401
                log.debug("JWT 校验失败: {}", e.getMessage());
            } catch (Exception e) {
                log.warn("JWT 认证过滤异常", e);
            }
        }
        chain.doFilter(request, response);
    }

    private List<GrantedAuthority> buildAuthorities(LoginUser loginUser) {
        // roleKey 库内口径已含 ROLE_ 前缀（ROLE_ADMIN 等），此处仅兜底补前缀
        return loginUser.getRoles().stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .distinct()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(role))
                .toList();
    }
}
