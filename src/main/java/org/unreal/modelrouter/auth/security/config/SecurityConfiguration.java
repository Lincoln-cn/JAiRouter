package org.unreal.modelrouter.auth.security.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.unreal.modelrouter.common.exceptionhandler.ReactiveGlobalExceptionHandler;
import org.unreal.modelrouter.auth.filter.DefaultAuthenticationConverter;
import org.unreal.modelrouter.auth.filter.SpringSecurityAuthenticationFilter;
import org.unreal.modelrouter.auth.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.monitor.tracing.config.TracingSecurityConfiguration;

import java.util.List;

/**
 * Spring Security配置类
 * 配置WebFlux安全过滤器链和认证管理器
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
public class SecurityConfiguration {

    private final SecurityProperties securityProperties;
    private final ApiKeyService apiKeyService;
    private JwtTokenValidator jwtTokenValidator;

    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    private TracingSecurityConfiguration.TracingSecurityFilterChainCustomizer tracingCustomizer;

    // 使用setter注入JwtTokenValidator，使其成为可选依赖
    @Autowired(required = false)
    public void setJwtTokenValidator(final JwtTokenValidator jwtTokenValidator) {
        this.jwtTokenValidator = jwtTokenValidator;
    }

    /**
     * 配置安全过滤器链
     * 定义哪些路径需要认证，哪些可以匿名访问，实现基于角色的访问控制（RBAC）
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            final ServerHttpSecurity http,
            final ReactiveAuthenticationManager authenticationManager,
            final ServerAuthenticationConverter serverAuthenticationConverter) {

        log.info("配置Spring Security WebFlux过滤器链");

        // 直接创建认证过滤器实例，避免注册为 @Bean 导致被 Spring Security 自动发现
        // 和 addFilterBefore() 重复添加，引发请求体被多次消费的问题
        SpringSecurityAuthenticationFilter securityFilter =
                new SpringSecurityAuthenticationFilter(securityProperties, serverAuthenticationConverter, authenticationManager);

        // 如果启用了追踪功能，则添加追踪过滤器
        ServerHttpSecurity customizedHttp = http;
        if (tracingCustomizer != null) {
            customizedHttp = tracingCustomizer.customize(http);
            log.info("已集成追踪过滤器到安全过滤器链");
        }

        // 配置授权规则 - 实现基于角色的访问控制（RBAC）
        ServerHttpSecurity.AuthorizeExchangeSpec authorizeExchangeSpec = customizedHttp
                // 禁用CSRF，因为这是一个API网关
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 启用CORS支持，允许Web管理界面跨域访问
                .cors(cors -> cors.disable()) // 使用控制器级别的@CrossOrigin注解
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
                .authorizeExchange();

        // 配置公共路径
        authorizeExchangeSpec
                // 健康检查端点允许匿名访问
                .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // API文档端点允许匿名访问
                .pathMatchers("/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                // Web管理界面静态资源允许匿名访问（前端会处理认证）
                .pathMatchers("/admin/**").permitAll()
                // favicon.ico 允许匿名访问
                .pathMatchers("/favicon.ico").permitAll()
                // 健康状态SSE推送允许匿名访问（前端实时健康状态监听）
                .pathMatchers("/api/health-status/**").permitAll()
                // WebSocket端点允许匿名访问（路由监控等）
                .pathMatchers("/ws/**").permitAll()
                // JWT登录端点允许匿名访问
                .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/jwt/login").permitAll()
                // JWT验证端点允许匿名访问（用于验证令牌有效性）
                .pathMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/jwt/validate").permitAll()
                // JWT账户管理端点需要管理员权限
                .pathMatchers("/api/security/jwt/accounts/**").hasRole("ADMIN")
                // AI服务端点需要认证（API Key权限由适配器层按服务类型控制）
                .pathMatchers("/v1/**").authenticated()
                // 其他API端点需要认证
                .pathMatchers("/api/**").authenticated()
                // 监控端点需要管理员权限（除了已明确允许的健康检查端点）
                .pathMatchers("/actuator/**").hasRole("ADMIN")
                // 其他所有请求需要认证
                .anyExchange().authenticated();

        // 完成配置并添加过滤器
        // 注意：securityFilter 不要同时注册为 @Bean（WebFilter），否则 Spring Security 会
        // 自动发现并添加一次，addFilterBefore 又添加一次，导致认证过滤器执行多次、请求体被多次消费。
        return customizedHttp
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
    public AuthenticationManager authenticationManager(final PasswordEncoder passwordEncoder) {
        // 动态获取UserDetailsService以避免循环依赖
        UserDetailsService userDetailsService = applicationContext.getBean(UserDetailsService.class);
        log.info("=== AuthenticationManager created with UserDetailsService: {} ===", 
            userDetailsService.getClass().getName());

        return new org.springframework.security.authentication.ProviderManager(
                List.of(
                        new DaoAuthenticationProvider(passwordEncoder) {{
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
}