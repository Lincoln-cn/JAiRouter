package org.unreal.modelrouter;

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
import org.unreal.modelrouter.tracing.query.TraceQueryService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UniversalControllerTest {

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private MetricsCollector metricsCollector;

    @Mock
    private TraceQueryService traceQueryService;

    @Mock
    private BaseAdapter mockAdapter;

    private UniversalController universalController;

    @BeforeEach
    void setUp() {
        universalController = new UniversalController(adapterRegistry, serviceStateManager, metricsCollector, traceQueryService);
    }

    @Test
    void testChatCompletions_Success() {
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

        // Act & Assert
        StepVerifier.create(universalController.chatCompletions("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    ChatDTO.Response actualResponse = (ChatDTO.Response) response.getBody();
                    assertNotNull(actualResponse);
                    assertEquals(expectedResponse.id(), actualResponse.id());
                    return true;
                })
                .verifyComplete();

        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    void testChatCompletions_ServiceUnhealthy() {
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

        verify(serviceStateManager).isServiceHealthy("chat");
        verifyNoInteractions(adapterRegistry);
    }

    @Test
    void testChatCompletions_NoAdapter() {
        // Arrange
        ChatDTO.Request request = new ChatDTO.Request(
                "gpt-3.5-turbo",
                List.of(new ChatDTO.Message("user", "Hello", null)),
                null, null, null, null, null, null, null, null, null
        );
        
        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/chat/completions").build();
        
        when(serviceStateManager.isServiceHealthy("chat")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.chat)).thenReturn(null);

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> {
            universalController.chatCompletions("Bearer token", request, httpRequest).block();
        });

        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    void testChatCompletions_AdapterError() {
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

        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }
}