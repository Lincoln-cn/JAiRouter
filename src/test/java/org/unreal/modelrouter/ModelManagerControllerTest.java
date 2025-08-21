package org.unreal.modelrouter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.config.ConfigurationService;
import org.unreal.modelrouter.config.ConfigurationValidator;
import org.unreal.modelrouter.controller.ServiceTypeController;
import org.unreal.modelrouter.model.ModelRouterProperties;
import reactor.core.publisher.Mono;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
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

        ServiceTypeController controller = new ServiceTypeController(configurationService, configurationValidator);
        webTestClient = WebTestClient.bindToController(controller).build();
    }

    @Test
    void getAllConfigurations_shouldReturnConfiguration() {
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
                .uri("/api/config/type")
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .value(result -> {
                    Map<String, Object> data = (Map<String, Object>) result.get("data");
                    assert "test-adapter".equals(data.get("adapter"));
                    Map<String, Object> returnedServices = (Map<String, Object>) data.get("services");
                    Map<String, Object> returnedChatService = (Map<String, Object>) returnedServices.get("chat");
                    assert "chat-adapter".equals(returnedChatService.get("adapter"));
                });

    }

    @Test
    void createService_shouldCreateNewService() {
        // Given
        doNothing().when(configurationService).createService(anyString(), anyMap());
        when(configurationValidator.isValidServiceType(anyString())).thenReturn(true);
        doNothing().when(configurationValidator).validateServiceConfig(anyString(), anyMap(), anyList(), anyList());

        // When & Then
        webTestClient.post()
                .uri("/api/config/type/services/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"instances\":[],\"loadBalance\":{\"type\":\"random\"}}"), String.class)
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    void updateServiceConfig_shouldUpdateServiceConfiguration() {
        // Given
        doNothing().when(configurationService).updateServiceConfig(anyString(), anyMap());
        when(configurationValidator.isValidServiceType(anyString())).thenReturn(true);
        doNothing().when(configurationValidator).validateServiceConfig(anyString(), anyMap(), anyList(), anyList());

        // When & Then
        webTestClient.put()
                .uri("/api/config/type/services/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just("{\"adapter\":\"new-adapter\",\"loadBalance\":{\"type\":\"round-robin\"}}"), String.class)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void deleteService_shouldRemoveService() {
        // Given
        doNothing().when(configurationService).deleteService(anyString());
        when(configurationValidator.isValidServiceType(anyString())).thenReturn(true);

        // When & Then
        webTestClient.delete()
                .uri("/api/config/type/services/chat")
                .exchange()
                .expectStatus().isOk();
    }
}
