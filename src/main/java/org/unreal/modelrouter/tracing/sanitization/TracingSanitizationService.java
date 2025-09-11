package org.unreal.modelrouter.tracing.sanitization;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.sanitization.SanitizationService;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪数据脱敏服务
 * 
 * 专门处理分布式追踪系统中的敏感数据脱敏，包括：
 * - Span属性的敏感数据脱敏
 * - Span事件中的敏感数据脱敏
 * - 追踪上下文中的敏感信息处理
 * - 追踪特定的脱敏规则管理
 * - 脱敏操作的审计记录
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TracingSanitizationService {
    
    private final SanitizationService sanitizationService;
    private final TracingConfiguration tracingConfiguration;
    private final @Lazy StructuredLogger structuredLogger;
    
    // 追踪特定的敏感字段集合
    private final Set<String> tracingSensitiveFields = ConcurrentHashMap.newKeySet();
    
    // 追踪脱敏规则缓存
    private final Map<String, String> tracingSanitizationRules = new ConcurrentHashMap<>();
    
    // 追踪敏感属性键的模式
    private static final String[] SENSITIVE_ATTRIBUTE_PATTERNS = {
        "user.id", "user.email", "user.name", "user.phone",
        "auth.token", "auth.key", "auth.credential",
        "request.body", "response.body", "sql.query",
        "db.statement", "http.request.header.authorization",
        "http.request.header.cookie", "error.stack_trace"
    };
    
    /**
     * 初始化追踪脱敏服务
     */
    public void initialize() {
        log.info("初始化追踪数据脱敏服务");
        
        // 加载默认的追踪敏感字段
        loadDefaultTracingSensitiveFields();
        
        // 加载配置中的追踪脱敏规则
        loadTracingSanitizationRules();
        
        log.info("追踪数据脱敏服务初始化完成，敏感字段数量: {}", tracingSensitiveFields.size());
    }
    
    /**
     * 脱敏Span属性
     * 
     * @param attributes 原始属性
     * @param context 追踪上下文
     * @return 脱敏后的属性
     */
    public Mono<Attributes> sanitizeSpanAttributes(Attributes attributes, TracingContext context) {
        if (!isTracingSanitizationEnabled() || attributes.isEmpty()) {
            return Mono.just(attributes);
        }
        
        return Mono.fromCallable(() -> {
            AttributesBuilder builder = Attributes.builder();
            
            attributes.forEach((attributeKey, attributeValue) -> {
                if (isSensitiveAttribute(attributeKey)) {
                    // 对敏感属性进行脱敏
                    if (attributeValue instanceof String) {
                        sanitizationService.sanitizeRequest((String) attributeValue, "text/plain", null)
                                .subscribe(sanitizedValue -> {
                                    putAttributeSafely(builder, attributeKey, sanitizedValue);
                                    // 记录脱敏操作审计日志
                                    recordSanitizationAudit("span_attribute", attributeKey.getKey(), "mask", context);
                                });
                    } else {
                        putAttributeSafely(builder, attributeKey, attributeValue);
                    }
                } else {
                    putAttributeSafely(builder, attributeKey, attributeValue);
                }
            });
            
            return builder.build();
        });
    }
    
    /**
     * 脱敏Span事件属性
     * 
     * @param eventName 事件名称
     * @param eventAttributes 事件属性
     * @param context 追踪上下文
     * @return 脱敏后的事件属性
     */
    public Mono<Map<String, Object>> sanitizeEventAttributes(String eventName, 
                                                           Map<String, Object> eventAttributes, 
                                                           TracingContext context) {
        if (!isTracingSanitizationEnabled() || eventAttributes.isEmpty()) {
            return Mono.just(eventAttributes);
        }
        
        return Mono.fromCallable(() -> {
            Map<String, Object> sanitizedAttributes = new HashMap<>();
            
            for (Map.Entry<String, Object> entry : eventAttributes.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (isSensitiveEventAttribute(key)) {
                    // 对敏感事件属性进行脱敏
                    if (value instanceof String) {
                        sanitizationService.sanitizeRequest((String) value, "text/plain", null)
                                .subscribe(sanitizedValue -> {
                                    sanitizedAttributes.put(key, sanitizedValue);
                                    
                                    // 记录脱敏操作审计日志
                                    recordSanitizationAudit("event_attribute", key, "mask", context);
                                });
                    } else {
                        sanitizedAttributes.put(key, value);
                    }
                } else {
                    sanitizedAttributes.put(key, value);
                }
            }
            
            return sanitizedAttributes;
        });
    }
    
    /**
     * 脱敏结构化日志数据
     * 
     * @param logData 日志数据
     * @param context 追踪上下文
     * @return 脱敏后的日志数据
     */
    public Mono<Map<String, Object>> sanitizeLogData(Map<String, Object> logData, TracingContext context) {
        if (!isTracingSanitizationEnabled() || logData.isEmpty()) {
            return Mono.just(logData);
        }
        
        return sanitizeMapRecursively(logData, context);
    }
    
    /**
     * 递归脱敏Map数据
     */
    @SuppressWarnings("unchecked")
    private Mono<Map<String, Object>> sanitizeMapRecursively(Map<String, Object> data, TracingContext context) {
        Map<String, Object> sanitizedData = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            if (value instanceof String && isSensitiveLogField(key)) {
                // 脱敏字符串值
                sanitizationService.sanitizeRequest((String) value, "text/plain", null)
                        .subscribe(sanitizedValue -> {
                            sanitizedData.put(key, sanitizedValue);
                            recordSanitizationAudit("log_field", key, "mask", context);
                        });
            } else if (value instanceof Map) {
                // 递归处理嵌套Map
                sanitizeMapRecursively((Map<String, Object>) value, context)
                        .subscribe(sanitizedValue -> sanitizedData.put(key, sanitizedValue));
            } else {
                sanitizedData.put(key, value);
            }
        }
        
        return Mono.just(sanitizedData);
    }
    
    /**
     * 安全地放置属性到AttributesBuilder中，处理泛型类型问题
     */
    @SuppressWarnings("unchecked")
    private void putAttributeSafely(AttributesBuilder builder, AttributeKey<?> key, Object value) {
        try {
            // 根据值的类型创建对应的AttributeKey并放置值
            if (value instanceof String) {
                builder.put(AttributeKey.stringKey(key.getKey()), (String) value);
            } else if (value instanceof Long) {
                builder.put(AttributeKey.longKey(key.getKey()), (Long) value);
            } else if (value instanceof Integer) {
                builder.put(AttributeKey.longKey(key.getKey()), ((Integer) value).longValue());
            } else if (value instanceof Double) {
                builder.put(AttributeKey.doubleKey(key.getKey()), (Double) value);
            } else if (value instanceof Float) {
                builder.put(AttributeKey.doubleKey(key.getKey()), ((Float) value).doubleValue());
            } else if (value instanceof Boolean) {
                builder.put(AttributeKey.booleanKey(key.getKey()), (Boolean) value);
            } else {
                // 如果类型不匹配，转换为String类型
                builder.put(AttributeKey.stringKey(key.getKey()), String.valueOf(value));
            }
        } catch (Exception e) {
            log.debug("放置属性失败: {}, 使用默认值", key.getKey(), e);
            builder.put(AttributeKey.stringKey(key.getKey()), String.valueOf(value));
        }
    }
    
    /**
     * 脱敏属性值
     */
    @SuppressWarnings("unchecked")
    private Mono<Object> sanitizeAttributeValue(AttributeKey<?> key, Object value, TracingContext context) {
        if (value instanceof String) {
            return sanitizationService.sanitizeRequest((String) value, "text/plain", null)
                    .cast(Object.class);
        }
        return Mono.just(value);
    }
    
    /**
     * 检查是否为敏感属性
     */
    private boolean isSensitiveAttribute(AttributeKey<?> attributeKey) {
        String key = attributeKey.getKey().toLowerCase();
        
        // 检查是否匹配预定义的敏感属性模式
        for (String pattern : SENSITIVE_ATTRIBUTE_PATTERNS) {
            if (key.contains(pattern.toLowerCase())) {
                return true;
            }
        }
        
        // 检查配置中的敏感字段
        return tracingSensitiveFields.stream()
                .anyMatch(field -> key.contains(field.toLowerCase()));
    }
    
    /**
     * 检查是否为敏感事件属性
     */
    private boolean isSensitiveEventAttribute(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("token") || 
               lowerKey.contains("secret") ||
               lowerKey.contains("credential") ||
               lowerKey.contains("authorization") ||
               tracingSensitiveFields.contains(lowerKey);
    }
    
    /**
     * 检查是否为敏感日志字段
     */
    private boolean isSensitiveLogField(String key) {
        String lowerKey = key.toLowerCase();
        return lowerKey.contains("password") || 
               lowerKey.contains("token") || 
               lowerKey.contains("secret") ||
               lowerKey.contains("email") ||
               lowerKey.contains("phone") ||
               lowerKey.contains("id_card") ||
               tracingSensitiveFields.contains(lowerKey);
    }
    
    /**
     * 记录脱敏操作审计日志
     */
    private void recordSanitizationAudit(String dataType, String fieldName, String action, TracingContext context) {
        try {
            structuredLogger.logSanitization(
                String.format("%s.%s", dataType, fieldName),
                action,
                "tracing-sanitization",
                context
            );
        } catch (Exception e) {
            log.debug("记录脱敏审计日志失败", e);
        }
    }
    
    /**
     * 检查是否启用追踪脱敏
     */
    private boolean isTracingSanitizationEnabled() {
        return tracingConfiguration.getSecurity().getSanitization().isEnabled();
    }
    
    /**
     * 加载默认的追踪敏感字段
     */
    private void loadDefaultTracingSensitiveFields() {
        tracingSensitiveFields.addAll(tracingConfiguration.getLogging().getSensitiveFields());
        
        // 添加追踪特定的敏感字段
        tracingSensitiveFields.add("user_id");
        tracingSensitiveFields.add("email");
        tracingSensitiveFields.add("phone");
        tracingSensitiveFields.add("token");
        tracingSensitiveFields.add("password");
        tracingSensitiveFields.add("secret");
        tracingSensitiveFields.add("credential");
        tracingSensitiveFields.add("authorization");
    }
    
    /**
     * 加载追踪脱敏规则
     */
    private void loadTracingSanitizationRules() {
        // 从配置中加载追踪特定的脱敏规则
        tracingConfiguration.getSecurity().getSanitization().getAdditionalPatterns()
                .forEach(pattern -> tracingSanitizationRules.put(pattern, "mask"));
    }
    
    /**
     * 添加追踪敏感字段
     * 
     * @param field 敏感字段名称
     */
    public void addTracingSensitiveField(String field) {
        tracingSensitiveFields.add(field.toLowerCase());
        log.debug("添加追踪敏感字段: {}", field);
    }
    
    /**
     * 移除追踪敏感字段
     * 
     * @param field 敏感字段名称
     */
    public void removeTracingSensitiveField(String field) {
        tracingSensitiveFields.remove(field.toLowerCase());
        log.debug("移除追踪敏感字段: {}", field);
    }
    
    /**
     * 获取当前追踪敏感字段列表
     * 
     * @return 敏感字段集合
     */
    public Set<String> getTracingSensitiveFields() {
        return Set.copyOf(tracingSensitiveFields);
    }
}