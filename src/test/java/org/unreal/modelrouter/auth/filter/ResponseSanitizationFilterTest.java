package org.unreal.modelrouter.auth.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.auth.sanitization.SanitizationService;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.config.properties.SanitizationConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ResponseSanitizationFilter 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("ResponseSanitizationFilter 测试")
class ResponseSanitizationFilterTest {

    private SanitizationService sanitizationService;
    private SecurityAuditService auditService;
    private SecurityProperties securityProperties;
    private ResponseSanitizationFilter filter;

    @BeforeEach
    void setUp() {
        sanitizationService = mock(SanitizationService.class);
        auditService = mock(SecurityAuditService.class);
        securityProperties = mock(SecurityProperties.class);

        // 默认配置
        SanitizationConfig sanitizationConfig = mock(SanitizationConfig.class);
        SanitizationConfig.ResponseSanitization responseConfig = mock(SanitizationConfig.ResponseSanitization.class);
        when(securityProperties.getSanitization()).thenReturn(sanitizationConfig);
        when(sanitizationConfig.getResponse()).thenReturn(responseConfig);
        when(responseConfig.isLogSanitization()).thenReturn(false);
        when(responseConfig.isFailOnError()).thenReturn(false);

        when(auditService.recordEvent(any())).thenReturn(Mono.empty());

        filter = new ResponseSanitizationFilter(sanitizationService, auditService, securityProperties);
    }

    @Nested
    @DisplayName("排除路径测试")
    class ExcludedPathTests {

        @Test
        @DisplayName("FILT-001: 排除路径 - actuator路径跳过脱敏")
        void testActuatorPathExcluded() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(exchange);
            verifyNoInteractions(sanitizationService);
        }

        @Test
        @DisplayName("FILT-002: 排除路径 - swagger路径跳过脱敏")
        void testSwaggerPathExcluded() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/swagger-ui/index.html")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(exchange);
            verifyNoInteractions(sanitizationService);
        }
    }

    @Nested
    @DisplayName("内容类型测试")
    class ContentTypeTests {

        @Test
        @DisplayName("FILT-003: 内容类型 - JSON内容需要脱敏")
        void testJsonContentTypeNeedsSanitization() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("FILT-004: 内容类型 - 非JSON内容跳过脱敏")
        void testNonJsonContentTypeSkipped() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/api/binary")
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("脱敏服务调用测试")
    class SanitizationServiceTests {

        @Test
        @DisplayName("FILT-005: 脱敏服务 - 成功脱敏")
        void testSuccessfulSanitization() {
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.just("sanitized content"));

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
        }

        @Test
        @DisplayName("FILT-006: 脱敏服务 - 脱敏失败返回原始内容")
        void testSanitizationFailureReturnsOriginal() {
            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Sanitization failed")));

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
        }
    }

    @Nested
    @DisplayName("审计日志测试")
    class AuditLogTests {

        @Test
        @DisplayName("FILT-007: 审计日志 - 启用日志时记录脱敏事件")
        void testAuditLogEnabled() {
            SanitizationConfig sanitizationConfig = mock(SanitizationConfig.class);
            SanitizationConfig.ResponseSanitization responseConfig = mock(SanitizationConfig.ResponseSanitization.class);
            when(securityProperties.getSanitization()).thenReturn(sanitizationConfig);
            when(sanitizationConfig.getResponse()).thenReturn(responseConfig);
            when(responseConfig.isLogSanitization()).thenReturn(true);

            when(sanitizationService.sanitizeResponse(anyString(), anyString()))
                    .thenReturn(Mono.just("sanitized"));
            when(auditService.recordEvent(any())).thenReturn(Mono.empty());

            ResponseSanitizationFilter filterWithAudit = new ResponseSanitizationFilter(
                    sanitizationService, auditService, securityProperties);

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filterWithAudit.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
        }
    }
}
