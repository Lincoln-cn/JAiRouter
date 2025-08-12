package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.adapter.BaseAdapter;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.controller.UniversalController;
import org.unreal.modelrouter.dto.*;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UniversalControllerTest {

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private BaseAdapter mockAdapter;

    private UniversalController universalController;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        universalController = new UniversalController(adapterRegistry, serviceStateManager);
        webTestClient = WebTestClient.bindToController(universalController).build();
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
                    ChatDTO.Response body = (ChatDTO.Response) response.getBody();
                    assertNotNull(body);
                    assertEquals("chat-123", body.id());
                    assertEquals("gpt-3.5-turbo", body.model());
                    return true;
                })
                .verifyComplete();

        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
        verify(mockAdapter).chat(eq(request), eq("Bearer token"), any(ServerHttpRequest.class));
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
    void testEmbeddings_Success() {
        // Arrange
        EmbeddingDTO.Request request = new EmbeddingDTO.Request(
                "text-embedding-ada-002",
                List.of("Hello world"),
                null, null, null
        );
        
        EmbeddingDTO.Response expectedResponse = new EmbeddingDTO.Response(
                "embedding",
                List.of(new EmbeddingDTO.EmbeddingData(
                        "embedding",
                        List.of(0.1, 0.2, 0.3),
                        0
                )),
                "text-embedding-ada-002",
                new EmbeddingDTO.Usage(2, 2)
        );

        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/embeddings").build();
        
        when(serviceStateManager.isServiceHealthy("embedding")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.embedding)).thenReturn(mockAdapter);
        when(mockAdapter.embedding(any(EmbeddingDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(expectedResponse)));

        // Act & Assert
        StepVerifier.create(universalController.embeddings("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    EmbeddingDTO.Response body = (EmbeddingDTO.Response) response.getBody();
                    assertNotNull(body);
                    assertEquals("text-embedding-ada-002", body.model());
                    assertEquals(1, body.data().size());
                    return true;
                })
                .verifyComplete();

        verify(serviceStateManager).isServiceHealthy("embedding");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.embedding);
        verify(mockAdapter).embedding(eq(request), eq("Bearer token"), any(ServerHttpRequest.class));
    }

    @Test
    void testRerank_Success() {
        // Arrange
        RerankDTO.Request request = new RerankDTO.Request(
                "rerank-model",
                "query text",
                List.of("doc1", "doc2"),
                null, null
        );
        
        RerankDTO.Response expectedResponse = new RerankDTO.Response(
                "rerank-123",
                List.of(
                        new RerankDTO.RerankResult(0, 0.9, "doc1"),
                        new RerankDTO.RerankResult(1, 0.7, "doc2")
                ),
                "rerank-model",
                new RerankDTO.Usage(10)
        );

        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/rerank").build();
        
        when(serviceStateManager.isServiceHealthy("rerank")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.rerank)).thenReturn(mockAdapter);
        when(mockAdapter.rerank(any(RerankDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(expectedResponse)));

        // Act & Assert
        StepVerifier.create(universalController.rerank("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    RerankDTO.Response body = (RerankDTO.Response) response.getBody();
                    assertNotNull(body);
                    assertEquals("rerank-model", body.model());
                    assertEquals(2, body.results().size());
                    return true;
                })
                .verifyComplete();

        verify(serviceStateManager).isServiceHealthy("rerank");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.rerank);
        verify(mockAdapter).rerank(eq(request), eq("Bearer token"), any(ServerHttpRequest.class));
    }

    @Test
    void testTextToSpeech_Success() {
        // Arrange
        TtsDTO.Request request = new TtsDTO.Request(
                "tts-1",
                "Hello world",
                "alloy",
                null, null
        );
        
        byte[] audioData = "fake audio data".getBytes();
        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/audio/speech").build();
        
        when(serviceStateManager.isServiceHealthy("tts")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.tts)).thenReturn(mockAdapter);
        when(mockAdapter.tts(any(TtsDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(audioData)));

        // Act & Assert
        StepVerifier.create(universalController.textToSpeech("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.getHeaders().getContentType());
                    return true;
                })
                .verifyComplete();

        verify(serviceStateManager).isServiceHealthy("tts");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.tts);
        verify(mockAdapter).tts(eq(request), eq("Bearer token"), any(ServerHttpRequest.class));
    }

    @Test
    void testSpeechToText_Success() {
        // Arrange
        SttDTO.Request request = new SttDTO.Request(
                "whisper-1",
                null, // file will be set separately
                "auto", null, null, null
        );
        
        SttDTO.Response expectedResponse = new SttDTO.Response("Hello world", "en", 2.5, null);
        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/audio/transcriptions").build();
        
        when(serviceStateManager.isServiceHealthy("stt")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.stt)).thenReturn(mockAdapter);
        when(mockAdapter.stt(any(SttDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(expectedResponse)));

        // Act & Assert
        StepVerifier.create(universalController.speechToText("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    SttDTO.Response body = (SttDTO.Response) response.getBody();
                    assertNotNull(body);
                    assertEquals("Hello world", body.text());
                    return true;
                })
                .verifyComplete();

        verify(serviceStateManager).isServiceHealthy("stt");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.stt);
        verify(mockAdapter).stt(eq(request), eq("Bearer token"), any(ServerHttpRequest.class));
    }

    @Test
    void testImageGenerate_Success() {
        // Arrange
        ImageGenerateDTO.Request request = new ImageGenerateDTO.Request(
                "A beautiful sunset",
                "dall-e-3",
                null, null, null, null, null, null
        );
        
        ImageGenerateDTO.Response expectedResponse = new ImageGenerateDTO.Response(
                System.currentTimeMillis() / 1000,
                new ImageGenerateDTO.Response.Data[]{
                        new ImageGenerateDTO.Response.Data(
                                "https://example.com/image.png",
                                null,
                                "A beautiful sunset"
                        )
                },
                null
        );

        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/images/generations").build();
        
        when(serviceStateManager.isServiceHealthy("imgGen")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.imgGen)).thenReturn(mockAdapter);
        when(mockAdapter.imageGenerate(any(ImageGenerateDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(expectedResponse)));

        // Act & Assert
        StepVerifier.create(universalController.imageGenerate("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    ImageGenerateDTO.Response body = (ImageGenerateDTO.Response) response.getBody();
                    assertNotNull(body);
                    assertEquals(1, body.data().length);
                    assertEquals("A beautiful sunset", body.data()[0].revised_prompt());
                    return true;
                })
                .verifyComplete();

        verify(serviceStateManager).isServiceHealthy("imgGen");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.imgGen);
        verify(mockAdapter).imageGenerate(eq(request), eq("Bearer token"), any(ServerHttpRequest.class));
    }

    @Test
    void testImageEdits_Success() {
        // Arrange
        ImageEditDTO.Request request = new ImageEditDTO.Request(
                null, // image will be set separately
                "Edit this image",
                null, null, null, // background, input_fidelity, mask
                "dall-e-2",
                null, null, null, null, null, null, null, null, null
        );
        
        ImageEditDTO.Response expectedResponse = new ImageEditDTO.Response(
                System.currentTimeMillis() / 1000,
                new ImageEditDTO.Response.Data[]{
                        new ImageEditDTO.Response.Data(
                                "https://example.com/edited-image.png",
                                null
                        )
                },
                null, null, null, null, null
        );

        ServerHttpRequest httpRequest = MockServerHttpRequest.post("/v1/images/edits").build();
        
        when(serviceStateManager.isServiceHealthy("imgEdit")).thenReturn(true);
        when(adapterRegistry.getAdapter(ModelServiceRegistry.ServiceType.imgEdit)).thenReturn(mockAdapter);
        when(mockAdapter.imageEdit(any(ImageEditDTO.Request.class), anyString(), any(ServerHttpRequest.class)))
                .thenReturn(Mono.just(ResponseEntity.ok(expectedResponse)));

        // Act & Assert
        StepVerifier.create(universalController.imageEdits("Bearer token", request, httpRequest))
                .expectNextMatches(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    ImageEditDTO.Response body = (ImageEditDTO.Response) response.getBody();
                    assertNotNull(body);
                    assertEquals(1, body.data().length);
                    assertNotNull(body.data()[0].url());
                    return true;
                })
                .verifyComplete();

        verify(serviceStateManager).isServiceHealthy("imgEdit");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.imgEdit);
        verify(mockAdapter).imageEdit(eq(request), eq("Bearer token"), any(ServerHttpRequest.class));
    }

    @Test
    void testHandleServiceRequest_UnsupportedOperation() {
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

        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    void testHandleServiceRequest_IllegalArgument() {
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

        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }

    @Test
    void testHandleServiceRequest_GenericException() {
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
                .thenThrow(new RuntimeException("Unexpected error"));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> {
            universalController.chatCompletions("Bearer token", request, httpRequest).block();
        });

        verify(serviceStateManager).isServiceHealthy("chat");
        verify(adapterRegistry).getAdapter(ModelServiceRegistry.ServiceType.chat);
    }
}
