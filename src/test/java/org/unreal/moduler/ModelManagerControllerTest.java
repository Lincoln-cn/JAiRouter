package org.unreal.moduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.config.ConfigurationValidator;
import org.unreal.modelrouter.controller.ServiceTypeController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

class ModelManagerControllerTest {

    private WebTestClient webTestClient;

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private ConfigurationValidator configurationValidator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        ServiceTypeController controller = new ServiceTypeController(configurationService,configurationValidator);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void getCurrentConfig_shouldReturnConfiguration() {
        // Given
        Map<String, Object> testConfig = new HashMap<>();
        testConfig.put("adapter", "test-adapter");

        Map<String, Object> services = new HashMap<>();
        Map<String, Object> chatService = new HashMap<>();
        chatService.put("adapter", "chat-adapter");
        services.put("chat", chatService);
        testConfig.put("services", services);

        when(configurationService.getAllConfigurations()).thenReturn(testConfig);

        // When & Then
        webTestClient.get()
                .uri("/api/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(result -> {
                    assert result.get("adapter").equals("test-adapter");
                    Map<String, Object> returnedServices = (Map<String, Object>) result.get("services");
                    Map<String, Object> returnedChatService = (Map<String, Object>) returnedServices.get("chat");
                    assert returnedChatService.get("adapter").equals("chat-adapter");
                });
    }

    @Test
    void addServiceInstance_shouldAddNewInstance() {
        // Given
        doNothing().when(configurationService).addServiceInstance(anyString(), any());

        // When & Then
        webTestClient.post()
                .uri("/api/config/services/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"name\":\"test-model\",\"baseUrl\":\"http://test.example.com\",\"path\":\"/v1/chat/completions\",\"weight\":1}"), String.class)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void updateServiceConfig_shouldUpdateServiceConfiguration() {
        // Given
        doNothing().when(configurationService).updateServiceConfig(anyString(), anyMap());

        // When & Then
        webTestClient.put()
                .uri("/api/config/services/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"adapter\":\"new-adapter\",\"loadBalance\":{\"type\":\"round-robin\"}}"), String.class)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void removeServiceInstance_shouldRemoveInstance() {
        // Given
        doNothing().when(configurationService).addServiceInstance(anyString(), any());

        // When & Then
        webTestClient.post()
                .uri("/api/config/services/chat/instances")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"name\":\"test-model\",\"baseUrl\":\"http://test.example.com\",\"path\":\"/v1/chat/completions\",\"weight\":1}"), String.class)
                .exchange()
                .expectStatus().isOk();
    }
}
