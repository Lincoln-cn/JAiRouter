package org.unreal.modelrouter.config.sync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceConfigEntity;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * HttpConfigPushService 单元测试.
 *
 * @since v2.6.12
 */
@DisplayName("HttpConfigPushService 测试")
@ExtendWith(MockitoExtension.class)
class HttpConfigPushServiceTest {

    @Mock
    private ServiceConfigRepository serviceConfigRepository;

    @Mock
    private ServiceInstanceRepository serviceInstanceRepository;

    @InjectMocks
    private HttpConfigPushService configPushService;

    private ServiceConfigEntity testConfig;
    private ServiceInstanceEntity testInstance;

    @BeforeEach
    void setUp() {
        testConfig = new ServiceConfigEntity();
        testConfig.setId(1L);
        testConfig.setServiceType("chat");
        testConfig.setAdapter("ollama");
        testConfig.setIsLatest(true);

        testInstance = new ServiceInstanceEntity();
        testInstance.setId(1L);
        testInstance.setServiceConfigId(1L);
        testInstance.setInstanceName("test-instance");
        testInstance.setBaseUrl("http://localhost:11434");
        testInstance.setStatus("ACTIVE");

        configPushService.init();
    }

    @Test
    @DisplayName("推送配置到不存在的服务应返回失败")
    void testPushToNonExistentService() {
        when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("nonexistent"))
            .thenReturn(Optional.empty());

        var result = configPushService.pushToService("nonexistent", Map.of("key", "value"))
            .block();

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.message().contains("Service not found"));
    }

    @Test
    @DisplayName("推送配置到无实例的服务应返回失败")
    void testPushToServiceWithNoInstances() {
        when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("chat"))
            .thenReturn(Optional.of(testConfig));
        when(serviceInstanceRepository.findByServiceConfigId(1L))
            .thenReturn(Collections.emptyList());

        var result = configPushService.pushToService("chat", Map.of("key", "value"))
            .block();

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.message().contains("No instances found"));
    }

    @Test
    @DisplayName("推送配置到无活跃实例的服务应返回失败")
    void testPushToServiceWithNoActiveInstances() {
        testInstance.setStatus("INACTIVE");

        when(serviceConfigRepository.findFirstByServiceTypeAndIsLatestTrue("chat"))
            .thenReturn(Optional.of(testConfig));
        when(serviceInstanceRepository.findByServiceConfigId(1L))
            .thenReturn(List.of(testInstance));

        var result = configPushService.pushToService("chat", Map.of("key", "value"))
            .block();

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.message().contains("No active instance"));
    }

    @Test
    @DisplayName("广播配置到空服务列表应返回成功")
    void testBroadcastToEmptyServices() {
        when(serviceConfigRepository.findAll())
            .thenReturn(Collections.emptyList());

        var result = configPushService.broadcastConfig(Map.of("key", "value"))
            .block();

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(0, result.successCount());
    }
}
