package org.unreal.modelrouter.tracing.logger;

import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.unreal.modelrouter.tracing.TracingContext;

import java.util.Map;

/**
 * 结构化日志记录器接口
 * 
 * 提供结构化的JSON格式日志记录功能，包括：
 * - HTTP请求和响应的结构化日志记录
 * - 后端服务调用的追踪日志
 * - 错误和异常的详细日志记录
 * - 业务事件的结构化记录
 * - 性能指标的日志记录
 * - 安全事件的审计日志
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
public interface StructuredLogger {
    
    // ========================================
    // HTTP请求响应日志
    // ========================================
    
    /**
     * 记录HTTP请求开始日志
     * 
     * @param request HTTP请求对象
     * @param context 追踪上下文
     */
    void logRequest(ServerHttpRequest request, TracingContext context);
    
    /**
     * 记录HTTP响应完成日志
     * 
     * @param response HTTP响应对象
     * @param context 追踪上下文
     * @param duration 请求处理时长（毫秒）
     */
    void logResponse(ServerHttpResponse response, TracingContext context, long duration);
    
    // ========================================
    // 后端服务调用日志
    // ========================================
    
    /**
     * 记录后端服务调用日志
     * 
     * @param adapter 适配器名称
     * @param instance 服务实例名称
     * @param duration 调用时长（毫秒）
     * @param success 是否成功
     * @param context 追踪上下文
     */
    void logBackendCall(String adapter, String instance, long duration, boolean success, TracingContext context);
    
    /**
     * 记录后端服务调用详细信息
     * 
     * @param adapter 适配器名称
     * @param instance 服务实例名称
     * @param url 请求URL
     * @param method HTTP方法
     * @param duration 调用时长（毫秒）
     * @param statusCode 响应状态码
     * @param success 是否成功
     * @param context 追踪上下文
     */
    void logBackendCallDetails(String adapter, String instance, String url, String method, 
                              long duration, int statusCode, boolean success, TracingContext context);
    
    // ========================================
    // 错误和异常日志
    // ========================================
    
    /**
     * 记录错误日志
     * 
     * @param error 异常对象
     * @param context 追踪上下文
     */
    void logError(Throwable error, TracingContext context);
    
    /**
     * 记录错误日志（带额外信息）
     * 
     * @param error 异常对象
     * @param context 追踪上下文
     * @param additionalInfo 额外信息
     */
    void logError(Throwable error, TracingContext context, Map<String, Object> additionalInfo);
    
    // ========================================
    // 业务事件日志
    // ========================================
    
    /**
     * 记录业务事件日志
     * 
     * @param event 事件名称
     * @param data 事件数据
     * @param context 追踪上下文
     */
    void logBusinessEvent(String event, Map<String, Object> data, TracingContext context);
    
    /**
     * 记录负载均衡决策日志
     * 
     * @param strategy 负载均衡策略
     * @param selectedInstance 选中的实例
     * @param availableInstances 可用实例数量
     * @param context 追踪上下文
     */
    void logLoadBalancerDecision(String strategy, String selectedInstance, int availableInstances, TracingContext context);
    
    /**
     * 记录限流检查日志
     * 
     * @param algorithm 限流算法
     * @param allowed 是否允许通过
     * @param remainingTokens 剩余令牌数
     * @param context 追踪上下文
     */
    void logRateLimitCheck(String algorithm, boolean allowed, long remainingTokens, TracingContext context);
    
    /**
     * 记录熔断器状态变化日志
     * 
     * @param previousState 之前状态
     * @param currentState 当前状态
     * @param reason 状态变化原因
     * @param context 追踪上下文
     */
    void logCircuitBreakerStateChange(String previousState, String currentState, String reason, TracingContext context);
    
    // ========================================
    // 性能日志
    // ========================================
    
    /**
     * 记录性能指标日志
     * 
     * @param operation 操作名称
     * @param duration 操作时长（毫秒）
     * @param metrics 性能指标
     * @param context 追踪上下文
     */
    void logPerformance(String operation, long duration, Map<String, Object> metrics, TracingContext context);
    
    /**
     * 记录慢查询日志
     * 
     * @param operation 操作名称
     * @param duration 操作时长（毫秒）
     * @param threshold 慢查询阈值（毫秒）
     * @param context 追踪上下文
     */
    void logSlowQuery(String operation, long duration, long threshold, TracingContext context);
    
    // ========================================
    // 安全事件日志
    // ========================================
    
    /**
     * 记录安全事件日志
     * 
     * @param event 安全事件名称
     * @param user 用户标识
     * @param ip 客户端IP
     * @param context 追踪上下文
     */
    void logSecurityEvent(String event, String user, String ip, TracingContext context);
    
    /**
     * 记录认证事件日志
     * 
     * @param success 认证是否成功
     * @param authMethod 认证方法
     * @param user 用户标识
     * @param ip 客户端IP
     * @param context 追踪上下文
     */
    void logAuthenticationEvent(boolean success, String authMethod, String user, String ip, TracingContext context);
    
    /**
     * 记录数据脱敏日志
     * 
     * @param field 脱敏字段
     * @param action 脱敏动作
     * @param ruleId 脱敏规则ID
     * @param context 追踪上下文
     */
    void logSanitization(String field, String action, String ruleId, TracingContext context);
    
    // ========================================
    // 配置和系统事件日志
    // ========================================
    
    /**
     * 记录配置变更日志
     * 
     * @param configType 配置类型
     * @param action 变更动作
     * @param details 变更详情
     * @param context 追踪上下文
     */
    void logConfigurationChange(String configType, String action, Map<String, Object> details, TracingContext context);
    
    /**
     * 记录系统事件日志
     * 
     * @param event 系统事件名称
     * @param level 日志级别
     * @param details 事件详情
     * @param context 追踪上下文
     */
    void logSystemEvent(String event, String level, Map<String, Object> details, TracingContext context);
}