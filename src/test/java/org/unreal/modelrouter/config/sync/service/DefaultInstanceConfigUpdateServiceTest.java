package org.unreal.modelrouter.config.sync.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.persistence.jpa.entity.ServiceInstanceEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ServiceInstanceRepository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * DefaultInstanceConfigUpdateService 单元测试.
 *
 * @since v2.6.12
 */
@DisplayName("DefaultInstanceConfigUpdateService 测试")
@ExtendWith(MockitoExtension.class)
class DefaultInstanceConfigUpdateServiceTest {

    @Mock
    private ServiceInstanceRepository instanceRepository;

    @InjectMocks
    private DefaultInstanceConfigUpdateService updateService;

    private ServiceInstanceEntity testInstance;

    @BeforeEach
    void setUp() {
        testInstance = new ServiceInstanceEntity();
        testInstance.setId(1L);
        testInstance.setInstanceName("test-instance");
        testInstance.setBaseUrl("http://localhost:11434");
        testInstance.setStatus("ACTIVE");
    }

    @Test
    @DisplayName("更新不存在的实例配置应返回失败")
    void testUpdateNonExistentInstance() {
        when(instanceRepository.findByInstanceName("nonexistent"))
            .thenReturn(Optional.empty());

        var result = updateService.updateInstanceConfig("nonexistent", Map.of("key", "value"))
            .block();

        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.message().contains("Instance not found"));
    }

    @Test
    @DisplayName("更新存在的实例配置应成功")
    void testUpdateExistingInstance() {
        when(instanceRepository.findByInstanceName("test-instance"))
            .thenReturn(Optional.of(testInstance));
        when(instanceRepository.save(any(ServiceInstanceEntity.class)))
            .thenReturn(testInstance);

        var result = updateService.updateInstanceConfig("test-instance", Map.of("key", "value"))
            .block();

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("test-instance", result.instanceId());
        verify(instanceRepository).save(any(ServiceInstanceEntity.class));
    }

    @Test
    @DisplayName("批量更新实例配置")
    void testBatchUpdate() {
        when(instanceRepository.findByInstanceName("test-instance"))
            .thenReturn(Optional.of(testInstance));
        when(instanceRepository.save(any(ServiceInstanceEntity.class)))
            .thenReturn(testInstance);

        var updates = List.of(
            new InstanceConfigUpdateService.InstanceConfigUpdate("test-instance", Map.of("key1", "value1")),
            new InstanceConfigUpdateService.InstanceConfigUpdate("nonexistent", Map.of("key2", "value2"))
        );

        var result = updateService.batchUpdate(updates).block();

        assertNotNull(result);
        assertEquals(1, result.successCount());
        assertEquals(1, result.failureCount());
    }

    @Test
    @DisplayName("批量更新空列表")
    void testBatchUpdateEmptyList() {
        var result = updateService.batchUpdate(Collections.emptyList()).block();

        assertNotNull(result);
        assertTrue(result.success());
        assertEquals(0, result.successCount());
        assertEquals(0, result.failureCount());
    }
}
