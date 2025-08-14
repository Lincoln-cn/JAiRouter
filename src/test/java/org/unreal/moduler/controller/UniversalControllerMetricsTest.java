package org.unreal.moduler.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.controller.UniversalController;
import org.unreal.modelrouter.dto.ChatDTO;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.monitoring.collector.MetricsCollector;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测试UniversalController中的指标收集功能
 */
@ExtendWith(MockitoExtension.class)
class UniversalControllerMetricsTest {

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private BaseAdapter mockAdapter;

    private UniversalController universalController;

    @BeforeEach
    void setUp() {
        universalController = new UniversalController(adapterRegistry, serviceStateManager, metricsCollector);
    }

    @Test
    void testChatCompletions_WithMetrics_Success() {
        // Arrange
        ChatDTO.Request request = new ChatDTO.Request(
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null
        );
        
        ChatDTO.Response expectedResponse = new ChatDTO.Response(
                "chat-123",
                "chat.completion",
                System.currentTimeMillis() / 1000,
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Choice(
                        0,
                        new ChatDTO.Message("assistant", "Hello! How can I help you?", null),
                        null,
                        "stop"
                )),
                new ChatDTO.Usage(10, 15, 25),
                null
        );

        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/chat/completions")
                .header("Content-Length", "100")
                .build();
        
        when(serviceStateManager.isServiceHealthy("chat")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)).thenReturn(mockAdapter);
        when(mockAdapter.chat(any(ChatDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(expectedResponse)));

        // Act & Assert
        StepVerifier.create(universalController.chatCompletions("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    return true;
                })
                .verifyComplete();

        // Verify metrics were recorded
        verify(metricsCollector).recordRequest(eq("chat"), eq("POST"), anyLong(), eq("200"));
        verify(metricsCollector).recordRequestSize(eq("chat"), eq(100L), anyLong());
        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    void testChatCompletions_WithMetrics_ServiceUnhealthy() {
        // Arrange
        ChatDTO.Request request = new ChatDTO.Request(
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null
        );
        
        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/chat/completions").build();
        
        when(serviceStateManager.isServiceHealthy("chat")).thenReturn(false);

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> {
            universalController.chatCompletions("Bearer token", request, httpRequest).block();
        });

        // Verify metrics were recorded for service unavailable
        verify(metricsCollector).recordRequest(eq("chat"), eq("POST"), anyLong(), eq("503"));
        verify(serviceStateManager).isServiceHealthy("chat");
        verifyNoInteractions(adapterRegistry);
    }

    @Test
    void testChatCompletions_WithMetrics_AdapterError() {
        // Arrange
        ChatDTO.Request request = new ChatDTO.Request(
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null
        );
        
        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/chat/completions").build();
        
        when(serviceStateManager.isServiceHealthy("chat")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)).thenReturn(mockAdapter);
        when(mockAdapter.chat(any(ChatDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.error(new RuntimeException("Backend error")));

        // Act & Assert
        StepVerifier.create(universalController.chatCompletions("Bearer token", request, httpRequest))
                .expectError(RuntimeException.class)
                .verify();

        // Verify metrics were recorded for error
        verify(metricsCollector).recordRequest(eq("chat"), eq("POST"), anyLong(), eq("500"));
        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    void testChatCompletions_WithMetrics_UnsupportedOperation() {
        // Arrange
        ChatDTO.Request request = new ChatDTO.Request(
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null
        );
        
        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/chat/completions").build();
        
        when(serviceStateManager.isServiceHealthy("chat")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)).thenReturn(mockAdapter);
        when(mockAdapter.chat(any(ChatDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenThrow(new UnsupportedOperationException("Chat not supported"));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> {
            universalController.chatCompletions("Bearer token", request, httpRequest).block();
        });

        // Verify metrics were recorded for not implemented
        verify(metricsCollector).recordRequest(eq("chat"), eq("POST"), anyLong(), eq("501"));
        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    void testChatCompletions_WithMetrics_IllegalArgument() {
        // Arrange
        ChatDTO.Request request = new ChatDTO.Request(
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null
        );
        
        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/chat/completions").build();
        
        when(serviceStateManager.isServiceHealthy("chat")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)).thenReturn(mockAdapter);
        when(mockAdapter.chat(any(ChatDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenThrow(new IllegalArgumentException("Invalid configuration"));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> {
            universalController.chatCompletions("Bearer token", request, httpRequest).block();
        });

        // Verify metrics were recorded for bad request
        verify(metricsCollector).recordRequest(eq("chat"), eq("POST"), anyLong(), eq("400"));
        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    void testChatCompletions_WithoutMetricsCollector() {
        // Arrange - Create controller without metrics collector
        UniversalController controllerWithoutMetrics = new UniversalController(
                adapterRegistry, serviceStateManager, null);
        
        ChatDTO.Request request = new ChatDTO.Request(
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null
        );
        
        ChatDTO.Response expectedResponse = new ChatDTO.Response(
                "chat-123",
                "chat.completion",
                System.currentTimeMillis() / 1000,
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Choice(
                        0,
                        new ChatDTO.Message("assistant", "Hello! How can I help you?", null),
                        null,
                        "stop"
                )),
                new ChatDTO.Usage(10, 15, 25),
                null
        );

        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/chat/completions").build();
        
        when(serviceStateManager.isServiceHealthy("chat")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)).thenReturn(mockAdapter);
        when(mockAdapter.chat(any(ChatDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(expectedResponse)));

        // Act & Assert
        StepVerifier.create(controllerWithoutMetrics.chatCompletions("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    return true;
                })
                .verifyComplete();

        // Verify no metrics interactions occurred
        verifyNoInteractions(metricsCollector);
        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    void testMetricsCollector_ExceptionHandling() {
        // Arrange
        ChatDTO.Request request = new ChatDTO.Request(
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null
        );
        
        ChatDTO.Response expectedResponse = new ChatDTO.Response(
                "chat-123",
                "chat.completion",
                System.currentTimeMillis() / 1000,
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Choice(
                        0,
                        new ChatDTO.Message("assistant", "Hello! How can I help you?", null),
                        null,
                        "stop"
                )),
                new ChatDTO.Usage(10, 15, 25),
                null
        );

        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/chat/completions").build();
        
        when(serviceStateManager.isServiceHealthy("chat")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)).thenReturn(mockAdapter);
        when(mockAdapter.chat(any(ChatDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(expectedResponse)));
        
        // Make metrics collector throw exception
        doThrow(new RuntimeException("Metrics error")).when(metricsCollector)
                .recordRequest(anyString(), anyString(), anyLong(), anyString());

        // Act & Assert - Should still work even if metrics fail
        StepVerifier.create(universalController.chatCompletions("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    return true;
                })
                .verifyComplete();

        // Verify metrics were attempted
        verify(metricsCollector).recordRequest(eq("chat"), eq("POST"), anyLong(), eq("200"));
        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }
}