package org.unreal.modelrouter.tracing.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪安全管理器
 * 
 * 负责管理分布式追踪数据的安全访问控制，包括：
 * - 基于角色的追踪数据访问控制
 * - 追踪查询的权限验证
 * - 追踪数据访问的审计日志
 * - 追踪数据的安全隔离
 * - 敏感追踪信息的访问限制
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TracingSecurityManager {
    
    private final TracingConfiguration tracingConfiguration;
    private final @Lazy StructuredLogger structuredLogger;
    
    // 追踪访问权限缓存
    private final Map<String, Set<String>> userTracePermissions = new ConcurrentHashMap<>();
    
    // 追踪数据访问记录
    private final Map<String, List<TraceAccessRecord>> traceAccessHistory = new ConcurrentHashMap<>();
    
    // 默认的角色权限映射
    private static final Map<String, Set<String>> DEFAULT_ROLE_PERMISSIONS = Map.of(
        "ADMIN", Set.of("trace:read", "trace:write", "trace:delete", "trace:export", "trace:manage"),
        "OPERATOR", Set.of("trace:read", "trace:export"),
        "DEVELOPER", Set.of("trace:read", "trace:write"),
        "READ", Set.of("trace:read"),
        "WRITE", Set.of("trace:read", "trace:write")
    );
    
    /**
     * 验证用户是否有权限访问追踪数据
     * 
     * @param operation 操作类型（read, write, delete, export, manage）
     * @param traceId 追踪ID（可选）
     * @param spanId Span ID（可选）
     * @return 是否有权限
     */
    public Mono<Boolean> hasTracePermission(String operation, String traceId, String spanId) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication == null || !authentication.isAuthenticated()) {
                        recordAccessAttempt(null, operation, traceId, false, "未认证用户");
                        return Mono.just(false);
                    }
                    
                    String username = authentication.getName();
                    Set<String> userPermissions = getUserTracePermissions(authentication);
                    
                    String requiredPermission = "trace:" + operation;
                    boolean hasPermission = userPermissions.contains(requiredPermission);
                    
                    // 记录访问尝试
                    recordAccessAttempt(username, operation, traceId, hasPermission, 
                            hasPermission ? "权限验证通过" : "权限不足");
                    
                    // 记录审计日志
                    if (hasPermission) {
                        recordTraceAccessAudit(username, operation, traceId, spanId, "允许", null);
                    } else {
                        recordTraceAccessAudit(username, operation, traceId, spanId, "拒绝", "权限不足");
                    }
                    
                    return Mono.just(hasPermission);
                })
                .onErrorReturn(false);
    }
    
    /**
     * 验证用户是否有权限查看指定的追踪上下文
     * 
     * @param context 追踪上下文
     * @return 是否有权限
     */
    public Mono<Boolean> canAccessTraceContext(TracingContext context) {
        if (context == null) {
            return Mono.just(false);
        }
        
        return hasTracePermission("read", context.getTraceId(), context.getSpanId());
    }
    
    /**
     * 验证用户是否有权限修改追踪数据
     * 
     * @param context 追踪上下文
     * @return 是否有权限
     */
    public Mono<Boolean> canModifyTraceData(TracingContext context) {
        if (context == null) {
            return Mono.just(false);
        }
        
        return hasTracePermission("write", context.getTraceId(), context.getSpanId());
    }
    
    /**
     * 验证用户是否有权限导出追踪数据
     * 
     * @param traceIds 追踪ID列表
     * @return 是否有权限
     */
    public Mono<Boolean> canExportTraceData(List<String> traceIds) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication == null || !authentication.isAuthenticated()) {
                        return Mono.just(false);
                    }
                    
                    Set<String> userPermissions = getUserTracePermissions(authentication);
                    boolean hasPermission = userPermissions.contains("trace:export");
                    
                    // 记录导出审计日志
                    recordTraceExportAudit(authentication.getName(), traceIds, hasPermission);
                    
                    return Mono.just(hasPermission);
                });
    }
    
    /**
     * 过滤用户可访问的追踪数据
     * 
     * @param traceData 原始追踪数据
     * @return 过滤后的追踪数据
     */
    public Mono<Map<String, Object>> filterAccessibleTraceData(Map<String, Object> traceData) {
        return ReactiveSecurityContextHolder.getContext()
                .map(securityContext -> securityContext.getAuthentication())
                .flatMap(authentication -> {
                    if (authentication == null || !authentication.isAuthenticated()) {
                        return Mono.just(Collections.emptyMap());
                    }
                    
                    Set<String> userPermissions = getUserTracePermissions(authentication);
                    Map<String, Object> filteredData = new HashMap<>();
                    
                    // 基于权限过滤数据字段
                    for (Map.Entry<String, Object> entry : traceData.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        
                        if (canAccessTraceField(key, userPermissions)) {
                            filteredData.put(key, value);
                        } else {
                            // 对敏感字段进行脱敏处理
                            filteredData.put(key, "[REDACTED]");
                        }
                    }
                    
                    return Mono.just(filteredData);
                });
    }
    
    /**
     * 获取用户的追踪权限
     */
    private Set<String> getUserTracePermissions(Authentication authentication) {
        String username = authentication.getName();
        
        // 先检查缓存
        if (userTracePermissions.containsKey(username)) {
            return userTracePermissions.get(username);
        }
        
        Set<String> permissions = new HashSet<>();
        
        // 从用户角色获取权限
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();
            if (role.startsWith("ROLE_")) {
                role = role.substring(5); // 移除 "ROLE_" 前缀
            }
            
            Set<String> rolePermissions = DEFAULT_ROLE_PERMISSIONS.get(role);
            if (rolePermissions != null) {
                permissions.addAll(rolePermissions);
            }
        }
        
        // 检查配置中的允许角色
        List<String> allowedRoles = tracingConfiguration.getSecurity().getAccessControl().getAllowedRoles();
        boolean hasAllowedRole = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(role -> allowedRoles.contains(role.replace("ROLE_", "")));
        
        if (!hasAllowedRole && !allowedRoles.isEmpty()) {
            permissions.clear(); // 如果用户没有配置允许的角色，清空所有权限
        }
        
        // 缓存权限（实际项目中可能需要设置过期时间）
        userTracePermissions.put(username, permissions);
        
        return permissions;
    }
    
    /**
     * 检查是否可以访问指定的追踪字段
     */
    private boolean canAccessTraceField(String fieldName, Set<String> userPermissions) {
        // 敏感字段需要管理员权限
        if (isSensitiveTraceField(fieldName)) {
            return userPermissions.contains("trace:manage");
        }
        
        // 其他字段只需要读权限
        return userPermissions.contains("trace:read");
    }
    
    /**
     * 检查是否为敏感追踪字段
     */
    private boolean isSensitiveTraceField(String fieldName) {
        String lowerField = fieldName.toLowerCase();
        return lowerField.contains("user") ||
               lowerField.contains("auth") ||
               lowerField.contains("token") ||
               lowerField.contains("credential") ||
               lowerField.contains("password") ||
               lowerField.contains("secret");
    }
    
    /**
     * 记录访问尝试
     */
    private void recordAccessAttempt(String username, String operation, String traceId, 
                                   boolean success, String reason) {
        TraceAccessRecord record = new TraceAccessRecord(
                username, operation, traceId, success, reason, Instant.now()
        );
        
        traceAccessHistory.computeIfAbsent(username, k -> new ArrayList<>()).add(record);
        
        // 限制历史记录数量（保留最近100条）
        List<TraceAccessRecord> userHistory = traceAccessHistory.get(username);
        if (userHistory.size() > 100) {
            userHistory.subList(0, userHistory.size() - 100).clear();
        }
    }
    
    /**
     * 记录追踪访问审计日志
     */
    private void recordTraceAccessAudit(String username, String operation, String traceId, 
                                      String spanId, String result, String reason) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("username", username);
            auditData.put("operation", operation);
            auditData.put("traceId", traceId);
            auditData.put("spanId", spanId);
            auditData.put("result", result);
            auditData.put("reason", reason);
            auditData.put("timestamp", Instant.now().toString());
            
            structuredLogger.logSecurityEvent("trace_access", username, null, null);
            
        } catch (Exception e) {
            log.warn("记录追踪访问审计日志失败", e);
        }
    }
    
    /**
     * 记录追踪导出审计日志
     */
    private void recordTraceExportAudit(String username, List<String> traceIds, boolean success) {
        try {
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("username", username);
            auditData.put("operation", "export");
            auditData.put("traceCount", traceIds.size());
            auditData.put("traceIds", traceIds);
            auditData.put("success", success);
            auditData.put("timestamp", Instant.now().toString());
            
            structuredLogger.logSecurityEvent("trace_export", username, null, null);
            
        } catch (Exception e) {
            log.warn("记录追踪导出审计日志失败", e);
        }
    }
    
    /**
     * 获取用户的追踪访问历史
     * 
     * @param username 用户名
     * @return 访问历史记录
     */
    public List<TraceAccessRecord> getUserTraceAccessHistory(String username) {
        return traceAccessHistory.getOrDefault(username, Collections.emptyList());
    }
    
    /**
     * 清理用户权限缓存
     * 
     * @param username 用户名
     */
    public void clearUserPermissionCache(String username) {
        userTracePermissions.remove(username);
        log.debug("清理用户权限缓存: {}", username);
    }
    
    /**
     * 清理所有权限缓存
     */
    public void clearAllPermissionCache() {
        userTracePermissions.clear();
        log.info("清理所有用户权限缓存");
    }
    
    /**
     * 追踪访问记录
     */
    public static class TraceAccessRecord {
        private final String username;
        private final String operation;
        private final String traceId;
        private final boolean success;
        private final String reason;
        private final Instant timestamp;
        
        public TraceAccessRecord(String username, String operation, String traceId, 
                               boolean success, String reason, Instant timestamp) {
            this.username = username;
            this.operation = operation;
            this.traceId = traceId;
            this.success = success;
            this.reason = reason;
            this.timestamp = timestamp;
        }
        
        // Getters
        public String getUsername() { return username; }
        public String getOperation() { return operation; }
        public String getTraceId() { return traceId; }
        public boolean isSuccess() { return success; }
        public String getReason() { return reason; }
        public Instant getTimestamp() { return timestamp; }
    }
}