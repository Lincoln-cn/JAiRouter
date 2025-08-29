package org.unreal.modelrouter.monitoring.error;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.ErrorTrackerProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 异常堆栈脱敏器
 * 
 * 负责对异常堆栈信息进行脱敏处理，保护敏感信息不被泄露。
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class StackTraceSanitizer {
    
    private final ErrorTrackerProperties.SanitizationConfig config;
    private final List<Pattern> sensitivePatterns;
    
    public StackTraceSanitizer(ErrorTrackerProperties.SanitizationConfig config) {
        this.config = config;
        this.sensitivePatterns = compileSensitivePatterns();
    }
    
    /**
     * 脱敏异常对象
     * 
     * @param throwable 原始异常
     * @return 脱敏后的异常信息
     */
    public SanitizedThrowable sanitize(Throwable throwable) {
        if (!config.isEnabled() || throwable == null) {
            return new SanitizedThrowable(throwable);
        }
        
        try {
            return new SanitizedThrowable(
                throwable.getClass().getName(),
                sanitizeMessage(throwable.getMessage()),
                sanitizeStackTrace(throwable.getStackTrace()),
                throwable.getCause() != null ? sanitize(throwable.getCause()) : null
            );
        } catch (Exception e) {
            log.debug("脱敏异常失败，返回原始信息", e);
            return new SanitizedThrowable(throwable);
        }
    }
    
    /**
     * 脱敏异常消息
     * 
     * @param message 原始消息
     * @return 脱敏后的消息
     */
    private String sanitizeMessage(String message) {
        if (message == null) {
            return null;
        }
        
        String sanitized = message;
        
        // 使用正则表达式脱敏敏感信息
        for (Pattern pattern : sensitivePatterns) {
            sanitized = pattern.matcher(sanitized).replaceAll("***");
        }
        
        return sanitized;
    }
    
    /**
     * 脱敏堆栈跟踪
     * 
     * @param stackTrace 原始堆栈跟踪
     * @return 脱敏后的堆栈跟踪
     */
    private StackTraceElement[] sanitizeStackTrace(StackTraceElement[] stackTrace) {
        if (stackTrace == null) {
            return null;
        }
        
        List<StackTraceElement> sanitizedStack = new ArrayList<>();
        int depth = 0;
        
        for (StackTraceElement element : stackTrace) {
            // 检查深度限制
            if (depth >= config.getMaxStackDepth()) {
                sanitizedStack.add(new StackTraceElement(
                    "...", "more", "StackTruncated.java", -1
                ));
                break;
            }
            
            String className = element.getClassName();
            
            // 跳过排除的包
            if (shouldExcludePackage(className)) {
                continue;
            }
            
            // 脱敏敏感包
            if (shouldSanitizePackage(className)) {
                sanitizedStack.add(new StackTraceElement(
                    "***SANITIZED***", "***", "***", -1
                ));
            } else {
                sanitizedStack.add(element);
            }
            
            depth++;
        }
        
        return sanitizedStack.toArray(new StackTraceElement[0]);
    }
    
    /**
     * 检查是否应该排除包
     * 
     * @param className 类名
     * @return 是否排除
     */
    private boolean shouldExcludePackage(String className) {
        return config.getExcludedPackages().stream()
            .anyMatch(className::startsWith);
    }
    
    /**
     * 检查是否应该脱敏包
     * 
     * @param className 类名
     * @return 是否脱敏
     */
    private boolean shouldSanitizePackage(String className) {
        return config.getSensitivePackages().stream()
            .anyMatch(className::startsWith);
    }
    
    /**
     * 编译敏感信息正则表达式
     * 
     * @return 编译后的正则表达式列表
     */
    private List<Pattern> compileSensitivePatterns() {
        List<Pattern> patterns = new ArrayList<>();
        
        // 为每个敏感字段创建正则表达式
        for (String field : config.getSensitiveFields()) {
            // 匹配 field=value 或 field:value 格式
            patterns.add(Pattern.compile(
                "(?i)" + Pattern.quote(field) + "\\s*[=:]\\s*[^\\s,;}]+",
                Pattern.CASE_INSENSITIVE
            ));
        }
        
        // 添加一些通用的敏感信息模式
        patterns.add(Pattern.compile("Bearer\\s+[\\w\\-\\.]+", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\b[A-Za-z0-9+/]{20,}={0,2}\\b")); // Base64编码
        patterns.add(Pattern.compile("\\b[0-9a-fA-F]{32,}\\b")); // 十六进制字符串（可能是哈希或密钥）
        
        return patterns;
    }
    
    /**
     * 脱敏后的异常信息
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SanitizedThrowable {
        private String className;
        private String message;
        private StackTraceElement[] stackTrace;
        private SanitizedThrowable cause;
        
        /**
         * 从原始异常创建（不脱敏）
         * 
         * @param throwable 原始异常
         */
        public SanitizedThrowable(Throwable throwable) {
            if (throwable != null) {
                this.className = throwable.getClass().getName();
                this.message = throwable.getMessage();
                this.stackTrace = throwable.getStackTrace();
                this.cause = throwable.getCause() != null ? new SanitizedThrowable(throwable.getCause()) : null;
            }
        }
        
        /**
         * 获取简化的字符串表示
         * 
         * @return 简化的异常信息
         */
        public String toSimpleString() {
            return className + (message != null ? ": " + message : "");
        }
        
        /**
         * 获取完整的字符串表示
         * 
         * @return 完整的异常信息
         */
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(className);
            if (message != null) {
                sb.append(": ").append(message);
            }
            
            if (stackTrace != null) {
                for (StackTraceElement element : stackTrace) {
                    sb.append("\n\tat ").append(element);
                }
            }
            
            if (cause != null) {
                sb.append("\nCaused by: ").append(cause.toString());
            }
            
            return sb.toString();
        }
    }
}