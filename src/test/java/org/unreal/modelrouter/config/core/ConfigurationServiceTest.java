package org.unreal.modelrouter.config.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.config.core.manager.*;
import org.unreal.modelrouter.config.core.service.ConfigQueryService;
import org.unreal.modelrouter.config.core.service.InstanceOperationService;
import org.unreal.modelrouter.config.core.service.ServiceConfigUpdateService;
import org.unreal.modelrouter.config.dto.UpdateServiceConfigRequest;
import org.unreal.modelrouter.config.event.ConfigSyncEvent;
import org.unreal.modelrouter.config.core.ConfigSyncService;
import org.unreal.modelrouter.monitor.tracing.config.SamplingConfigurationValidator;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ConfigurationService 单元测试
 *
 * <p>测试配置管理核心功能</p>
 *
 * @version v2.10.0
 * @since 2026-05-24
 */
@DisplayName("ConfigurationService 配置管理服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConfigurationServiceTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private ConfigurationHelper configurationHelper;

    @Mock
    private ConfigMergeService configMergeService;

    @Mock
    private ServiceStateManager serviceStateManager;

    @Mock
    private SamplingConfigurationValidator samplingValidator;

    @Mock
    private ServiceConfigManager serviceConfigManager;

    @Mock
    private InstanceManager instanceManager;

    @Mock
    private ConfigVersionManager configVersionManager;

    @Mock
    private ConfigValidator configValidator;

    @Mock
    private TracingConfigManager tracingConfigManager;

    @Mock
    private ConfigComparisonService configComparisonService;

    @Mock
    private ConfigQueryService configQueryService;

    @Mock
    private InstanceOperationService instanceOperationService;

    @Mock
    private ServiceConfigUpdateService serviceConfigUpdateService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private ConfigSyncService configSyncService;

    @InjectMocks
    private ConfigurationService configurationService;

    private Map<String, Object> sampleConfig;

    @BeforeEach
    void setUp() {
        // 手动注入 eventPublisher (因为它是字段注入，@InjectMocks 不会自动注入)
        ReflectionTestUtils.setField(configurationService, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(configurationService, "configSyncService", configSyncService);

        // 准备示例配置
        sampleConfig = new HashMap<>();
        Map<String, Object> services = new HashMap<>();
        Map<String, Object> gpustackConfig = new HashMap<>();
        gpustackConfig.put("enabled", true);
        gpustackConfig.put("instances", new ArrayList<>());
        services.put("gpustack", gpustackConfig);
        sampleConfig.put("services", services);

        // 设置默认 mock 行为
        when(configVersionManager.getCurrentVersion()).thenReturn(1);
        when(configValidator.isValidServiceType(anyString())).thenReturn(true);
        when(configValidator.createDefaultServiceConfig()).thenReturn(new HashMap<>());
    }

    // ==================== 版本管理测试 ====================

    @Nested
    @DisplayName("版本管理功能测试")
    class VersionManagementTests {

        @Test
        @DisplayName("CONFIG-001: 配置未变化时不创建新版本")
        void testSaveAsNewVersionIfChanged_NoChange() {
            // Given
            Map<String, Object> config = new HashMap<>(sampleConfig);
            when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
            when(storeManager.getConfigByVersion(anyString(), anyInt())).thenReturn(sampleConfig);
            when(configComparisonService.isConfigurationChanged(any(), any())).thenReturn(false);

            // When
            int result = configurationService.saveAsNewVersionIfChanged(config, "test", "user1");

            // Then
            assertEquals(1, result);
            verify(configVersionManager, never()).saveAsNewVersion(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("CONFIG-002: 配置变化时创建新版本")
        void testSaveAsNewVersionIfChanged_WithChange() {
            // Given
            Map<String, Object> newConfig = new HashMap<>(sampleConfig);
            newConfig.put("newKey", "newValue");

            when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
            when(storeManager.getConfigByVersion(anyString(), anyInt())).thenReturn(sampleConfig);
            when(configComparisonService.isConfigurationChanged(any(), any())).thenReturn(true);
            when(configVersionManager.saveAsNewVersion(any(), anyString(), anyString())).thenReturn(2);

            // When
            int result = configurationService.saveAsNewVersionIfChanged(newConfig, "添加新配置", "user1");

            // Then
            assertEquals(2, result);
            verify(configVersionManager).saveAsNewVersion(any(), eq("添加新配置"), eq("user1"));
        }
    }

    // ==================== 查询操作测试 ====================

    @Nested
    @DisplayName("查询操作测试")
    class QueryOperationTests {

        @Test
        @DisplayName("CONFIG-003: 获取所有配置")
        void testGetAllConfigurations() {
            // Given
            when(configQueryService.getAllConfigurations()).thenReturn(sampleConfig);

            // When
            Map<String, Object> result = configurationService.getAllConfigurations();

            // Then
            assertNotNull(result);
            assertTrue(result.containsKey("services"));
            verify(configQueryService).getAllConfigurations();
        }

        @Test
        @DisplayName("CONFIG-004: 获取可用服务类型")
        void testGetAvailableServiceTypes() {
            // Given
            Set<String> serviceTypes = Set.of("gpustack", "ollama", "vllm");
            when(configQueryService.getAvailableServiceTypes()).thenReturn(serviceTypes);

            // When
            Set<String> result = configurationService.getAvailableServiceTypes();

            // Then
            assertNotNull(result);
            assertEquals(3, result.size());
            assertTrue(result.contains("gpustack"));
            verify(configQueryService).getAvailableServiceTypes();
        }

        @Test
        @DisplayName("CONFIG-005: 获取指定服务的可用模型")
        void testGetAvailableModels() {
            // Given
            Set<String> models = Set.of("llama2", "mistral", "qwen");
            when(configQueryService.getAvailableModels("gpustack")).thenReturn(models);

            // When
            Set<String> result = configurationService.getAvailableModels("gpustack");

            // Then
            assertNotNull(result);
            assertEquals(3, result.size());
            verify(configQueryService).getAvailableModels("gpustack");
        }

        @Test
        @DisplayName("CONFIG-006: 检查是否存在持久化配置")
        void testHasPersistedConfig() {
            // Given
            when(configQueryService.hasPersistedConfig()).thenReturn(true);

            // When
            boolean result = configurationService.hasPersistedConfig();

            // Then
            assertTrue(result);
            verify(configQueryService).hasPersistedConfig();
        }

        @Test
        @DisplayName("CONFIG-007: 获取追踪配置")
        void testGetTraceConfig() {
            // Given
            TraceConfig traceConfig = mock(TraceConfig.class);
            when(configQueryService.getTraceConfig()).thenReturn(traceConfig);

            // When
            TraceConfig result = configurationService.getTraceConfig();

            // Then
            assertNotNull(result);
            verify(configQueryService).getTraceConfig();
        }
    }

    // ==================== 服务配置更新测试 ====================

    @Nested
    @DisplayName("服务配置更新测试")
    class ServiceConfigUpdateTests {

        @Test
        @DisplayName("CONFIG-008: 使用DTO更新服务配置")
        void testUpdateServiceConfigDto() {
            // Given
            UpdateServiceConfigRequest request = new UpdateServiceConfigRequest();
            request.setAdapter("gpustack");

            when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
            when(storeManager.getConfigByVersion(anyString(), anyInt())).thenReturn(sampleConfig);
            when(configMergeService.getDefaultConfig()).thenReturn(sampleConfig);
            when(serviceConfigUpdateService.buildServiceConfigUpdate(any(), any())).thenReturn(new HashMap<>());
            when(configVersionManager.saveAsNewVersion(any(), anyString(), any())).thenReturn(2);

            // When
            configurationService.updateServiceConfigDto("gpustack", request);

            // Then
            verify(storeManager).saveConfig(anyString(), any());
            verify(configVersionManager).saveAsNewVersion(any(), anyString(), any());
            verify(eventPublisher).publishEvent(any(ConfigSyncEvent.class));
        }
    }

    // ==================== 实例管理测试 ====================

    @Nested
    @DisplayName("实例管理测试")
    class InstanceManagementTests {

        @Test
        @DisplayName("CONFIG-009: 添加服务实例")
        void testAddServiceInstance() {
            // Given
            ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
            instance.setName("test-instance");
            instance.setBaseUrl("http://localhost:8080");

            Map<String, Object> instanceMap = new HashMap<>();
            instanceMap.put("name", "test-instance");
            instanceMap.put("baseUrl", "http://localhost:8080");

            when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
            when(storeManager.getConfigByVersion(anyString(), anyInt())).thenReturn(sampleConfig);
            when(configurationHelper.convertInstanceToMap(any())).thenReturn(instanceMap);
            when(instanceOperationService.addInstance(any(), any())).thenReturn("添加实例: test-instance");
            when(configComparisonService.isConfigurationChanged(any(), any())).thenReturn(true);
            when(configVersionManager.saveAsNewVersion(any(), anyString(), any())).thenReturn(2);

            // When
            configurationService.addServiceInstance("gpustack", instance);

            // Then
            verify(configValidator).isValidServiceType("gpustack");
            verify(instanceOperationService).addInstance(any(), any());
            verify(eventPublisher).publishEvent(any(ConfigSyncEvent.class));
        }

        @Test
        @DisplayName("CONFIG-010: 添加实例到无效服务类型抛出异常")
        void testAddServiceInstance_InvalidServiceType() {
            // Given
            ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
            instance.setName("test-instance");
            when(configValidator.isValidServiceType("invalid")).thenReturn(false);

            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                configurationService.addServiceInstance("invalid", instance);
            });
        }
    }

    // ==================== 批量操作测试 ====================

    @Nested
    @DisplayName("批量操作测试")
    class BatchOperationTests {

        @Test
        @DisplayName("CONFIG-011: 批量更新配置")
        void testBatchUpdateConfigurations() {
            // Given
            Map<String, Object> updates = new HashMap<>();
            updates.put("globalSetting", "newValue");

            when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
            when(storeManager.getConfigByVersion(anyString(), anyInt())).thenReturn(sampleConfig);

            // When
            configurationService.batchUpdateConfigurations(updates);

            // Then
            verify(configVersionManager).saveAsNewVersion(any());
            verify(eventPublisher).publishEvent(any(ConfigSyncEvent.class));
        }

        @Test
        @DisplayName("CONFIG-012: 批量更新服务实例")
        void testBatchUpdateServiceInstances() {
            // Given
            List<ConfigurationService.InstanceOperation> operations = new ArrayList<>();

            ModelRouterProperties.ModelInstance instance1 = new ModelRouterProperties.ModelInstance();
            instance1.setName("instance-1");
            operations.add(new ConfigurationService.InstanceOperation(
                ConfigurationService.InstanceOperationType.ADD,
                null,
                instance1
            ));

            Map<String, Object> instanceMap = new HashMap<>();
            instanceMap.put("name", "instance-1");

            when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
            when(storeManager.getConfigByVersion(anyString(), anyInt())).thenReturn(sampleConfig);
            when(configurationHelper.convertInstanceToMap(any())).thenReturn(instanceMap);
            when(instanceOperationService.addInstance(any(), any())).thenReturn("添加实例: instance-1");
            when(configComparisonService.isConfigurationChanged(any(), any())).thenReturn(true);
            when(configVersionManager.saveAsNewVersion(any(), anyString(), any())).thenReturn(2);

            // When
            configurationService.batchUpdateServiceInstances("gpustack", operations);

            // Then
            verify(instanceOperationService).addInstance(any(), any());
            verify(eventPublisher).publishEvent(any(ConfigSyncEvent.class));
        }

        @Test
        @DisplayName("CONFIG-013: 批量更新实例-包含删除操作")
        void testBatchUpdateServiceInstances_WithDelete() {
            // Given
            List<ConfigurationService.InstanceOperation> operations = new ArrayList<>();
            operations.add(new ConfigurationService.InstanceOperation(
                ConfigurationService.InstanceOperationType.DELETE,
                "instance-to-delete",
                null
            ));

            when(storeManager.getConfigVersions(anyString())).thenReturn(List.of(1));
            when(storeManager.getConfigByVersion(anyString(), anyInt())).thenReturn(sampleConfig);
            when(instanceOperationService.deleteInstance(any(), eq("instance-to-delete")))
                .thenReturn("删除实例: instance-to-delete");
            when(configComparisonService.isConfigurationChanged(any(), any())).thenReturn(true);
            when(configVersionManager.saveAsNewVersion(any(), anyString(), any())).thenReturn(2);

            // When
            configurationService.batchUpdateServiceInstances("gpustack", operations);

            // Then
            verify(instanceOperationService).deleteInstance(any(), eq("instance-to-delete"));
        }
    }

    // ==================== 追踪配置管理测试 ====================

    @Nested
    @DisplayName("追踪配置管理测试")
    class TracingConfigTests {

        @Test
        @DisplayName("CONFIG-014: 获取追踪采样配置")
        void testGetTracingSamplingConfig() {
            // Given
            Map<String, Object> samplingConfig = new HashMap<>();
            samplingConfig.put("sampleRate", 0.1);
            when(tracingConfigManager.getTracingSamplingConfig()).thenReturn(samplingConfig);

            // When
            Map<String, Object> result = configurationService.getTracingSamplingConfig();

            // Then
            assertNotNull(result);
            assertEquals(0.1, result.get("sampleRate"));
            verify(tracingConfigManager).getTracingSamplingConfig();
        }

        @Test
        @DisplayName("CONFIG-015: 更新追踪采样配置")
        void testUpdateTracingSamplingConfig() {
            // Given
            Map<String, Object> samplingConfig = new HashMap<>();
            samplingConfig.put("sampleRate", 0.2);

            // When
            configurationService.updateTracingSamplingConfig(samplingConfig, true);

            // Then
            verify(tracingConfigManager).updateTracingSamplingConfig(samplingConfig, true);
            verify(eventPublisher).publishEvent(any(ConfigSyncEvent.class));
        }

        @Test
        @DisplayName("CONFIG-016: 删除追踪配置")
        void testDeleteTraceConfig() {
            // When
            configurationService.deleteTraceConfig(true);

            // Then
            verify(tracingConfigManager).deleteTraceConfig(true);
            verify(eventPublisher).publishEvent(any(ConfigSyncEvent.class));
        }
    }

    // ==================== 重置操作测试 ====================

    @Nested
    @DisplayName("重置操作测试")
    class ResetOperationTests {

        @Test
        @DisplayName("CONFIG-017: 重置为默认配置")
        void testResetToDefaultConfig() {
            // When
            configurationService.resetToDefaultConfig();

            // Then
            verify(configMergeService).resetToYamlConfig();
            verify(eventPublisher).publishEvent(any(ConfigSyncEvent.class));
        }
    }

    // ==================== 版本清理测试 ====================

    @Nested
    @DisplayName("版本清理测试")
    class VersionCleanupTests {

        @Test
        @DisplayName("CONFIG-018: 清理版本")
        void testCleanVersion() {
            // When
            configurationService.cleanVersion();

            // Then
            verify(configVersionManager).cleanVersion();
        }
    }

    // ==================== 延迟注入测试 ====================

    @Nested
    @DisplayName("延迟注入测试")
    class LazyInjectionTests {

        @Test
        @DisplayName("CONFIG-019: 设置ModelServiceRegistry")
        void testSetModelServiceRegistry() {
            // Given
            ModelServiceRegistry registry = mock(ModelServiceRegistry.class);

            // When
            configurationService.setModelServiceRegistry(registry);

            // Then - 无异常即成功
            assertDoesNotThrow(() -> configurationService.setModelServiceRegistry(registry));
        }

        @Test
        @DisplayName("CONFIG-020: 设置ConfigSyncService")
        void testSetConfigSyncService() {
            // Given
            ConfigSyncService syncService = mock(ConfigSyncService.class);

            // When
            configurationService.setConfigSyncService(syncService);

            // Then - 无异常即成功
            assertDoesNotThrow(() -> configurationService.setConfigSyncService(syncService));
        }
    }
}
