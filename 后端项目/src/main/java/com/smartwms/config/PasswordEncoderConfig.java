package com.smartwms.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * BCrypt 密码编码器。
 * 独立于 SecurityConfig：AuthServiceImpl 依赖 PasswordEncoder，
 * 若与 SecurityConfig（依赖过滤器→AuthServiceImpl）同处一会形成循环依赖。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
