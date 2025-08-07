package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.adapter.AdapterRegistry;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.config.ModelRouterProperties;
import org.unreal.modelrouter.config.ModelServiceRegistry;
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
    private ServerChecker serverChecker;

    @Mock
    private AdapterRegistry adapterRegistry;

    @Mock
    private ModelRouterProperties.ModelInstance modelInstance;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        UniversalController controller = new UniversalController(registry, serverChecker, adapterRegistry);
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
                .uri("/v1/models")
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
    void testGetSystemStatus() {
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
        when(serverChecker.isServiceHealthy(anyString())).thenReturn(true);

        // 执行测试
        webTestClient.get()
                .uri("/v1/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Model Router Gateway")
                .jsonPath("$.services.chat.healthy").isEqualTo(true)
                .jsonPath("$.services.chat.model_count").isEqualTo(1);
    }

    @Test
    void testGetServiceStatus() {
        // 准备测试数据
        List<ModelRouterProperties.ModelInstance> instances = Arrays.asList(modelInstance);
        Map<ModelServiceRegistry.ServiceType, List<ModelRouterProperties.ModelInstance>> allInstances = new HashMap<>();
        allInstances.put(ModelServiceRegistry.ServiceType.chat, instances);

        when(registry.getAllInstances()).thenReturn(allInstances);
        when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.chat)).thenReturn(new HashSet<>(Arrays.asList("test-model")));
        when(registry.getLoadBalanceStrategy(ModelServiceRegistry.ServiceType.chat)).thenReturn("round_robin");
        when(serverChecker.isServiceHealthy("chat")).thenReturn(true);
        when(serverChecker.isInstanceHealthy(anyString(), any(ModelRouterProperties.ModelInstance.class))).thenReturn(true);

        when(modelInstance.getName()).thenReturn("test-instance");
        when(modelInstance.getBaseUrl()).thenReturn("http://test.example.com");
        when(modelInstance.getPath()).thenReturn("/v1");
        when(modelInstance.getWeight()).thenReturn(1);

        // 执行测试
        webTestClient.get()
                .uri("/v1/status/chat")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.service_type").isEqualTo("chat")
                .jsonPath("$.service_healthy").isEqualTo(true)
                .jsonPath("$.available_models[0]").isEqualTo("test-model")
                .jsonPath("$.instances.length()").isEqualTo(1)
                .jsonPath("$.instances[0].name").isEqualTo("test-instance");
    }

    @Test
    void testGetServiceStatusWithInvalidServiceType() {
        // 执行测试
        webTestClient.get()
                .uri("/v1/status/invalid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("Invalid service type: invalid");
    }

    @Test
    void testChatCompletionsWhenServiceUnavailable() {
        // 准备测试数据
        when(serverChecker.isServiceHealthy("chat")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("service_unavailable")
                .jsonPath("$.type").isEqualTo("chat");
    }

    @Test
    void testEmbeddingsWhenServiceUnavailable() {
        // 准备测试数据
        when(serverChecker.isServiceHealthy("embedding")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("service_unavailable")
                .jsonPath("$.type").isEqualTo("embedding");
    }

    @Test
    void testRerankWhenServiceUnavailable() {
        // 准备测试数据
        when(serverChecker.isServiceHealthy("rerank")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/v1/rerank")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("service_unavailable")
                .jsonPath("$.type").isEqualTo("rerank");
    }

    @Test
    void testTextToSpeechWhenServiceUnavailable() {
        // 准备测试数据
        when(serverChecker.isServiceHealthy("tts")).thenReturn(false);

        // 执行测试
        webTestClient.post()
                .uri("/v1/audio/speech")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{}"), String.class)
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.code").isEqualTo("service_unavailable")
                .jsonPath("$.type").isEqualTo("tts");
    }

    @Test
    void testGetModelsSuccessResponseStructure() {
        // 准备测试数据
        Set<String> chatModels = new HashSet<>(Arrays.asList("test-model"));
        when(registry.getAvailableModels(ModelServiceRegistry.ServiceType.chat)).thenReturn(chatModels);

        // 执行测试
        webTestClient.get()
                .uri("/v1/models")
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
