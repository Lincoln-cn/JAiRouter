package org.unreal.modelrouter.tracing.interceptor;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BackendCallTracingInterceptorTest {

    @Mock
    private StructuredLogger structuredLogger;

    @Mock
    private TracingContext tracingContext;

    @Mock
    private Span span;

    @Mock
    private ClientResponse response;

    @Mock
    private ExchangeFunction exchangeFunction;

    private BackendCallTracingInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new BackendCallTracingInterceptor(structuredLogger);
    }

    @Test
    void testFilterWithoutActiveTracingContext() {
        // Given
        try (var mockStatic = mockStatic(TracingContextHolder.class)) {
            mockStatic.when(TracingContextHolder::getCurrentContext).thenReturn(null);
            
            ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://example.com")).build();
            when(exchangeFunction.exchange(request)).thenReturn(Mono.just(response));

            // When
            Mono<ClientResponse> result = interceptor.filter(request, exchangeFunction);

            // Then
            StepVerifier.create(result)
                    .expectNext(response)
                    .verifyComplete();
            verify(exchangeFunction).exchange(request);
        }
    }

    @Test
    void testFilterWithActiveTracingContext_Success() {
        // Given
        try (var mockStatic = mockStatic(TracingContextHolder.class)) {
            mockStatic.when(TracingContextHolder::getCurrentContext).thenReturn(tracingContext);
            when(tracingContext.isActive()).thenReturn(true);
            when(tracingContext.createSpan(anyString(), any(SpanKind.class))).thenReturn(span);
            
            ClientRequest request = ClientRequest.create(HttpMethod.GET, URI.create("http://api.openai.com/v1/chat/completions")).build();
            
            ClientResponse response = mock(ClientResponse.class);
            when(response.statusCode()).thenReturn(org.springframework.http.HttpStatus.OK);
            when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(response));

            // When & Then
            StepVerifier.create(interceptor.filter(request, exchangeFunction))
                    .expectNext(response)
                    .verifyComplete();
        }
    }

    @Test
    void testInferAdapterType() {
        // Given
        String openaiHost = "api.openai.com";
        String ollamaHost = "ollama.local";
        String unknownHost = "unknown.service.com";

        // When
        String openaiAdapter = interceptor.inferAdapterType(openaiHost);
        String ollamaAdapter = interceptor.inferAdapterType(ollamaHost);
        String unknownAdapter = interceptor.inferAdapterType(unknownHost);

        // Then
        assertEquals("openai", openaiAdapter);
        assertEquals("ollama", ollamaAdapter);
        assertEquals("unknown", unknownAdapter);
    }
}