package org.unreal.modelrouter.monitor.monitoring.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.exception.AuthorizationException;
import org.unreal.modelrouter.exception.DownstreamServiceException;
import org.unreal.modelrouter.exception.SanitizationException;
import org.unreal.modelrouter.exception.SecurityAuthenticationException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ErrorCodeResolver 单元测试
 * 
 * @author JAiRouter Team
 * @since 1.9.1
 */
@DisplayName("ErrorCodeResolver 单元测试")
class ErrorCodeResolverTest {

    private ErrorCodeResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ErrorCodeResolver();
    }

    @Test
    @DisplayName("测试解析 null 异常的错误代码")
    void testResolveErrorCode_Null() {
        String errorCode = resolver.resolveErrorCode(null);
        assertEquals("UNK_000", errorCode);
    }

    @Test
    @DisplayName("测试解析认证异常的错误代码")
    void testResolveErrorCode_AuthenticationException() {
        AuthenticationException ex = new AuthenticationException("认证失败", "AUTH_001");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertEquals("AUTH_001", errorCode);
    }

    @Test
    @DisplayName("测试解析授权异常的错误代码")
    void testResolveErrorCode_AuthorizationException() {
        AuthorizationException ex = new AuthorizationException("授权失败", "AUTHZ_001");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertEquals("AUTHZ_001", errorCode);
    }

    @Test
    @DisplayName("测试解析安全认证异常的错误代码")
    void testResolveErrorCode_SecurityAuthenticationException() {
        SecurityAuthenticationException ex = new SecurityAuthenticationException("AUTH_002", "安全认证失败");
        
        // SecurityAuthenticationException 没有实现 getErrorCode 方法，所以会使用类名生成错误代码
        String errorCode = resolver.resolveErrorCode(ex);
        // 由于没有实现 SecurityException 接口，会生成基于类名的错误代码
        assertTrue(errorCode.startsWith("AUTH_") || errorCode.startsWith("SYS_"));
    }

    @Test
    @DisplayName("测试解析数据脱敏异常的错误代码")
    void testResolveErrorCode_SanitizationException() {
        SanitizationException ex = new SanitizationException("数据脱敏失败", "SAN_001");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertEquals("SAN_001", errorCode);
    }

    @Test
    @DisplayName("测试解析下游服务异常的错误代码")
    void testResolveErrorCode_DownstreamServiceException() {
        DownstreamServiceException ex = new DownstreamServiceException("下游服务失败",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertTrue(errorCode.startsWith("DOWN_") || errorCode.startsWith("SYS_"));
    }

    @Test
    @DisplayName("测试解析 Spring 响应状态异常的错误代码")
    void testResolveErrorCode_ResponseStatusException() {
        ResponseStatusException ex = new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "请求错误");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertTrue(errorCode.startsWith("VAL_"));
    }

    @Test
    @DisplayName("测试解析网络异常的错误代码")
    void testResolveErrorCode_ConnectException() {
        ConnectException ex = new ConnectException("连接失败");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertTrue(errorCode.startsWith("NET_"));
    }

    @Test
    @DisplayName("测试解析超时异常的错误代码")
    void testResolveErrorCode_SocketTimeoutException() {
        SocketTimeoutException ex = new SocketTimeoutException("连接超时");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertTrue(errorCode.startsWith("TIME_"));
    }

    @Test
    @DisplayName("测试解析通用超时异常的错误代码")
    void testResolveErrorCode_TimeoutException() {
        TimeoutException ex = new TimeoutException("操作超时");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertTrue(errorCode.startsWith("TIME_"));
    }

    @Test
    @DisplayName("测试解析非法参数异常的错误代码")
    void testResolveErrorCode_IllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("参数无效");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertTrue(errorCode.startsWith("VAL_"));
    }

    @Test
    @DisplayName("测试解析空指针异常的错误代码")
    void testResolveErrorCode_NullPointerException() {
        NullPointerException ex = new NullPointerException("空指针");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertTrue(errorCode.startsWith("SYS_"));
    }

    @Test
    @DisplayName("测试解析未知异常的错误代码")
    void testResolveErrorCode_UnknownException() {
        RuntimeException ex = new RuntimeException("未知错误");
        
        String errorCode = resolver.resolveErrorCode(ex);
        assertTrue(errorCode.startsWith("SYS_") || errorCode.startsWith("UNK_"));
    }

    @Test
    @DisplayName("测试解析错误分类 - null")
    void testResolveErrorCategory_Null() {
        ErrorCodeResolver.ErrorCategory category = resolver.resolveErrorCategory(null);
        assertEquals(ErrorCodeResolver.ErrorCategory.UNKNOWN, category);
    }

    @Test
    @DisplayName("测试解析错误分类 - 认证")
    void testResolveErrorCategory_Authentication() {
        AuthenticationException ex = new AuthenticationException("认证失败", "AUTH_001");
        
        ErrorCodeResolver.ErrorCategory category = resolver.resolveErrorCategory(ex);
        assertEquals(ErrorCodeResolver.ErrorCategory.AUTHENTICATION, category);
    }

    @Test
    @DisplayName("测试解析错误分类 - 授权")
    void testResolveErrorCategory_Authorization() {
        AuthorizationException ex = new AuthorizationException("授权失败", "AUTHZ_001");
        
        ErrorCodeResolver.ErrorCategory category = resolver.resolveErrorCategory(ex);
        assertEquals(ErrorCodeResolver.ErrorCategory.AUTHORIZATION, category);
    }

    @Test
    @DisplayName("测试解析错误分类 - 验证")
    void testResolveErrorCategory_Validation() {
        IllegalArgumentException ex = new IllegalArgumentException("参数无效");
        
        ErrorCodeResolver.ErrorCategory category = resolver.resolveErrorCategory(ex);
        assertEquals(ErrorCodeResolver.ErrorCategory.VALIDATION, category);
    }

    @Test
    @DisplayName("测试解析错误分类 - 网络")
    void testResolveErrorCategory_Network() {
        ConnectException ex = new ConnectException("连接失败");
        
        ErrorCodeResolver.ErrorCategory category = resolver.resolveErrorCategory(ex);
        assertEquals(ErrorCodeResolver.ErrorCategory.NETWORK, category);
    }

    @Test
    @DisplayName("测试解析错误分类 - 超时")
    void testResolveErrorCategory_Timeout() {
        SocketTimeoutException ex = new SocketTimeoutException("连接超时");
        
        ErrorCodeResolver.ErrorCategory category = resolver.resolveErrorCategory(ex);
        assertEquals(ErrorCodeResolver.ErrorCategory.TIMEOUT, category);
    }

    @Test
    @DisplayName("测试解析 HTTP 状态码 - null")
    void testResolveHttpStatus_Null() {
        String httpStatus = resolver.resolveHttpStatus(null);
        assertEquals("500", httpStatus);
    }

    @Test
    @DisplayName("测试解析 HTTP 状态码 - 认证异常")
    void testResolveHttpStatus_AuthenticationException() {
        AuthenticationException ex = new AuthenticationException("认证失败", "AUTH_001");
        
        String httpStatus = resolver.resolveHttpStatus(ex);
        assertEquals("401", httpStatus);
    }

    @Test
    @DisplayName("测试解析 HTTP 状态码 - 授权异常")
    void testResolveHttpStatus_AuthorizationException() {
        AuthorizationException ex = new AuthorizationException("授权失败", "AUTHZ_001");
        
        String httpStatus = resolver.resolveHttpStatus(ex);
        assertEquals("403", httpStatus);
    }

    @Test
    @DisplayName("测试解析 HTTP 状态码 - Spring 响应状态异常")
    void testResolveHttpStatus_ResponseStatusException() {
        ResponseStatusException ex = new ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "请求错误");
        
        String httpStatus = resolver.resolveHttpStatus(ex);
        assertEquals("400", httpStatus);
    }

    @Test
    @DisplayName("测试解析 HTTP 状态码 - 认证分类")
    void testResolveHttpStatus_AuthenticationCategory() {
        // 认证异常应该返回 401
        AuthenticationException ex = new AuthenticationException("认证失败", "AUTH_001");
        String httpStatus = resolver.resolveHttpStatus(ex);
        assertEquals("401", httpStatus);
    }

    @Test
    @DisplayName("测试获取分类显示名称")
    void testGetCategoryDisplayName() {
        assertEquals("认证错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.AUTHENTICATION));
        assertEquals("授权错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.AUTHORIZATION));
        assertEquals("验证错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.VALIDATION));
        assertEquals("下游服务错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.DOWNSTREAM));
        assertEquals("数据脱敏错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.SANITIZATION));
        assertEquals("系统错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.SYSTEM));
        assertEquals("网络错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.NETWORK));
        assertEquals("超时错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.TIMEOUT));
        assertEquals("限流错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.RATE_LIMIT));
        assertEquals("熔断错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.CIRCUIT_BREAKER));
        assertEquals("未知错误", resolver.getCategoryDisplayName(ErrorCodeResolver.ErrorCategory.UNKNOWN));
    }

    @Test
    @DisplayName("测试错误代码生成的一致性")
    void testErrorCodeGenerationConsistency() {
        RuntimeException ex1 = new RuntimeException("错误 1");
        RuntimeException ex2 = new RuntimeException("错误 2");
        
        // 相同异常类型应该生成相同的错误代码后缀
        String code1 = resolver.resolveErrorCode(ex1);
        String code2 = resolver.resolveErrorCode(ex2);
        
        assertEquals(code1, code2);
    }

    @Test
    @DisplayName("测试不同异常类型生成不同的错误代码")
    void testDifferentExceptionTypesGenerateDifferentCodes() {
        RuntimeException ex1 = new RuntimeException("错误");
        IllegalArgumentException ex2 = new IllegalArgumentException("参数错误");
        
        String code1 = resolver.resolveErrorCode(ex1);
        String code2 = resolver.resolveErrorCode(ex2);
        
        // 不同异常类型应该有不同的前缀
        assertNotEquals(code1, code2);
    }
}
