package org.unreal.modelrouter.common.exceptionhandler;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.DispatcherHandler;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebHandler;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.unreal.modelrouter.common.dto.SecurityErrorResponse;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.common.exception.AuthorizationException;
import org.unreal.modelrouter.common.exception.DownstreamServiceException;
import org.unreal.modelrouter.common.exception.SanitizationException;
import org.unreal.modelrouter.common.exception.SecurityException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SecurityExceptionHandler 单元测试
 *
 * <p>v3.1 PR-4d.1：5 个 {@code @ExceptionHandler} 增加 {@link ServerWebExchange} 入参并按路径分支
 * （{@code /v1/**} 走 {@link V1ErrorBodyMapper} 的客户端协议形状，其它路径沿用
 * {@link SecurityErrorResponse}）。因此原有 16 处
 * {@code ResponseEntity<SecurityErrorResponse> response = handler.handleX(ex)} 形式的断言改为
 * 「新签名 + 显式取 {@code SecurityErrorResponse} 体」，断言语义（状态码 / 消息 / errorCode /
 * timestamp / path）逐条保留；{@code /v1} 路径新增协议形状断言。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@ExtendWith(MockitoExtension.class)
class SecurityExceptionHandlerTest {

    /**
     * 非 {@code /v1} 路径（默认交换对象，用于验证既有 {@link SecurityErrorResponse} 行为不变）.
     */
    private static final String LEGACY_PATH = "/api/security";

    /**
     * Anthropic 面路径.
     */
    private static final String ANTHROPIC_PATH = "/v1/messages";

    /**
     * OpenAI 面路径.
     */
    private static final String OPENAI_PATH = "/v1/chat/completions";

    /**
     * 控制台面探针路径（非 {@code /v1}，用于验证既有 {@link SecurityErrorResponse} 行为不变）.
     */
    private static final String PROBE_API_PATH = "/api/v1/security-probe";

    /**
     * 单测超时.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @InjectMocks
    private SecurityExceptionHandler handler;

    private MockServerWebExchange exchange;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        exchange = exchangeOf(LEGACY_PATH);
    }

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
            ResponseEntity<?> response = handler.handleAuthenticationException(ex, exchange);
            SecurityErrorResponse body = legacyBody(response);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertNotNull(body);
            assertEquals(401, body.getStatus());
            assertEquals("Invalid credentials", body.getMessage());
            assertEquals("AUTH_001", body.getErrorCode());
            assertNotNull(body.getTimestamp());
        }

        @Test
        @DisplayName("处理认证异常 - 使用静态工厂方法")
        void testHandleAuthenticationException_FactoryMethod() {
            // Arrange
            AuthenticationException ex = AuthenticationException.invalidApiKey();

            // Act
            ResponseEntity<?> response = handler.handleAuthenticationException(ex, exchange);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("INVALID_API_KEY", legacyBody(response).getErrorCode());
        }

        @Test
        @DisplayName("处理JWT过期异常")
        void testHandleAuthenticationException_JwtExpired() {
            // Arrange
            AuthenticationException ex = AuthenticationException.expiredJwtToken();

            // Act
            ResponseEntity<?> response = handler.handleAuthenticationException(ex, exchange);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals("EXPIRED_JWT_TOKEN", legacyBody(response).getErrorCode());
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
            ResponseEntity<?> response = handler.handleAuthorizationException(ex, exchange);
            SecurityErrorResponse body = legacyBody(response);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertNotNull(body);
            assertEquals(403, body.getStatus());
            assertEquals("Access denied to resource", body.getMessage());
            assertEquals("AUTHZ_001", body.getErrorCode());
        }

        @Test
        @DisplayName("处理授权异常 - 使用静态工厂方法")
        void testHandleAuthorizationException_FactoryMethod() {
            // Arrange
            AuthorizationException ex = AuthorizationException.insufficientPermissions("ADMIN");

            // Act
            ResponseEntity<?> response = handler.handleAuthorizationException(ex, exchange);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertEquals("INSUFFICIENT_PERMISSIONS", legacyBody(response).getErrorCode());
        }

        @Test
        @DisplayName("处理资源禁止访问异常")
        void testHandleAuthorizationException_ResourceForbidden() {
            // Arrange
            AuthorizationException ex = AuthorizationException.resourceForbidden("/api/admin");

            // Act
            ResponseEntity<?> response = handler.handleAuthorizationException(ex, exchange);

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            assertEquals("RESOURCE_FORBIDDEN", legacyBody(response).getErrorCode());
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
            ResponseEntity<?> response = handler.handleSanitizationException(ex, exchange);
            SecurityErrorResponse body = legacyBody(response);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            // 应该返回通用消息，不暴露具体的脱敏错误信息
            assertEquals("数据处理失败", body.getMessage());
            assertEquals("SAN_001", body.getErrorCode());
        }

        @Test
        @DisplayName("处理脱敏异常 - 使用静态工厂方法")
        void testHandleSanitizationException_FactoryMethod() {
            // Arrange
            SanitizationException ex = SanitizationException.sanitizationFailed("regex error");

            // Act
            ResponseEntity<?> response = handler.handleSanitizationException(ex, exchange);
            SecurityErrorResponse body = legacyBody(response);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("SANITIZATION_FAILED", body.getErrorCode());
            assertEquals("数据处理失败", body.getMessage());
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
            ResponseEntity<?> response = handler.handleSecurityException(ex, exchange);
            SecurityErrorResponse body = legacyBody(response);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals("Security violation detected", body.getMessage());
            assertEquals("SEC_001", body.getErrorCode());
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
            ResponseEntity<?> response = handler.handleSecurityException(ex, exchange);

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            assertEquals(429, legacyBody(response).getStatus());
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
            ResponseEntity<?> response = handler.handleDownstreamServiceException(ex, exchange);
            SecurityErrorResponse body = legacyBody(response);

            // Assert
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
            assertEquals(503, body.getStatus());
            assertEquals("Backend service unavailable", body.getMessage());
            assertEquals("DOWNSTREAM_SERVICE_ERROR", body.getErrorCode());
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
            ResponseEntity<?> response = handler.handleDownstreamServiceException(ex, exchange);

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            assertEquals(401, legacyBody(response).getStatus());
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
            ResponseEntity<?> response = handler.handleDownstreamServiceException(ex, exchange);

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            assertEquals(500, legacyBody(response).getStatus());
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
            ResponseEntity<?> response = handler.handleDownstreamServiceException(ex, exchange);

            // Assert
            assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
            assertEquals(502, legacyBody(response).getStatus());
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
            ResponseEntity<?> response = handler.handleAuthenticationException(ex, exchange);
            SecurityErrorResponse body = legacyBody(response);

            // Assert
            assertNotNull(body);
            assertNotNull(body.getTimestamp());
            // 基本类型 int 的 getter 不可能为 null，改断言状态码与 HTTP 状态一致（原断言恒真）
            assertEquals(response.getStatusCode().value(), body.getStatus());
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
            ResponseEntity<?> response = handler.handleAuthorizationException(ex, exchange);
            SecurityErrorResponse body = legacyBody(response);

            // Assert
            assertEquals(response.getStatusCode().value(), body.getStatus());
            assertEquals(HttpStatus.FORBIDDEN.getReasonPhrase(), body.getError());
        }
    }

    @Nested
    @DisplayName("v3.1 PR-4d.1: /v1/** 客户端协议形状")
    class V1ProtocolBodyTests {

        @Test
        @DisplayName("/v1/messages + 认证异常 → Anthropic 形状 + authentication_error + 状态码 401")
        void anthropicMessagesAuthentication_shouldRenderAnthropicBody() {
            // Arrange
            final AuthenticationException ex = new AuthenticationException("Invalid credentials", "AUTH_001");

            // Act
            final ResponseEntity<?> response = handler.handleAuthenticationException(ex, exchangeOf(ANTHROPIC_PATH));

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), "状态码必须保持");
            assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
            final Map<?, ?> body = mapBody(response);
            assertEquals(List.of("type", "error"), new ArrayList<>(body.keySet()),
                    "Anthropic 错误体只应有 type + error: " + body);
            assertEquals("error", body.get("type"));
            final Map<?, ?> error = errorNode(body);
            assertEquals("authentication_error", error.get("type"));
            assertEquals("Invalid credentials", error.get("message"), "必须是原错误消息");
            assertFalse(error.containsKey("code"), "Anthropic 面不带 code 字段: " + body);
        }

        @Test
        @DisplayName("/v1/messages/count_tokens + 授权异常 → 同属 Anthropic 面 + permission_error")
        void anthropicCountTokensAuthorization_shouldRenderAnthropicBody() {
            // Arrange
            final AuthorizationException ex = AuthorizationException.insufficientPermissions("ADMIN");

            // Act
            final ResponseEntity<?> response =
                    handler.handleAuthorizationException(ex, exchangeOf("/v1/messages/count_tokens"));

            // Assert
            assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
            final Map<?, ?> error = errorNode(mapBody(response));
            assertEquals("permission_error", error.get("type"));
            assertFalse(error.containsKey("code"), "Anthropic 面不带 code 字段");
        }

        @Test
        @DisplayName("/v1/messages + 安全异常 429 → rate_limit_error + 状态码 429")
        void anthropicMessagesRateLimit_shouldRenderAnthropicBody() {
            // Arrange
            final SecurityException ex = new SecurityException(
                    "Rate limit exceeded", "SEC_RATE_LIMIT", HttpStatus.TOO_MANY_REQUESTS);

            // Act
            final ResponseEntity<?> response = handler.handleSecurityException(ex, exchangeOf(ANTHROPIC_PATH));

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            final Map<?, ?> error = errorNode(mapBody(response));
            assertEquals("rate_limit_error", error.get("type"));
            assertEquals("Rate limit exceeded", error.get("message"));
        }

        @Test
        @DisplayName("/v1/messages + 脱敏异常 → api_error（500）+ 消息不泄露细节")
        void anthropicMessagesSanitization_shouldHideSensitiveInfo() {
            // Arrange
            final SanitizationException ex = new SanitizationException(
                    "Sensitive data exposed in field 'password'", "SAN_001");

            // Act
            final ResponseEntity<?> response = handler.handleSanitizationException(ex, exchangeOf(ANTHROPIC_PATH));

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            final Map<?, ?> error = errorNode(mapBody(response));
            assertEquals("api_error", error.get("type"));
            assertEquals("数据处理失败", error.get("message"), "协议面同样不得暴露脱敏细节");
        }

        @Test
        @DisplayName("/v1/messages + 下游 503 → api_error；401 → authentication_error（状态码原样）")
        void anthropicMessagesDownstream_shouldMapByFinalStatus() {
            // Arrange
            final DownstreamServiceException unavailable = new DownstreamServiceException(
                    "Backend service unavailable", HttpStatus.SERVICE_UNAVAILABLE);
            final DownstreamServiceException unauthorized = new DownstreamServiceException(
                    "Authentication failed at downstream", HttpStatus.UNAUTHORIZED);

            // Act
            final ResponseEntity<?> unavailableResponse =
                    handler.handleDownstreamServiceException(unavailable, exchangeOf(ANTHROPIC_PATH));
            final ResponseEntity<?> unauthorizedResponse =
                    handler.handleDownstreamServiceException(unauthorized, exchangeOf(ANTHROPIC_PATH));

            // Assert
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailableResponse.getStatusCode());
            assertEquals("api_error", errorNode(mapBody(unavailableResponse)).get("type"));
            assertEquals(HttpStatus.UNAUTHORIZED, unauthorizedResponse.getStatusCode());
            assertEquals("authentication_error", errorNode(mapBody(unauthorizedResponse)).get("type"));
        }

        @Test
        @DisplayName("/v1/chat/completions + 认证异常 → OpenAI 形状 {error:{message,type,code}}")
        void openAiChatCompletionsAuthentication_shouldRenderOpenAiBody() {
            // Arrange
            final AuthenticationException ex = new AuthenticationException("Invalid credentials", "AUTH_001");

            // Act
            final ResponseEntity<?> response = handler.handleAuthenticationException(ex, exchangeOf(OPENAI_PATH));

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(), "状态码必须保持");
            final Map<?, ?> body = mapBody(response);
            assertEquals(List.of("error"), new ArrayList<>(body.keySet()), "OpenAI 错误体只应有 error: " + body);
            final Map<?, ?> error = errorNode(body);
            assertEquals(List.of("message", "type", "code"), new ArrayList<>(error.keySet()),
                    "OpenAI error 应含 message/type/code: " + body);
            assertEquals("Invalid credentials", error.get("message"));
            assertEquals("authentication_error", error.get("type"));
            assertEquals("AUTH_001", error.get("code"), "code 应保留原 errorCode");
        }

        @Test
        @DisplayName("/v1/chat/completions + 脱敏异常 → OpenAI 形状 api_error（500）+ code 保留")
        void openAiChatCompletionsSanitization_shouldRenderOpenAiBody() {
            // Arrange
            final SanitizationException ex = new SanitizationException("Sensitive data", "SAN_001");

            // Act
            final ResponseEntity<?> response = handler.handleSanitizationException(ex, exchangeOf(OPENAI_PATH));

            // Assert
            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            final Map<?, ?> error = errorNode(mapBody(response));
            assertEquals("api_error", error.get("type"));
            assertEquals("SAN_001", error.get("code"));
            assertEquals("数据处理失败", error.get("message"));
        }

        @Test
        @DisplayName("/v1/chat/completions + 下游 401 → authentication_error + DOWNSTREAM_SERVICE_ERROR")
        void openAiChatCompletionsDownstream401_shouldRenderOpenAiBody() {
            // Arrange
            final DownstreamServiceException ex = new DownstreamServiceException(
                    "Authentication failed at downstream", HttpStatus.UNAUTHORIZED);

            // Act
            final ResponseEntity<?> response =
                    handler.handleDownstreamServiceException(ex, exchangeOf(OPENAI_PATH));

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            final Map<?, ?> error = errorNode(mapBody(response));
            assertEquals("authentication_error", error.get("type"));
            assertEquals("DOWNSTREAM_SERVICE_ERROR", error.get("code"));
        }

        @Test
        @DisplayName("回归 /api/v1/chat/completions + 认证异常 → SecurityErrorResponse 不变")
        void apiPathAuthentication_shouldKeepSecurityErrorResponse() {
            // Arrange
            final AuthenticationException ex = new AuthenticationException("Invalid credentials", "AUTH_001");

            // Act
            final ResponseEntity<?> response =
                    handler.handleAuthenticationException(ex, exchangeOf("/api/v1/chat/completions"));

            // Assert
            assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
            final SecurityErrorResponse body = legacyBody(response);
            assertEquals(401, body.getStatus());
            assertEquals("Unauthorized", body.getError());
            assertEquals("Invalid credentials", body.getMessage());
            assertEquals("AUTH_001", body.getErrorCode());
            assertEquals("/api/security", body.getPath(), "既有 path 字段语义不变");
            assertNotNull(body.getTimestamp());
        }

        @Test
        @DisplayName("回归 /api/security + 安全异常 429 → SecurityErrorResponse 不变")
        void apiPathSecurityException_shouldKeepSecurityErrorResponse() {
            // Arrange
            final SecurityException ex = new SecurityException(
                    "Rate limit exceeded", "SEC_RATE_LIMIT", HttpStatus.TOO_MANY_REQUESTS);

            // Act
            final ResponseEntity<?> response = handler.handleSecurityException(ex, exchangeOf(LEGACY_PATH));

            // Assert
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
            final SecurityErrorResponse body = legacyBody(response);
            assertEquals(429, body.getStatus());
            assertEquals("Too Many Requests", body.getError());
            assertEquals("SEC_RATE_LIMIT", body.getErrorCode());
        }
    }

    @Nested
    @DisplayName("真实 WebFlux 组合：advice 新签名端到端可用")
    class RealWebFluxAdviceTests {

        @Test
        @DisplayName("注解控制器内上抛安全异常 → /v1 两种协议形状 + /api 既有形状（线上 JSON）")
        void realAdviceDispatch_shouldRenderProtocolBodies() throws Exception {
            try (AnnotationConfigApplicationContext context =
                         new AnnotationConfigApplicationContext(ProbeConfig.class)) {
                final HttpHandler httpHandler = WebHttpHandlerBuilder.applicationContext(context).build();

                final MockServerWebExchange anthropic = dispatchTo(httpHandler, ANTHROPIC_PATH);
                assertEquals(HttpStatus.UNAUTHORIZED, anthropic.getResponse().getStatusCode());
                assertEquals("application/json",
                        anthropic.getResponse().getHeaders().getFirst("Content-Type"));
                final JsonNode anthropicJson = singleJsonObject(bodyOf(anthropic));
                assertEquals("error", anthropicJson.path("type").asText(), "线上 JSON: " + anthropicJson);
                assertEquals("authentication_error", anthropicJson.path("error").path("type").asText());
                assertEquals("Invalid credentials", anthropicJson.path("error").path("message").asText());
                assertFalse(anthropicJson.path("error").has("code"), "Anthropic 面不带 code: " + anthropicJson);
                assertFalse(anthropicJson.has("errorCode"), "不得再输出 SecurityErrorResponse 字段: " + anthropicJson);

                final MockServerWebExchange openAi = dispatchTo(httpHandler, OPENAI_PATH);
                assertEquals(HttpStatus.UNAUTHORIZED, openAi.getResponse().getStatusCode());
                final JsonNode openAiJson = singleJsonObject(bodyOf(openAi));
                assertEquals("authentication_error", openAiJson.path("error").path("type").asText(),
                        "线上 JSON: " + openAiJson);
                assertEquals("AUTH_001", openAiJson.path("error").path("code").asText());
                assertFalse(openAiJson.has("errorCode"), "不得再输出 SecurityErrorResponse 字段: " + openAiJson);

                final MockServerWebExchange api = dispatchTo(httpHandler, PROBE_API_PATH);
                assertEquals(HttpStatus.UNAUTHORIZED, api.getResponse().getStatusCode());
                final JsonNode apiJson = singleJsonObject(bodyOf(api));
                assertEquals("AUTH_001", apiJson.path("errorCode").asText(),
                        "/api 面必须仍是 SecurityErrorResponse: " + apiJson);
                assertTrue(apiJson.path("error").isTextual(),
                        "/api 面的 error 仍是状态描述文本: " + apiJson);
                assertFalse(apiJson.path("error").isObject(), "/api 面不得输出 /v1 形状: " + apiJson);
            }
        }
    }

    /**
     * 真实 WebFlux 组合配置（只装配被测的 {@link SecurityExceptionHandler} advice + 探针控制器）.
     */
    @Configuration
    @EnableWebFlux
    public static class ProbeConfig {

        /**
         * 探针控制器.
         *
         * @return 探针控制器
         */
        @Bean
        public ProbeController probeController() {
            return new ProbeController();
        }

        /**
         * 被测 advice（WebFlux 中先于 WebExceptionHandler 拿到控制器内异常）.
         *
         * @return 安全异常处理器
         */
        @Bean
        public SecurityExceptionHandler securityExceptionHandler() {
            return new SecurityExceptionHandler();
        }

        /**
         * DispatcherHandler 作为 webHandler（{@code WebHttpHandlerBuilder} 需要该 bean 名）.
         *
         * @param context 应用上下文
         * @return WebHandler
         */
        @Bean(name = "webHandler")
        public WebHandler webHandler(final ApplicationContext context) {
            return new DispatcherHandler(context);
        }
    }

    /**
     * 探针控制器：模拟安全异常从注解控制器内上抛.
     */
    @RestController
    public static class ProbeController {

        /**
         * Anthropic 面探针.
         *
         * @return 错误流
         */
        @PostMapping(ANTHROPIC_PATH)
        public Mono<String> anthropicMessages() {
            return Mono.error(authError());
        }

        /**
         * OpenAI 面探针.
         *
         * @return 错误流
         */
        @PostMapping(OPENAI_PATH)
        public Mono<String> chatCompletions() {
            return Mono.error(authError());
        }

        /**
         * 控制台面探针.
         *
         * @return 错误流
         */
        @PostMapping(PROBE_API_PATH)
        public Mono<String> apiProbe() {
            return Mono.error(authError());
        }

        /**
         * 认证异常（与生产鉴权失败一致）.
         *
         * @return 认证异常
         */
        private static AuthenticationException authError() {
            return new AuthenticationException("Invalid credentials", "AUTH_001");
        }
    }

    /**
     * 断言响应体是协议形状的 JSON 对象（{@link Map}）.
     *
     * @param response 处理器返回的响应
     * @return 错误体
     */
    private static Map<?, ?> mapBody(final ResponseEntity<?> response) {
        final Object body = response.getBody();
        assertInstanceOf(Map.class, body, "/v1 路径必须返回协议形状错误体");
        return (Map<?, ?>) body;
    }

    /**
     * 取出协议错误体中的 {@code error} 子对象.
     *
     * @param body 协议错误体
     * @return {@code error} 子对象
     */
    private static Map<?, ?> errorNode(final Map<?, ?> body) {
        final Object error = body.get("error");
        assertInstanceOf(Map.class, error, "错误体必须含 error 对象: " + body);
        return (Map<?, ?>) error;
    }

    /**
     * 经完整 WebFlux 组合分发请求（真实 DispatcherHandler + 真实 advice）.
     *
     * @param httpHandler 组合后的 HttpHandler
     * @param path        请求路径
     * @return 交换对象（含已提交的响应）
     */
    private static MockServerWebExchange dispatchTo(final HttpHandler httpHandler, final String path) {
        final MockServerWebExchange dispatched = exchangeOf(path);
        httpHandler.handle(dispatched.getRequest(), dispatched.getResponse()).block(TIMEOUT);
        return dispatched;
    }

    /**
     * 读取交换对象的响应体.
     *
     * @param exchange 交换对象
     * @return 响应体文本
     */
    private static String bodyOf(final ServerWebExchange exchange) {
        return ((MockServerHttpResponse) exchange.getResponse()).getBodyAsString().block(TIMEOUT);
    }

    /**
     * 解析响应体并要求其<b>恰好</b>是一个 JSON 对象（顺带证明响应未被重复写入）.
     *
     * @param body 响应体文本
     * @return JSON 节点
     * @throws Exception 解析失败
     */
    private JsonNode singleJsonObject(final String body) throws Exception {
        assertNotNull(body, "响应体不应为空");
        final JsonParser parser = objectMapper.getFactory().createParser(body);
        final JsonNode json = objectMapper.readTree(parser);
        assertNotNull(json);
        assertNull(parser.nextToken(), "响应体应只包含一个 JSON 对象（未重复写入）: " + body);
        return json;
    }

    /**
     * 断言响应体仍是既有 {@link SecurityErrorResponse}（非 {@code /v1} 路径契约不变）.
     *
     * @param response 处理器返回的响应
     * @return 既有安全错误体
     */
    private static SecurityErrorResponse legacyBody(final ResponseEntity<?> response) {
        assertNotNull(response, "响应不应为 null");
        final Object body = response.getBody();
        assertInstanceOf(SecurityErrorResponse.class, body,
                "非 /v1 路径必须仍返回 SecurityErrorResponse");
        return (SecurityErrorResponse) body;
    }

    /**
     * 构建真实 WebFlux 交换对象.
     *
     * @param path 请求路径
     * @return 交换对象
     */
    private static MockServerWebExchange exchangeOf(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }
}
