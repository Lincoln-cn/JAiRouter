package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.controller.UniversalController;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class UniversalControllerTest {

    private WebTestClient webTestClient;

    @Mock
    private ModelServiceRegistry registry;

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private ModelRouterProperties.ModelInstance modelInstance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        UniversalController controller = new UniversalController(registry, adapterRegistry, serviceStateManager);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void testGetModels() {
        // 准备测试数据
        Set<String> chatModels = new HashSet<>(Arrays.asList("gpt-3.5-turbo", "gpt-4"));
        Set<String> embeddingModels = new HashSet<>(Arrays.asList("text-embedding-ada-002"));

        when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.chat)).thenReturn(chatModels);
        when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.embedding)).thenReturn(embeddingModels);

        // 执行测试
        webTestClient.get()
                .uri("/api/models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data.length()").isEqualTo(3)
                // 使用更灵活的断言方式，不依赖于顺序
                .jsonPath("$.data[?(@.id == 'gpt-3.5-turbo')].id").isEqualTo("gpt-3.5-turbo")
                .jsonPath("$.data[?(@.id == 'gpt-4')].id").isEqualTo("gpt-4")
                .jsonPath("$.data[?(@.id == 'text-embedding-ada-002')].id").isEqualTo("text-embedding-ada-002");
    }

    @Test
    void testGetStatus() {
        // 准备测试数据
        Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> allInstances = new HashMap<>();
        allInstances.put(ModelServiceRegistry.ServiceType.chat, Arrays.asList(modelInstance));

        when(registry.getAllInstances()).thenReturn(allInstances);
        when(registry.getAvailableModels(any(ModelServiceRegistry.ServiceType.class))).thenAnswer(invocation -> {
            ModelServiceRegistry.ServiceType type = invocation.getArgument(0);
            if (type == ModelServiceRegistry.ServiceType.chat) {
                return new HashSet<>(Arrays.asList("test-model"));
            }
            return new HashSet<>();
        });
        when(serviceStateManager.isServiceHealthy(anyString())).thenReturn(true);
        when(registry.getServiceAdapter(any())).thenReturn("normal");
        when(registry.getLoadBalanceStrategy(any())).thenReturn("RoundRobinLoadBalancer");

        when(modelInstance.getName()).thenReturn("test-instance");
        when(modelInstance.getBaseUrl()).thenReturn("http://test.example.com");
        when(modelInstance.getPath()).thenReturn("/v1");

        // 执行测试
        webTestClient.get()
                .uri("/api/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.timestamp").exists()
                .jsonPath("$.status").isEqualTo("running")
                .jsonPath("$.services.chat.healthy").isEqualTo(true)
                .jsonPath("$.services.chat.adapter").isEqualTo("normal");
    }

    @Test
    void testChatCompletionsWhenServiceUnavailable() {
        // 准备测试数据
        when(serviceStateManager.isServiceHealthy("chat")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/api/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void testEmbeddingsWhenServiceUnavailable() {
        // 准备测试数据
        when(serviceStateManager.isServiceHealthy("embedding")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/api/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void testRerankWhenServiceUnavailable() {
        // 准备测试数据
        when(serviceStateManager.isServiceHealthy("rerank")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/api/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void testTextToSpeechWhenServiceUnavailable() {
        // 准备测试数据
        when(serviceStateManager.isServiceHealthy("tts")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/api/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void testSpeechToTextWhenServiceUnavailable() {
        // 准备测试数据
        when(serviceStateManager.isServiceHealthy("stt")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/api/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(Mono.just(new MockMultipartFile("file", "test.wav", "audio/wav", "test".getBytes())), MockMultipartFile.class)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void testImageGenerateWhenServiceUnavailable() {
        // 准备测试数据
        when(serviceStateManager.isServiceHealthy("imgGen")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/api/images/generations")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void testImageEditWhenServiceUnavailable() {
        // 准备测试数据
        when(serviceStateManager.isServiceHealthy("imgEdit")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/api/images/edits")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503);
    }

    @Test
    void testGetModelsSuccessResponseStructure() {
        // 准备测试数据
        Set<String> chatModels = new HashSet<>(Arrays.asList("test-model"));
        when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.chat)).thenReturn(chatModels);

        // 执行测试
        webTestClient.get()
                .uri("/api/models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data").isArray()
                .jsonPath("$.data[0].object").isEqualTo("model")
                .jsonPath("$.data[0].owned_by").isEqualTo("model-router")
                .jsonPath("$.data[0].service_type").isEqualTo("chat");
    }

}
