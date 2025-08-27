package org.unreal.modelrouter.tracing.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.unreal.modelrouter.tracing.filter.TracingWebFilter;
import org.unreal.modelrouter.tracing.security.TracingSecurityFilter;

/**
 * 追踪安全配置类
 * 
 * 配置追踪功能与Spring Security的集成，包括：
 * - 确保TracingWebFilter在安全过滤器之前执行
 * - 配置TracingSecurityFilter在认证过滤器之后执行
 * - 处理追踪和安全的协调工作
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jairouter.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class TracingSecurityConfiguration {
    
    private final @Lazy TracingWebFilter tracingWebFilter;
    private final @Lazy TracingSecurityFilter tracingSecurityFilter;
    
    /**
     * 配置追踪相关的安全过滤器链
     * 
     * 注意：这个方法用于在现有的SecurityWebFilterChain基础上添加追踪过滤器
     * 实际的安全配置仍然由SecurityConfiguration类负责
     */
    @Bean
    @ConditionalOnProperty(name = "jairouter.security.enabled", havingValue = "true")
    public org.springframework.boot.web.server.WebServerFactoryCustomizer<?> tracingSecurityFilterCustomizer() {
        return factory -> {
            log.info("配置追踪安全过滤器集成");
        };
    }
    
    /**
     * 自定义安全过滤器链配置器
     * 
     * 这个方法提供了一个配置器，可以被SecurityConfiguration使用来添加追踪过滤器
     */
    @Bean
    public TracingSecurityFilterChainCustomizer tracingSecurityFilterChainCustomizer() {
        return new TracingSecurityFilterChainCustomizer(tracingWebFilter, tracingSecurityFilter);
    }
    
    /**
     * 追踪安全过滤器链自定义器
     */
    public static class TracingSecurityFilterChainCustomizer {
        private final TracingWebFilter tracingWebFilter;
        private final TracingSecurityFilter tracingSecurityFilter;
        
        public TracingSecurityFilterChainCustomizer(TracingWebFilter tracingWebFilter, 
                                                   TracingSecurityFilter tracingSecurityFilter) {
            this.tracingWebFilter = tracingWebFilter;
            this.tracingSecurityFilter = tracingSecurityFilter;
        }
        
        /**
         * 自定义安全过滤器链，添加追踪过滤器
         * 
         * @param http ServerHttpSecurity配置对象
         * @return 配置后的ServerHttpSecurity
         */
        public ServerHttpSecurity customize(ServerHttpSecurity http) {
            log.info("添加追踪过滤器到安全过滤器链");
            
            return http
                    // 在认证过滤器之前添加追踪Web过滤器
                    .addFilterBefore(tracingWebFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                    // 在授权过滤器之后添加追踪安全过滤器
                    .addFilterAfter(tracingSecurityFilter, SecurityWebFiltersOrder.AUTHORIZATION);
        }
    }
}