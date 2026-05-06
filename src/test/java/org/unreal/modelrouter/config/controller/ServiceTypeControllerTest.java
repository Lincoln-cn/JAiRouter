package org.unreal.modelrouter.config.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.config.core.ConfigurationService;
import org.unreal.modelrouter.config.core.ServiceConfigManager;
import org.unreal.modelrouter.config.core.ConfigurationValidator;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.*;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ServiceTypeController 单元测试 - v2.9.6
 */
@DisplayName("ServiceTypeController v2.9.6 测试")
@ExtendWith(MockitoExtension.class)
class ServiceTypeControllerTest {

    @Mock
    private ConfigurationService configurationService;

    @Mock
    private ServiceConfigManager serviceConfigManager;

    @Mock
    private ConfigurationValidator configurationValidator;

    @InjectMocks
    private ServiceTypeController controller;

    private Map<String, Object> sampleConfig;

    @BeforeEach
    void setUp() {
        sampleConfig = new HashMap<>();
        sampleConfig.put("services", Map.of("chat", Map.of("instances", List.of())));
    }

    @Test
    @DisplayName("测试 1: 获取所有配置")
    void testGetAllConfigurations() {
        when(configurationService.getAllConfigurations()).thenReturn(sampleConfig);

        StepVerifier.create(controller.getAllConfigurations())
                .assertNext(response -> {
                    assertTrue(response.getStatusCode().is2xxSuccessful());
                    assertNotNull(response.getBody());
                    assertTrue(response.getBody().isSuccess());
                    assertNotNull(response.getBody().getData());
                })
                .verifyComplete();

        verify(configurationService).getAllConfigurations();
    }

    @Test
    @DisplayName("测试 2: 获取可用模型列表")
    void testGetAvailableModels() {
        Set<String> models = Set.of("gpt-4", "gpt-3.5-turbo");
        when(configurationValidator.isValidServiceType("chat")).thenReturn(true);
        when(configurationService.getAvailableModels("chat")).thenReturn(models);

        ResponseEntity<RouterResponse<Set<String>>> response = controller.getAvailableModels("chat");

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(models, response.getBody().getData());

        verify(configurationValidator).isValidServiceType("chat");
        verify(configurationService).getAvailableModels("chat");
    }

    @Test
    @DisplayName("测试 3: 重置配置为默认值")
    void testResetToDefaultConfig() {
        doNothing().when(configurationService).resetToDefaultConfig();

        ResponseEntity<RouterResponse<Void>> response = controller.resetToDefaultConfig();

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        verify(configurationService).resetToDefaultConfig();
    }

    @Test
    @DisplayName("测试 4: 获取可用服务类型列表")
    void testGetAvailableServiceTypes() {
        Set<String> serviceTypes = Set.of("chat", "embedding", "rerank");
        when(configurationService.getAvailableServiceTypes()).thenReturn(serviceTypes);

        ResponseEntity<RouterResponse<Set<String>>> response = controller.getAvailableServiceTypes();

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());
        assertEquals(serviceTypes, response.getBody().getData());

        verify(configurationService).getAvailableServiceTypes();
    }

    @Test
    @DisplayName("测试 5: 删除服务 - 成功")
    void testDeleteServiceSuccess() {
        when(configurationValidator.isValidServiceType("embedding")).thenReturn(true);
        doNothing().when(serviceConfigManager).deleteService(anyString());

        ResponseEntity<RouterResponse<Void>> response = controller.deleteService("embedding");

        assertTrue(response.getStatusCode().is2xxSuccessful());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isSuccess());

        verify(configurationValidator).isValidServiceType("embedding");
        verify(serviceConfigManager).deleteService("embedding");
    }

    @Test
    @DisplayName("测试 6: 删除服务 - 无效服务类型抛出异常")
    void testDeleteServiceInvalidType() {
        when(configurationValidator.isValidServiceType("nonexistent")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> {
            controller.deleteService("nonexistent");
        });

        verify(configurationValidator).isValidServiceType("nonexistent");
        verify(serviceConfigManager, never()).deleteService(anyString());
    }
}