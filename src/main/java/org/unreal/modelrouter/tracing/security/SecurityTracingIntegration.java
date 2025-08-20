package org.unreal.modelrouter.tracing.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.unreal.modelrouter.tracing.TracingConstants;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import org.unreal.modelrouter.tracing.reactive.ReactiveTracingContextHolder;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 安全追踪集成组件
 * 
 * 将安全认证和授权事件集成到追踪系统中，包括：
 * - 认证成功和失败的追踪记录
 * - 用户信息添加到追踪上下文
 * - 权限检查的追踪记录
 * - 安全事件的结构化日志
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityTracingIntegration {
    
    private final StructuredLogger structuredLogger;
    
    /**
     * 记录认证成功事件
     * 
     * @param exchange Web交换对象
     * @param authentication 认证对象
     * @return 处理结果
     */
    public Mono<Void> recordAuthenticationSuccess(ServerWebExchange exchange, Authentication authentication) {
        return ReactiveTracingContextHolder.getCurrentContext()
                .doOnNext(context -> {
                    // 添加用户信息到追踪上下文
                    addUserInfoToContext(context, authentication);
                    
                    // 记录认证成功事件
                    String clientIp = getClientIp(exchange);
                    String authMethod = getAuthenticationMethod(authentication);
                    
                    structuredLogger.logAuthenticationEvent(
                            true, authMethod, authentication.getName(), clientIp, context);
                    
                    // 添加认证成功事件到Span
                    context.addEvent(TracingConstants.Events.AUTHENTICATION_SUCCESS, 
                            createAuthenticationEventData(authentication, authMethod, clientIp));
                    
                    log.debug("记录认证成功事件: 用户={}, 方法={}, IP={} (traceId: {})", 
                            authentication.getName(), authMethod, clientIp, context.getTraceId());
                })
                .then();
    }
    
    /**
     * 记录认证失败事件
     * 
     * @param exchange Web交换对象
     * @param reason 失败原因
     * @param attemptedUser 尝试认证的用户（可能为null）
     * @return 处理结果
     */
    public Mono<Void> recordAuthenticationFailure(ServerWebExchange exchange, String reason, String attemptedUser) {
        return ReactiveTracingContextHolder.getCurrentContext()
                .doOnNext(context -> {
                    String clientIp = getClientIp(exchange);
                    String user = attemptedUser != null ? attemptedUser : "unknown";
                    
                    structuredLogger.logAuthenticationEvent(
                            false, "unknown", user, clientIp, context);
                    
                    // 添加认证失败事件到Span
                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("user", user);
                    eventData.put("reason", reason);
                    eventData.put("clientIp", clientIp);
                    eventData.put("timestamp", java.time.Instant.now().toString());
                    
                    context.addEvent(TracingConstants.Events.AUTHENTICATION_FAILURE, eventData);
                    
                    // 设置错误标签
                    context.setTag(TracingConstants.ErrorAttributes.ERROR, true);
                    context.setTag(TracingConstants.ErrorAttributes.ERROR_TYPE, "AuthenticationFailure");
                    context.setTag(TracingConstants.ErrorAttributes.ERROR_MESSAGE, reason);
                    
                    log.debug("记录认证失败事件: 用户={}, 原因={}, IP={} (traceId: {})", 
                            user, reason, clientIp, context.getTraceId());
                })
                .then();
    }
    
    /**
     * 记录授权成功事件
     * 
     * @param exchange Web交换对象
     * @param authentication 认证对象
     * @param resource 访问的资源
     * @return 处理结果
     */
    public Mono<Void> recordAuthorizationSuccess(ServerWebExchange exchange, Authentication authentication, String resource) {
        return ReactiveTracingContextHolder.getCurrentContext()
                .doOnNext(context -> {
                    String clientIp = getClientIp(exchange);
                    
                    // 添加授权成功事件到Span
                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("user", authentication.getName());
                    eventData.put("resource", resource);
                    eventData.put("authorities", getAuthoritiesString(authentication));
                    eventData.put("clientIp", clientIp);
                    eventData.put("timestamp", java.time.Instant.now().toString());
                    
                    context.addEvent(TracingConstants.Events.AUTHORIZATION_SUCCESS, eventData);
                    
                    log.debug("记录授权成功事件: 用户={}, 资源={}, IP={} (traceId: {})", 
                            authentication.getName(), resource, clientIp, context.getTraceId());
                })
                .then();
    }
    
    /**
     * 记录授权失败事件
     * 
     * @param exchange Web交换对象
     * @param authentication 认证对象（可能为null）
     * @param resource 尝试访问的资源
     * @param reason 失败原因
     * @return 处理结果
     */
    public Mono<Void> recordAuthorizationFailure(ServerWebExchange exchange, Authentication authentication, 
                                                 String resource, String reason) {
        return ReactiveTracingContextHolder.getCurrentContext()
                .doOnNext(context -> {
                    String clientIp = getClientIp(exchange);
                    String user = authentication != null ? authentication.getName() : "anonymous";
                    
                    // 记录安全事件
                    structuredLogger.logSecurityEvent("authorization_failure", user, clientIp, context);
                    
                    // 添加授权失败事件到Span
                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("user", user);
                    eventData.put("resource", resource);
                    eventData.put("reason", reason);
                    eventData.put("clientIp", clientIp);
                    eventData.put("timestamp", java.time.Instant.now().toString());
                    
                    if (authentication != null) {
                        eventData.put("authorities", getAuthoritiesString(authentication));
                    }
                    
                    context.addEvent(TracingConstants.Events.AUTHORIZATION_FAILURE, eventData);
                    
                    // 设置错误标签
                    context.setTag(TracingConstants.ErrorAttributes.ERROR, true);
                    context.setTag(TracingConstants.ErrorAttributes.ERROR_TYPE, "AuthorizationFailure");
                    context.setTag(TracingConstants.ErrorAttributes.ERROR_MESSAGE, reason);
                    
                    log.debug("记录授权失败事件: 用户={}, 资源={}, 原因={}, IP={} (traceId: {})", 
                            user, resource, reason, clientIp, context.getTraceId());
                })
                .then();
    }
    
    /**
     * 记录安全配置变更事件
     * 
     * @param configType 配置类型
     * @param action 变更动作
     * @param details 变更详情
     * @param operator 操作者
     * @return 处理结果
     */
    public Mono<Void> recordSecurityConfigChange(String configType, String action, 
                                                Map<String, Object> details, String operator) {
        return ReactiveTracingContextHolder.getCurrentContext()
                .doOnNext(context -> {
                    Map<String, Object> configDetails = new HashMap<>(details);
                    configDetails.put("operator", operator);
                    configDetails.put("timestamp", java.time.Instant.now().toString());
                    
                    structuredLogger.logConfigurationChange(configType, action, configDetails, context);
                    
                    // 添加配置变更事件到Span
                    context.addEvent("security.config.change", configDetails);
                    
                    log.debug("记录安全配置变更: 类型={}, 动作={}, 操作者={} (traceId: {})", 
                            configType, action, operator, context.getTraceId());
                })
                .then();
    }
    
    /**
     * 记录数据脱敏事件
     * 
     * @param field 脱敏字段
     * @param action 脱敏动作
     * @param ruleId 脱敏规则ID
     * @return 处理结果
     */
    public Mono<Void> recordDataSanitization(String field, String action, String ruleId) {
        return ReactiveTracingContextHolder.getCurrentContext()
                .doOnNext(context -> {
                    structuredLogger.logSanitization(field, action, ruleId, context);
                    
                    // 添加脱敏事件到Span
                    Map<String, Object> eventData = new HashMap<>();
                    eventData.put("field", field);
                    eventData.put("action", action);
                    eventData.put("ruleId", ruleId);
                    eventData.put("timestamp", java.time.Instant.now().toString());
                    
                    context.addEvent(TracingConstants.Events.DATA_SANITIZED, eventData);
                    
                    log.debug("记录数据脱敏事件: 字段={}, 动作={}, 规则={} (traceId: {})", 
                            field, action, ruleId, context.getTraceId());
                })
                .then();
    }
    
    /**
     * 添加用户信息到追踪上下文
     */
    private void addUserInfoToContext(TracingContext context, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            // 添加用户相关标签
            context.setTag(TracingConstants.SecurityAttributes.USER_ID, authentication.getName());
            
            // 添加认证方法
            String authMethod = getAuthenticationMethod(authentication);
            context.setTag(TracingConstants.SecurityAttributes.AUTH_METHOD, authMethod);
            
            // 添加用户权限
            String authorities = getAuthoritiesString(authentication);
            if (!authorities.isEmpty()) {
                context.setTag("user.authorities", authorities);
            }
            
            // 添加用户类型（如果可以确定）
            String userType = determineUserType(authentication);
            if (userType != null) {
                context.setTag(TracingConstants.SecurityAttributes.USER_TYPE, userType);
            }
        }
    }
    
    /**
     * 获取认证方法
     */
    private String getAuthenticationMethod(Authentication authentication) {
        if (authentication == null) {
            return "unknown";
        }
        
        String className = authentication.getClass().getSimpleName();
        if (className.contains("ApiKey")) {
            return "api_key";
        } else if (className.contains("Jwt")) {
            return "jwt";
        } else if (className.contains("Basic")) {
            return "basic";
        } else {
            return "custom";
        }
    }
    
    /**
     * 获取用户权限字符串
     */
    private String getAuthoritiesString(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return "";
        }
        
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));
    }
    
    /**
     * 确定用户类型
     */
    private String determineUserType(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return null;
        }
        
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
    }
    
    /**
     * 获取客户端IP地址
     */
    private String getClientIp(ServerWebExchange exchange) {
        // 检查X-Forwarded-For头部
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        // 检查X-Real-IP头部
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // 使用远程地址
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    /**
     * 创建认证事件数据
     */
    private Map<String, Object> createAuthenticationEventData(Authentication authentication, String authMethod, String clientIp) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("user", authentication.getName());
        eventData.put("authMethod", authMethod);
        eventData.put("clientIp", clientIp);
        eventData.put("authorities", getAuthoritiesString(authentication));
        eventData.put("timestamp", java.time.Instant.now().toString());
        
        // 添加认证详情（如果有）
        if (authentication.getDetails() != null) {
            eventData.put("authDetails", authentication.getDetails().toString());
        }
        
        return eventData;
    }
}