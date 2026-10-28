package org.unreal.modelrouter.monitor.tracing.logger.builder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.config.TracingConfiguration;
import org.unreal.modelrouter.monitor.tracing.logger.dto.RequestLogFields;
import org.unreal.modelrouter.monitor.tracing.sanitization.TracingSanitizationService;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * issue #695：结构化日志的 {@code url} 字段必须抹掉 query 里的凭据。
 *
 * <p>旧实现直接写 {@code request.getURI().toString()}，而 {@code TracingSanitizationService}
 * 只按字段名判断敏感（{@code url} 不敏感），凭据因此原样落盘。</p>
 */
@DisplayName("RequestLogBuilder url 字段脱敏（#695）")
class RequestLogBuilderUrlRedactionTest {

    private RequestLogBuilder newBuilder() {
        TracingConfiguration configuration = new TracingConfiguration();
        configuration.getLogging().setCaptureHeaders(false);

        TracingSanitizationService sanitizationService = mock(TracingSanitizationService.class);
        when(sanitizationService.sanitizeLogData(anyMap(), any()))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        return new RequestLogBuilder(configuration, sanitizationService);
    }

    @Test
    @DisplayName("url 字段不含 query 凭据，但保留路径与非敏感参数")
    void urlFieldDoesNotLeakQueryCredentials() {
        ServerHttpRequest request = MockServerHttpRequest
                .get("/v1/chat/completions?token=sk-secret-jwt&api_key=sk-secret-key&model=gpt-4o")
                .build();

        RequestLogFields fields = newBuilder()
                .buildRequestFields(request, mock(TracingContext.class))
                .block();

        assertNotNull(fields);
        assertNotNull(fields.getUrl());
        assertFalse(fields.getUrl().contains("sk-secret-jwt"), "url 不得含 JWT: " + fields.getUrl());
        assertFalse(fields.getUrl().contains("sk-secret-key"), "url 不得含 API Key: " + fields.getUrl());
        assertTrue(fields.getUrl().contains("token=[REDACTED]"), fields.getUrl());
        assertTrue(fields.getUrl().contains("api_key=[REDACTED]"), fields.getUrl());
        assertTrue(fields.getUrl().contains("model=gpt-4o"), fields.getUrl());
        assertTrue(fields.getUrl().contains("/v1/chat/completions"), fields.getUrl());
    }
}
