package org.unreal.modelrouter.security.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * 默认安全配置
 * 
 * 当 jairouter.security.enabled=false 时生效
 * 仅配置基本的安全规则，允许 /admin/** 和公开端点访问
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "false", matchIfMissing = true)
public class DefaultSecurityConfiguration {

    /**
     * 配置默认安全过滤器链
     * 
     * 当安全功能禁用时，仅保留基本的安全配置：
     * - 禁用表单登录
     * - 禁用 HTTP Basic 认证
     * - 允许 /admin/** 访问（SPA 路由）
     * - 允许健康检查等公开端点访问
     * - API 端点保持开放（因为没有认证机制）
     */
    @Bean
    public SecurityWebFilterChain defaultSecurityWebFilterChain(final ServerHttpSecurity http) {
        log.info("配置默认安全过滤器链（安全功能已禁用）");
        
        return http
                // 禁用 CSRF（API 网关不需要）
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 禁用 CORS（使用控制器级别配置）
                .cors(cors -> cors.disable())
                // 禁用表单登录
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // 禁用 HTTP Basic 认证
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // 禁用 logout
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // 配置授权规则
                .authorizeExchange(exchanges -> exchanges
                        // 健康检查端点允许匿名访问
                        .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        // API文档端点允许匿名访问
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                        // Web管理界面允许匿名访问
                        .pathMatchers("/admin/**").permitAll()
                        // 所有其他请求允许访问（因为安全功能已禁用）
                        .anyExchange().permitAll()
                )
                .build();
    }
}