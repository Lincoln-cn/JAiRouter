package org.unreal.modelrouter.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.unreal.modelrouter.exceptionhandler.ReactiveGlobalExceptionHandler;
import org.unreal.modelrouter.filter.DefaultAuthenticationConverter;
import org.unreal.modelrouter.security.authentication.ApiKeyService;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.tracing.config.TracingSecurityConfiguration;
import org.unreal.modelrouter.filter.SpringSecurityAuthenticationFilter;

/**
 * Spring Security配置类
 * 配置WebFlux安全过滤器链和认证管理器
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
public class SecurityConfiguration {
    
    private final SecurityProperties securityProperties;
    private final ApiKeyService apiKeyService;
    private final JwtTokenValidator jwtTokenValidator;
    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    private TracingSecurityConfiguration.TracingSecurityFilterChainCustomizer tracingCustomizer;
    
    /**
     * 配置安全过滤器链
     * 定义哪些路径需要认证，哪些可以匿名访问，实现基于角色的访问控制（RBAC）
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveAuthenticationManager authenticationManager,
            SpringSecurityAuthenticationFilter securityFilter) {
        
        log.info("配置Spring Security WebFlux过滤器链");
        
        // 如果启用了追踪功能，则添加追踪过滤器
        if (tracingCustomizer != null) {
            http = tracingCustomizer.customize(http);
            log.info("已集成追踪过滤器到安全过滤器链");
        }
        
        return http
                // 禁用CSRF，因为这是一个API网关
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 禁用表单登录
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // 禁用HTTP Basic认证
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                // 禁用默认的logout
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                // 使用无状态的安全上下文（不使用Session）
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // 配置异常处理
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((exchange, ex) -> {
                            // 直接使用ReactiveGlobalExceptionHandler处理认证异常
                            ReactiveGlobalExceptionHandler exceptionHandler = applicationContext.getBean(ReactiveGlobalExceptionHandler.class);
                            return exceptionHandler.handle(exchange, ex);
                        })
                )
                // 配置授权规则 - 实现基于角色的访问控制（RBAC）
                .authorizeExchange(exchanges -> exchanges
                        // 健康检查端点允许匿名访问
                        .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                        // API文档端点允许匿名访问
                        .pathMatchers("/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                        // JWT登录端点允许匿名访问
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/jwt/login").permitAll()
                        // 其他JWT管理端点需要认证
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/jwt/refresh").authenticated()
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/jwt/revoke").authenticated()
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/jwt/revoke/batch").hasRole("ADMIN")
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/jwt/validate").permitAll()
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/api/auth/jwt/blacklist/stats").hasRole("ADMIN")
                        // 监控端点需要管理员权限
                        .pathMatchers("/actuator/**").hasRole("ADMIN")
//                        .pathMatchers("/actuator/**").permitAll()
                        // 配置管理端点需要管理员权限
                        .pathMatchers("/api/config/**", "/api/instances/**").hasRole("ADMIN")
                        // AI服务端点的细粒度权限控制
                        .pathMatchers(org.springframework.http.HttpMethod.GET, "/v1/**").hasAnyRole("READ", "WRITE", "USER")
                        .pathMatchers(org.springframework.http.HttpMethod.POST, "/v1/**").hasAnyRole("WRITE", "USER")
                        .pathMatchers(org.springframework.http.HttpMethod.PUT, "/v1/**").hasAnyRole("WRITE", "USER")
                        .pathMatchers(org.springframework.http.HttpMethod.PATCH, "/v1/**").hasAnyRole("WRITE", "USER")
                        .pathMatchers(org.springframework.http.HttpMethod.DELETE, "/v1/**").hasAnyRole("DELETE", "USER")
                        // 其他所有请求需要认证
                        .anyExchange().authenticated()
                )
                .addFilterBefore(securityFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .build();
    }

    /**
     * 配置响应式认证管理器
     * 处理不同类型的认证请求
     */
    @Bean
    public ReactiveAuthenticationManager reactiveAuthenticationManager() {
        log.info("创建响应式认证管理器");
        return new CustomReactiveAuthenticationManager(
                apiKeyService, 
                jwtTokenValidator, 
                securityProperties
        );
    }

    /**
     * 配置认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(PasswordEncoder passwordEncoder) {
        // 动态获取UserDetailsService以避免循环依赖
        UserDetailsService userDetailsService = applicationContext.getBean(UserDetailsService.class);
        
        return new org.springframework.security.authentication.ProviderManager(
                java.util.Arrays.asList(
                        new org.springframework.security.authentication.dao.DaoAuthenticationProvider(passwordEncoder) {{
                            setUserDetailsService(userDetailsService);
                        }}
                )
        );
    }
    
    /**
     * 配置认证转换器
     */
    @Bean
    public ServerAuthenticationConverter serverAuthenticationConverter() {
        return new DefaultAuthenticationConverter(securityProperties);
    }
    
    /**
     * 创建API Key认证过滤器
     */
    @Bean
    public SpringSecurityAuthenticationFilter springSecurityApiKeyAuthenticationFilter(
            ServerAuthenticationConverter serverAuthenticationConverter,
            ReactiveAuthenticationManager reactiveAuthenticationManager) {
        return new SpringSecurityAuthenticationFilter(
                securityProperties, 
                serverAuthenticationConverter, 
                reactiveAuthenticationManager);
    }
}