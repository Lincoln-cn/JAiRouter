package org.unreal.modelrouter.monitor.tracing.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TracingSecurityFilter 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("TracingSecurityFilter 测试")
class TracingSecurityFilterTest {

    private SecurityTracingIntegration securityTracingIntegration;
    private TracingSecurityFilter filter;

    @BeforeEach
    void setUp() {
        securityTracingIntegration = mock(SecurityTracingIntegration.class);
        when(securityTracingIntegration.recordAuthenticationSuccess(any(), any())).thenReturn(Mono.empty());
        when(securityTracingIntegration.recordAuthenticationFailure(any(), anyString(), anyString())).thenReturn(Mono.empty());

        filter = new TracingSecurityFilter(securityTracingIntegration);
    }

    @Nested
    @DisplayName("过滤器顺序测试")
    class FilterOrderTests {

        @Test
        @DisplayName("FILT-016: 过滤器顺序 - 获取正确的顺序值")
        void testFilterOrder() {
            int order = filter.getOrder();
            // 应该是一个较大的值（在安全认证之后）
            assertTrue(order > 0);
        }
    }

    @Nested
    @DisplayName("排除路径测试")
    class ExcludedPathTests {

        @Test
        @DisplayName("FILT-017: 排除路径 - actuator路径跳过追踪")
        void testActuatorPathExcluded() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any());
        }

        @Test
        @DisplayName("FILT-018: 排除路径 - swagger路径跳过追踪")
        void testSwaggerPathExcluded() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/swagger-ui/index.html")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any());
        }
    }

    @Nested
    @DisplayName("正常路径测试")
    class NormalPathTests {

        @Test
        @DisplayName("FILT-019: 正常路径 - 需要追踪的路径正常处理")
        void testNormalPathProcessed() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any());
        }
    }

    @Nested
    @DisplayName("错误处理测试")
    class ErrorHandlingTests {

        @Test
        @DisplayName("FILT-020: 错误处理 - 链中异常正常传播")
        void testExceptionPropagation() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.error(new RuntimeException("Test error")));

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).expectError(RuntimeException.class).verify();
        }
    }
}
