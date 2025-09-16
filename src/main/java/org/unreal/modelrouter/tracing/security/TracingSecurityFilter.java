package org.unreal.modelrouter.tracing.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.tracing.TracingConstants;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.reactive.ReactiveTracingContextHolder;
import reactor.core.publisher.Mono;
import org.unreal.modelrouter.security.config.ExcludedPathsConfig;

/**
 * 追踪安全过滤器
 * 
 * 在安全认证过程中集成追踪功能，包括：
 * - 从安全上下文中提取用户信息并添加到追踪上下文
 * - 记录安全相关的追踪事件
 * - 在追踪上下文中标记安全状态
 * - 处理安全异常的追踪记录
 * 
 * 该过滤器应该在安全认证过滤器之后执行，以便能够获取到认证信息
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TracingSecurityFilter implements WebFilter, Ordered {
    
    private final SecurityTracingIntegration securityTracingIntegration;
    
    /**
     * 设置过滤器优先级，在安全认证过滤器之后执行
     */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 100;
    }
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 检查是否需要跳过追踪
        if (shouldSkipTracing(exchange)) {
            return chain.filter(exchange);
        }
        
        // 使用安全的方式处理追踪，避免修改HTTP headers
        return handleTracingSafely(exchange, chain);
    }
    
    /**
     * 安全地处理追踪逻辑，避免触发ReadOnlyHttpHeaders异常
     */
    private Mono<Void> handleTracingSafely(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .cast(org.springframework.security.core.context.SecurityContext.class)
                .map(org.springframework.security.core.context.SecurityContext::getAuthentication)
                .filter(Authentication::isAuthenticated)
                .flatMap(authentication -> {
                    // 处理认证成功的情况
                    return handleAuthenticatedRequestSafely(exchange, authentication)
                            .then(chain.filter(exchange));
                })
                .switchIfEmpty(
                    // 处理未认证的情况
                    handleUnauthenticatedRequestSafely(exchange)
                            .then(chain.filter(exchange))
                )
                .onErrorResume(error -> {
                    // 安全地处理异常，避免修改headers
                    return handleSecurityErrorSafely(exchange, error)
                            .then(Mono.defer(() -> chain.filter(exchange)));
                });
    }
    
    /**
     * 安全地处理已认证的请求
     */
    private Mono<Void> handleAuthenticatedRequestSafely(ServerWebExchange exchange, Authentication authentication) {
        return ReactiveTracingContextHolder.getCurrentContext()
                .doOnNext(context -> {
                    try {
                        // 添加用户信息到追踪上下文
                        addUserInfoToTracingContext(context, authentication);
                        
                        // 标记为已认证
                        context.setTag("security.authenticated", true);
                        context.setTag("security.principal", authentication.getName());
                        
                        // 添加认证成功事件
                        context.addEvent(TracingConstants.Events.AUTHENTICATION_SUCCESS, 
                                java.util.Map.of(
                                        "user", authentication.getName(),
                                        "authType", authentication.getClass().getSimpleName(),
                                        "timestamp", java.time.Instant.now().toString()
                                ));
                        
                        log.debug("已认证请求追踪信息已添加: 用户={} (traceId: {})", 
                                authentication.getName(), context.getTraceId());
                    } catch (Exception e) {
                        log.debug("添加追踪信息时发生错误，忽略并继续: {}", e.getMessage());
                    }
                })
                .then(securityTracingIntegration.recordAuthenticationSuccess(exchange, authentication))
                .onErrorResume(error -> {
                    log.debug("记录认证成功追踪时发生错误，忽略并继续: {}", error.getMessage());
                    return Mono.empty();
                });
    }
    
    /**
     * 安全地处理未认证的请求
     */
    private Mono<Void> handleUnauthenticatedRequestSafely(ServerWebExchange exchange) {
        return ReactiveTracingContextHolder.getCurrentContext()
                .doOnNext(context -> {
                    try {
                        // 标记为未认证
                        context.setTag("security.authenticated", false);
                        context.setTag("security.anonymous", true);
                        
                        // 添加匿名访问事件
                        context.addEvent("security.anonymous_access", 
                                java.util.Map.of(
                                        "path", exchange.getRequest().getPath().value(),
                                        "method", exchange.getRequest().getMethod() != null ? 
                                                exchange.getRequest().getMethod().name() : "UNKNOWN",
                                        "timestamp", java.time.Instant.now().toString()
                                ));
                        
                        log.debug("未认证请求追踪信息已添加: 路径={} (traceId: {})", 
                                exchange.getRequest().getPath().value(), context.getTraceId());
                    } catch (Exception e) {
                        log.debug("添加匿名访问追踪信息时发生错误，忽略并继续: {}", e.getMessage());
                    }
                })
                .then()
                .onErrorResume(error -> {
                    log.debug("处理未认证请求追踪时发生错误，忽略并继续: {}", error.getMessage());
                    return Mono.empty();
                });
    }
    
    /**
     * 安全地处理安全错误
     */
    private Mono<Void> handleSecurityErrorSafely(ServerWebExchange exchange, Throwable error) {
        return ReactiveTracingContextHolder.getCurrentContext()
                .doOnNext(context -> {
                    try {
                        // 标记安全错误
                        context.setTag("security.error", true);
                        context.setTag("security.error_type", error.getClass().getSimpleName());
                        context.setTag("security.error_message", error.getMessage());
                        
                        // 添加安全错误事件
                        context.addEvent("security.error", 
                                java.util.Map.of(
                                        "errorType", error.getClass().getSimpleName(),
                                        "errorMessage", error.getMessage() != null ? error.getMessage() : "",
                                        "path", exchange.getRequest().getPath().value(),
                                        "timestamp", java.time.Instant.now().toString()
                                ));
                        
                        log.debug("安全错误追踪信息已添加: 错误={} (traceId: {})", 
                                error.getMessage(), context.getTraceId());
                    } catch (Exception e) {
                        log.debug("添加安全错误追踪信息时发生错误，忽略并继续: {}", e.getMessage());
                    }
                })
                .then(securityTracingIntegration.recordAuthenticationFailure(
                        exchange, error.getMessage(), "unknown"))
                .onErrorResume(tracingError -> {
                    log.debug("记录认证失败追踪时发生错误，忽略并继续: {}", tracingError.getMessage());
                    return Mono.empty();
                });
    }
    
    /**
     * 添加用户信息到追踪上下文
     */
    private void addUserInfoToTracingContext(TracingContext context, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            try {
                // 基本用户信息
                context.setTag(TracingConstants.SecurityAttributes.USER_ID, authentication.getName());
                
                // 认证类型
                String authType = authentication.getClass().getSimpleName();
                context.setTag(TracingConstants.SecurityAttributes.AUTH_METHOD, authType);
                
                // 用户权限
                if (authentication.getAuthorities() != null && !authentication.getAuthorities().isEmpty()) {
                    String authorities = authentication.getAuthorities().stream()
                            .map(auth -> auth.getAuthority())
                            .reduce((a, b) -> a + "," + b)
                            .orElse("");
                    context.setTag("user.authorities", authorities);
                    
                    // 确定用户类型
                    String userType = determineUserType(authentication);
                    if (userType != null) {
                        context.setTag(TracingConstants.SecurityAttributes.USER_TYPE, userType);
                    }
                }
                
                // 认证详情（如果有）
                if (authentication.getDetails() != null) {
                    context.setTag("auth.details", authentication.getDetails().toString());
                }
            } catch (Exception e) {
                log.debug("设置用户追踪信息时发生错误: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 确定用户类型
     */
    private String determineUserType(Authentication authentication) {
        if (authentication.getAuthorities() == null) {
            return null;
        }
        
        try {
            boolean hasAdminRole = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().contains("ADMIN"));
            
            if (hasAdminRole) {
                return "admin";
            }
            
            boolean hasWriteRole = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().contains("WRITE"));
            
            if (hasWriteRole) {
                return "user";
            }
            
            return "readonly";
        } catch (Exception e) {
            log.debug("确定用户类型时发生错误: {}", e.getMessage());
            return "unknown";
        }
    }
    
    /**
     * 检查是否应该跳过追踪
     */
    private boolean shouldSkipTracing(ServerWebExchange exchange) {
        try {
            String path = exchange.getRequest().getPath().value();
            return ExcludedPathsConfig.isAuthExcluded(path);
        } catch (Exception e) {
            log.debug("检查是否跳过追踪时发生错误，默认不跳过: {}", e.getMessage());
            return false;
        }
    }
}