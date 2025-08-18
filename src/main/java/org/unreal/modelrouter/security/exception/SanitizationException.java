package org.unreal.modelrouter.security.exception;

import org.springframework.http.HttpStatus;

/**
 * 数据脱敏异常类
 * 用于处理数据脱敏过程中的异常
 */
public class SanitizationException extends SecurityException {
    
    public static final String SANITIZATION_FAILED = "SANITIZATION_FAILED";
    public static final String INVALID_SANITIZATION_RULE = "INVALID_SANITIZATION_RULE";
    public static final String RULE_COMPILATION_FAILED = "RULE_COMPILATION_FAILED";
    public static final String CONTENT_PROCESSING_FAILED = "CONTENT_PROCESSING_FAILED";
    
    public SanitizationException(String message, String errorCode) {
        super(message, errorCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    public SanitizationException(String message, Throwable cause, String errorCode) {
        super(message, cause, errorCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 创建脱敏失败异常
     */
    public static SanitizationException sanitizationFailed(String reason) {
        return new SanitizationException(
            String.format("数据脱敏失败: %s", reason), 
            SANITIZATION_FAILED
        );
    }
    
    /**
     * 创建无效脱敏规则异常
     */
    public static SanitizationException invalidRule(String ruleId, String reason) {
        return new SanitizationException(
            String.format("无效的脱敏规则 [%s]: %s", ruleId, reason), 
            INVALID_SANITIZATION_RULE
        );
    }
    
    /**
     * 创建规则编译失败异常
     */
    public static SanitizationException ruleCompilationFailed(String ruleId, Throwable cause) {
        return new SanitizationException(
            String.format("脱敏规则编译失败 [%s]", ruleId), 
            cause,
            RULE_COMPILATION_FAILED
        );
    }
    
    /**
     * 创建内容处理失败异常
     */
    public static SanitizationException contentProcessingFailed(String contentType, Throwable cause) {
        return new SanitizationException(
            String.format("内容处理失败，类型: %s", contentType), 
            cause,
            CONTENT_PROCESSING_FAILED
        );
    }
}