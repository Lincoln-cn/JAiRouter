package org.unreal.modelrouter.monitoring.error;

import org.springframework.stereotype.Component;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.exception.AuthorizationException;
import org.unreal.modelrouter.exception.DownstreamServiceException;
import org.unreal.modelrouter.exception.SanitizationException;
import org.unreal.modelrouter.exception.SecurityAuthenticationException;

import java.util.HashMap;
import java.util.Map;

/**
 * 错误代码解析器
 * 
 * 负责从异常对象中提取错误代码和错误分类信息，用于异常的 categorization 和统计。
 * 
 * @author JAiRouter Team
 * @since 1.9.1
 */
@Component
public class ErrorCodeResolver {

    /**
     * 错误分类枚举
     */
    public enum ErrorCategory {
        AUTHENTICATION,     // 认证错误
        AUTHORIZATION,      // 授权错误
        VALIDATION,         // 验证错误
        DOWNSTREAM,         // 下游服务错误
        SANITIZATION,       // 数据脱敏错误
        SYSTEM,             // 系统错误
        NETWORK,            // 网络错误
        TIMEOUT,            // 超时错误
        RATE_LIMIT,         // 限流错误
        CIRCUIT_BREAKER,    // 熔断错误
        UNKNOWN             // 未知错误
    }

    /**
     * 异常类型与错误分类的映射
     */
    private static final Map<String, ErrorCategory> EXCEPTION_CATEGORY_MAP = new HashMap<>();

    static {
        // 认证相关
        EXCEPTION_CATEGORY_MAP.put(AuthenticationException.class.getName(), ErrorCategory.AUTHENTICATION);
        EXCEPTION_CATEGORY_MAP.put(SecurityAuthenticationException.class.getName(), ErrorCategory.AUTHENTICATION);
        
        // 授权相关
        EXCEPTION_CATEGORY_MAP.put(AuthorizationException.class.getName(), ErrorCategory.AUTHORIZATION);
        
        // 数据脱敏
        EXCEPTION_CATEGORY_MAP.put(SanitizationException.class.getName(), ErrorCategory.SANITIZATION);
        
        // 下游服务
        EXCEPTION_CATEGORY_MAP.put(DownstreamServiceException.class.getName(), ErrorCategory.DOWNSTREAM);
        
        // Spring 常见异常
        EXCEPTION_CATEGORY_MAP.put("org.springframework.web.server.ResponseStatusException", ErrorCategory.VALIDATION);
        EXCEPTION_CATEGORY_MAP.put("org.springframework.web.server.ServerWebInputException", ErrorCategory.VALIDATION);
        EXCEPTION_CATEGORY_MAP.put("org.springframework.web.bind.MethodArgumentNotValidException", ErrorCategory.VALIDATION);
        EXCEPTION_CATEGORY_MAP.put("org.springframework.web.bind.MissingServletRequestParameterException", ErrorCategory.VALIDATION);
        EXCEPTION_CATEGORY_MAP.put("org.springframework.web.HttpRequestMethodNotSupportedException", ErrorCategory.VALIDATION);
        
        // 网络相关
        EXCEPTION_CATEGORY_MAP.put("java.net.ConnectException", ErrorCategory.NETWORK);
        EXCEPTION_CATEGORY_MAP.put("java.net.SocketTimeoutException", ErrorCategory.TIMEOUT);
        EXCEPTION_CATEGORY_MAP.put("java.net.UnknownHostException", ErrorCategory.NETWORK);
        EXCEPTION_CATEGORY_MAP.put("java.io.IOException", ErrorCategory.NETWORK);
        
        // 通用异常
        EXCEPTION_CATEGORY_MAP.put("java.lang.IllegalArgumentException", ErrorCategory.VALIDATION);
        EXCEPTION_CATEGORY_MAP.put("java.lang.IllegalStateException", ErrorCategory.SYSTEM);
        EXCEPTION_CATEGORY_MAP.put("java.lang.NullPointerException", ErrorCategory.SYSTEM);
        EXCEPTION_CATEGORY_MAP.put("java.util.concurrent.TimeoutException", ErrorCategory.TIMEOUT);
    }

    /**
     * 错误代码前缀映射
     */
    private static final Map<ErrorCategory, String> CATEGORY_CODE_PREFIX_MAP = new HashMap<>();

    static {
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.AUTHENTICATION, "AUTH_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.AUTHORIZATION, "AUTHZ_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.VALIDATION, "VAL_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.DOWNSTREAM, "DOWN_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.SANITIZATION, "SAN_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.SYSTEM, "SYS_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.NETWORK, "NET_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.TIMEOUT, "TIME_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.RATE_LIMIT, "RATE_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.CIRCUIT_BREAKER, "CB_");
        CATEGORY_CODE_PREFIX_MAP.put(ErrorCategory.UNKNOWN, "UNK_");
    }

    /**
     * 解析错误代码
     * 
     * @param throwable 异常对象
     * @return 错误代码
     */
    public String resolveErrorCode(Throwable throwable) {
        if (throwable == null) {
            return "UNK_000";
        }

        // 如果异常本身有错误代码，直接使用
        if (throwable instanceof org.unreal.modelrouter.exception.SecurityException) {
            return ((org.unreal.modelrouter.exception.SecurityException) throwable).getErrorCode();
        }

        // 根据异常类型生成错误代码
        ErrorCategory category = resolveErrorCategory(throwable);
        String simpleName = throwable.getClass().getSimpleName();
        
        // 生成错误代码：分类前缀 + 异常类名哈希
        String prefix = CATEGORY_CODE_PREFIX_MAP.getOrDefault(category, "UNK_");
        String codeSuffix = generateCodeSuffix(throwable.getClass().getName());
        
        return prefix + codeSuffix;
    }

    /**
     * 解析错误分类
     * 
     * @param throwable 异常对象
     * @return 错误分类
     */
    public ErrorCategory resolveErrorCategory(Throwable throwable) {
        if (throwable == null) {
            return ErrorCategory.UNKNOWN;
        }

        String exceptionClassName = throwable.getClass().getName();
        
        // 直接匹配
        ErrorCategory category = EXCEPTION_CATEGORY_MAP.get(exceptionClassName);
        if (category != null) {
            return category;
        }

        // 检查父类
        Class<?> superClass = throwable.getClass().getSuperclass();
        while (superClass != null && superClass != Object.class) {
            category = EXCEPTION_CATEGORY_MAP.get(superClass.getName());
            if (category != null) {
                return category;
            }
            superClass = superClass.getSuperclass();
        }

        // 根据包名判断
        if (exceptionClassName.contains(".security.")) {
            return ErrorCategory.AUTHENTICATION;
        } else if (exceptionClassName.contains(".auth.")) {
            return ErrorCategory.AUTHENTICATION;
        } else if (exceptionClassName.contains(".validation.")) {
            return ErrorCategory.VALIDATION;
        } else if (exceptionClassName.contains("Timeout")) {
            return ErrorCategory.TIMEOUT;
        } else if (exceptionClassName.contains("Network") || 
                   exceptionClassName.contains("Connect") ||
                   exceptionClassName.contains("Socket")) {
            return ErrorCategory.NETWORK;
        } else if (exceptionClassName.contains("RateLimit") ||
                   exceptionClassName.contains("Throttle")) {
            return ErrorCategory.RATE_LIMIT;
        } else if (exceptionClassName.contains("CircuitBreaker")) {
            return ErrorCategory.CIRCUIT_BREAKER;
        }

        // 默认系统错误
        return ErrorCategory.SYSTEM;
    }

    /**
     * 解析 HTTP 状态码
     * 
     * @param throwable 异常对象
     * @return HTTP 状态码字符串
     */
    public String resolveHttpStatus(Throwable throwable) {
        if (throwable == null) {
            return "500";
        }

        // 安全异常自带 HTTP 状态
        if (throwable instanceof org.unreal.modelrouter.exception.SecurityException) {
            return String.valueOf(((org.unreal.modelrouter.exception.SecurityException) throwable)
                    .getHttpStatus().value());
        }

        // Spring 响应状态异常
        if (throwable instanceof org.springframework.web.server.ResponseStatusException) {
            return String.valueOf(((org.springframework.web.server.ResponseStatusException) throwable)
                    .getStatusCode().value());
        }

        // 根据异常类型推断
        ErrorCategory category = resolveErrorCategory(throwable);
        return switch (category) {
            case AUTHENTICATION -> "401";
            case AUTHORIZATION -> "403";
            case VALIDATION -> "400";
            case TIMEOUT -> "504";
            case NETWORK -> "502";
            case DOWNSTREAM -> "503";
            default -> "500";
        };
    }

    /**
     * 生成错误代码后缀
     * 
     * @param className 异常类全名
     * @return 错误代码后缀（6 位数字）
     */
    private String generateCodeSuffix(String className) {
        // 使用类名的哈希值生成 6 位数字代码
        int hash = className.hashCode();
        // 确保为正数
        hash = Math.abs(hash);
        // 取后 6 位
        return String.format("%06d", hash % 1000000);
    }

    /**
     * 获取错误分类的显示名称
     * 
     * @param category 错误分类
     * @return 显示名称
     */
    public String getCategoryDisplayName(ErrorCategory category) {
        return switch (category) {
            case AUTHENTICATION -> "认证错误";
            case AUTHORIZATION -> "授权错误";
            case VALIDATION -> "验证错误";
            case DOWNSTREAM -> "下游服务错误";
            case SANITIZATION -> "数据脱敏错误";
            case SYSTEM -> "系统错误";
            case NETWORK -> "网络错误";
            case TIMEOUT -> "超时错误";
            case RATE_LIMIT -> "限流错误";
            case CIRCUIT_BREAKER -> "熔断错误";
            case UNKNOWN -> "未知错误";
        };
    }
}
