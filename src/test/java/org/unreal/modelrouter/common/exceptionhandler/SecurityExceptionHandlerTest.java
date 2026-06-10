package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.dto.SecurityErrorResponse;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.common.exception.AuthorizationException;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.common.exception.SanitizationException;
import org.unreal.modelrouter.common.exception.SecurityException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityExceptionHandler 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SecurityExceptionHandlerTest {

    @InjectMocks
    private SecurityExceptionHandler handler;

    @Nested
    @DisplayName("AuthenticationException 处理测试")
    class AuthenticationExceptionTests {

        @Test
        @DisplayName("处理认证异常 - 默认401 UNAUTHORIZED")
        void testHandleAuthenticationException_Unauthorized() {
            // Arrange
            AuthenticationException ex = new AuthenticationException(
                    "Invalid credentials",
                    "AUTH_001"
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleAuthenticationException(ex);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(401, response.getBody().getStatus());
            assertEquals("Invalid credentials", response.getBody().getMessage());
            assertEquals("AUTH_001", response.getBody().getErrorCode());
            assertNotNull(response.getBody().getTimestamp());
        }

        @Test
        @DisplayName("处理认证异常 - 使用静态工厂方法")
        void testHandleAuthenticationException_FactoryMethod() {
            // Arrange
            AuthenticationException ex = AuthenticationException.invalidApiKey();

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleAuthenticationException(ex);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("INVALID_API_KEY", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("处理JWT过期异常")
        void testHandleAuthenticationException_JwtExpired() {
            // Arrange
            AuthenticationException ex = AuthenticationException.expiredJwtToken();

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleAuthenticationException(ex);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("EXPIRED_JWT_TOKEN", response.getBody().getErrorCode());
        }
    }

    @Nested
    @DisplayName("AuthorizationException 处理测试")
    class AuthorizationExceptionTests {

        @Test
        @DisplayName("处理授权异常 - 默认403 FORBIDDEN")
        void testHandleAuthorizationException_Forbidden() {
            // Arrange
            AuthorizationException ex = new AuthorizationException(
                    "Access denied to resource",
                    "AUTHZ_001"
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleAuthorizationException(ex);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(403, response.getBody().getStatus());
            assertEquals("Access denied to resource", response.getBody().getMessage());
            assertEquals("AUTHZ_001", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("处理授权异常 - 使用静态工厂方法")
        void testHandleAuthorizationException_FactoryMethod() {
            // Arrange
            AuthorizationException ex = AuthorizationException.insufficientPermissions("ADMIN");

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleAuthorizationException(ex);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertEquals("INSUFFICIENT_PERMISSIONS", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("处理资源禁止访问异常")
        void testHandleAuthorizationException_ResourceForbidden() {
            // Arrange
            AuthorizationException ex = AuthorizationException.resourceForbidden("/api/admin");

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleAuthorizationException(ex);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertEquals("RESOURCE_FORBIDDEN", response.getBody().getErrorCode());
        }
    }

    @Nested
    @DisplayName("SanitizationException 处理测试")
    class SanitizationExceptionTests {

        @Test
        @DisplayName("处理脱敏异常 - 隐藏敏感信息")
        void testHandleSanitizationException_HideSensitiveInfo() {
            // Arrange
            SanitizationException ex = new SanitizationException(
                    "Sensitive data exposed in field 'password'",
                    "SAN_001"
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleSanitizationException(ex);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            // 应该返回通用消息，不暴露具体的脱敏错误信息
            assertEquals("数据处理失败", response.getBody().getMessage());
            assertEquals("SAN_001", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("处理脱敏异常 - 使用静态工厂方法")
        void testHandleSanitizationException_FactoryMethod() {
            // Arrange
            SanitizationException ex = SanitizationException.sanitizationFailed("regex error");

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleSanitizationException(ex);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("SANITIZATION_FAILED", response.getBody().getErrorCode());
            assertEquals("数据处理失败", response.getBody().getMessage());
        }
    }

    @Nested
    @DisplayName("SecurityException 处理测试")
    class SecurityExceptionTests {

        @Test
        @DisplayName("处理通用安全异常 - 内部服务器错误")
        void testHandleSecurityException_InternalError() {
            // Arrange
            SecurityException ex = new SecurityException(
                    "Security violation detected",
                    "SEC_001",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleSecurityException(ex);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("Security violation detected", response.getBody().getMessage());
            assertEquals("SEC_001", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("处理通用安全异常 - 自定义状态")
        void testHandleSecurityException_CustomStatus() {
            // Arrange
            SecurityException ex = new SecurityException(
                    "Rate limit exceeded",
                    "SEC_RATE_LIMIT",
                    HttpStatus.TOO_MANY_REQUESTS
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleSecurityException(ex);

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertEquals(429, response.getBody().getStatus());
        }
    }

    @Nested
    @DisplayName("DownstreamServiceException 处理测试")
    class DownstreamServiceExceptionTests {

        @Test
        @DisplayName("处理下游服务异常 - 503 SERVICE_UNAVAILABLE")
        void testHandleDownstreamServiceException_ServiceUnavailable() {
            // Arrange
            DownstreamServiceException ex = new DownstreamServiceException(
                    "Backend service unavailable",
                    HttpStatus.SERVICE_UNAVAILABLE
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleDownstreamServiceException(ex);

            // Assert
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
            assertEquals(503, response.getBody().getStatus());
            assertEquals("Backend service unavailable", response.getBody().getMessage());
            assertEquals("DOWNSTREAM_SERVICE_ERROR", response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("处理下游服务异常 - 401 认证错误")
        void testHandleDownstreamServiceException_Unauthorized() {
            // Arrange
            DownstreamServiceException ex = new DownstreamServiceException(
                    "Authentication failed at downstream",
                    HttpStatus.UNAUTHORIZED
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleDownstreamServiceException(ex);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals(401, response.getBody().getStatus());
        }

        @Test
        @DisplayName("处理下游服务异常 - 500 内部错误")
        void testHandleDownstreamServiceException_InternalError() {
            // Arrange
            DownstreamServiceException ex = new DownstreamServiceException(
                    "Backend returned error",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleDownstreamServiceException(ex);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(500, response.getBody().getStatus());
        }

        @Test
        @DisplayName("处理下游服务异常 - 502 BAD_GATEWAY")
        void testHandleDownstreamServiceException_BadGateway() {
            // Arrange
            DownstreamServiceException ex = new DownstreamServiceException(
                    "Invalid response from upstream",
                    HttpStatus.BAD_GATEWAY
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleDownstreamServiceException(ex);

            // Assert
            assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
            assertEquals(502, response.getBody().getStatus());
        }
    }

    @Nested
    @DisplayName("响应结构验证测试")
    class ResponseStructureTests {

        @Test
        @DisplayName("验证响应包含所有必要字段")
        void testResponseContainsAllFields() {
            // Arrange
            AuthenticationException ex = new AuthenticationException(
                    "Test error",
                    "TEST_001"
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleAuthenticationException(ex);
            SecurityErrorResponse body = response.getBody();

            // Assert
            assertNotNull(body);
            assertNotNull(body.getTimestamp());
            assertNotNull(body.getStatus());
            assertNotNull(body.getError());
            assertNotNull(body.getMessage());
            assertNotNull(body.getErrorCode());
            assertNotNull(body.getPath());
        }

        @Test
        @DisplayName("验证HTTP状态码与响应体一致")
        void testHttpStatusCodeConsistency() {
            // Arrange
            AuthorizationException ex = new AuthorizationException(
                    "Test",
                    "TEST"
            );

            // Act
            ResponseEntity<SecurityErrorResponse> response = handler.handleAuthorizationException(ex);

            // Assert
            assertEquals(response.getStatusCode().value(), response.getBody().getStatus());
            assertEquals(HttpStatus.FORBIDDEN.getReasonPhrase(), response.getBody().getError());
        }
    }
}
