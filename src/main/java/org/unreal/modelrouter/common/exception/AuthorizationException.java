package org.unreal.modelrouter.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 授权异常类
 * 用于处理权限不足相关的异常
 *
 * <p>⚠️ 错误码的单一事实来源是 {@link org.unreal.modelrouter.common.constants.ErrorCode}（issue #370 方案 A）。
 * 本类的三个码常量{@code INSUFFICIENT_PERMISSIONS} / {@code ACCESS_DENIED} / {@code FORBIDDEN_RESOURCE}
 * <b>全仓没有任何发出点</b>（其工厂方法只被单元测试调用）⇒ 未登记进注册表，这里保留字面量并显式标注；
 * 客户端不应据此写分支，处置见 issue #406。</p>
 */
public class AuthorizationException extends SecurityException {
    
    public static final String INSUFFICIENT_PERMISSIONS = "INSUFFICIENT_PERMISSIONS";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String FORBIDDEN_RESOURCE = "FORBIDDEN_RESOURCE";
    
    public AuthorizationException(final String message, final String errorCode) {
        super(message, errorCode, HttpStatus.FORBIDDEN);
    }
    
    public AuthorizationException(final String message, final Throwable cause, final String errorCode) {
        super(message, cause, errorCode, HttpStatus.FORBIDDEN);
    }
    
    /**
     * 创建权限不足异常
     */
    public static AuthorizationException insufficientPermissions(final String requiredPermission) {
        return new AuthorizationException(
            String.format("权限不足，需要权限: %s", requiredPermission), 
            INSUFFICIENT_PERMISSIONS
        );
    }
    
    /**
     * 创建访问被拒绝异常
     */
    public static AuthorizationException accessDenied(final String resource) {
        return new AuthorizationException(
            String.format("访问被拒绝，资源: %s", resource), 
            ACCESS_DENIED
        );
    }
    
    /**
     * 创建资源禁止访问异常
     */
    public static AuthorizationException resourceForbidden(final String resource) {
        return new AuthorizationException(
            String.format("禁止访问资源: %s", resource), 
            FORBIDDEN_RESOURCE
        );
    }
}