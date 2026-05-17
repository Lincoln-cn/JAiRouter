package org.unreal.modelrouter.auth.controller;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * 测试安全配置
 * 为 WebFlux Controller 测试提供简化的安全配置
 */
@TestConfiguration
public class TestSecurityConfig {

    /**
     * 禁用安全过滤器链，允许所有请求
     */
    @Bean
    @Primary
    public SecurityWebFilterChain testSecurityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    /**
     * 测试用户
     */
    @Bean
    @Primary
    public MapReactiveUserDetailsService testUserDetailsService() {
        UserDetails admin = User.withUsername("admin")
                .password("{noop}admin123")
                .roles("ADMIN", "USER")
                .build();
        UserDetails user = User.withUsername("testuser")
                .password("{noop}password")
                .roles("USER")
                .build();
        return new MapReactiveUserDetailsService(admin, user);
    }
}
