package com.litemall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.AntPathMatcher;

/**
 * M11：KeyPair 与 JwtTool 已由 {@code litemall-common-jwt} 自动装配（原 keyPair bean 已移除）。
 */
@Configuration
public class SecurityConfig {

    @Bean
    public AntPathMatcher antPathMatcher() {
        return new AntPathMatcher();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}