package org.unreal.modelrouter.common.exception;

import org.springframework.http.HttpStatus;
import org.unreal.modelrouter.common.constants.ErrorCode;

/**
 * 数据脱敏异常类
 * 用于处理数据脱敏过程中的异常
 *
 * <p>⚠️ 错误码的单一事实来源是 {@link ErrorCode}（issue #370 方案 A）：本类的码常量一律由它派生。
 * 例外：{@code RULE_COMPILATION_FAILED} <b>全仓没有任何发出点</b>（其工厂方法只被单元测试调用）
 * ⇒ 未登记进注册表，这里保留字面量并显式标注；客户端不应据此写分支，处置见 issue #406。</p>
 */
public class SanitizationException extends SecurityException {
    
    public static final String SANITIZATION_FAILED = ErrorCode.SANITIZATION_FAILED.code();
    public static final String INVALID_SANITIZATION_RULE = ErrorCode.INVALID_SANITIZATION_RULE.code();
    public static final String RULE_COMPILATION_FAILED = "RULE_COMPILATION_FAILED";
    public static final String CONTENT_PROCESSING_FAILED = ErrorCode.CONTENT_PROCESSING_FAILED.code();
    
    public SanitizationException(final String message, final String errorCode) {
        super(message, errorCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    public SanitizationException(final String message, final Throwable cause, final String errorCode) {
        super(message, cause, errorCode, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    /**
     * 创建脱敏失败异常
     */
    public static SanitizationException sanitizationFailed(final String reason) {
        return new SanitizationException(
            String.format("数据脱敏失败: %s", reason), 
            SANITIZATION_FAILED
        );
    }
    
    /**
     * 创建无效脱敏规则异常
     */
    public static SanitizationException invalidRule(final String ruleId, final String reason) {
        return new SanitizationException(
            String.format("无效的脱敏规则 [%s]: %s", ruleId, reason), 
            INVALID_SANITIZATION_RULE
        );
    }
    
    /**
     * 创建规则编译失败异常
     */
    public static SanitizationException ruleCompilationFailed(final String ruleId, final Throwable cause) {
        return new SanitizationException(
            String.format("脱敏规则编译失败 [%s]", ruleId), 
            cause,
            RULE_COMPILATION_FAILED
        );
    }
    
    /**
     * 创建内容处理失败异常
     */
    public static SanitizationException contentProcessingFailed(final String contentType, final Throwable cause) {
        return new SanitizationException(
            String.format("内容处理失败，类型: %s", contentType), 
            cause,
            CONTENT_PROCESSING_FAILED
        );
    }
}