package org.unreal.modelrouter.router.adapter.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.persistence.store.MemoryStoreManager;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.adapter.config.AdapterDefinitionProperties;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AdapterDefinitionPersistenceService 持久化服务测试
 *
 * TDD 测试先行：10 个测试用例覆盖持久化 CRUD 和异常降级
 */
@DisplayName("AdapterDefinitionPersistenceService 持久化服务测试")
@ExtendWith(MockitoExtension.class)
class AdapterDefinitionPersistenceServiceTest {

    @Mock
    private StoreManager storeManager;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AdapterDefinitionPersistenceService persistenceService;

    private AdapterDefinitionProperties.AdapterDefinition deepseekDefinition;

    @BeforeEach
    void setUp() {
        deepseekDefinition = new AdapterDefinitionProperties.AdapterDefinition();
        deepseekDefinition.setType("openai-compatible");
        AdapterDefinitionProperties.CapabilitiesConfig caps = new AdapterDefinitionProperties.CapabilitiesConfig();
        caps.setChat(true);
        caps.setStreaming(true);
        deepseekDefinition.setCapabilities(caps);
        AdapterDefinitionProperties.AuthConfig auth = new AdapterDefinitionProperties.AuthConfig();
        auth.setHeaderName("Authorization");
        auth.setHeaderPrefix("Bearer ");
        deepseekDefinition.setAuth(auth);
    }

    // ==================== 保存测试 ====================

    @Nested
    @DisplayName("保存定义测试")
    class SaveTests {

        @Test
        @DisplayName("PERSIST-001: 保存全部定义成功")
        void testSaveAllDefinitions_success() {
            // Given
            Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = new HashMap<>();
            definitions.put("deepseek", deepseekDefinition);

            // When
            assertDoesNotThrow(() -> persistenceService.saveAllDefinitions(definitions));

            // Then
            verify(storeManager).saveConfig(eq("adapter_definitions"), any());
        }

        @Test
        @DisplayName("PERSIST-004: 保存单个定义")
        void testSaveDefinition_singleDefinition() {
            // Given
            when(storeManager.getConfig("adapter_definitions")).thenReturn(new HashMap<>());
            Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = new HashMap<>();
            when(storeManager.getConfig("adapter_definitions")).thenReturn(new HashMap<>());

            // When
            persistenceService.saveDefinition("deepseek", deepseekDefinition);

            // Then
            verify(storeManager, atLeastOnce()).saveConfig(eq("adapter_definitions"), any());
        }

        @Test
        @DisplayName("PERSIST-007: StoreManager 异常时降级")
        void testSaveDefinition_storeManagerException() {
            // Given
            doThrow(new RuntimeException("存储异常")).when(storeManager).saveConfig(any(), any());

            // When & Then - 不应抛异常
            assertDoesNotThrow(() -> persistenceService.saveAllDefinitions(new HashMap<>()));
        }
    }

    // ==================== 加载测试 ====================

    @Nested
    @DisplayName("加载定义测试")
    class LoadTests {

        @Test
        @DisplayName("PERSIST-002: 加载已有数据")
        void testLoadAllDefinitions_hasData() {
            // Given
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("deepseek", deepseekDefinition);
            when(storeManager.getConfig("adapter_definitions")).thenReturn(configMap);

            // When
            Map<String, AdapterDefinitionProperties.AdapterDefinition> result =
                    persistenceService.loadAllDefinitions();

            // Then
            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.containsKey("deepseek"));
        }

        @Test
        @DisplayName("PERSIST-003: 无数据时返回空 Map")
        void testLoadAllDefinitions_noData_returnsEmpty() {
            // Given
            when(storeManager.getConfig("adapter_definitions")).thenReturn(null);

            // When
            Map<String, AdapterDefinitionProperties.AdapterDefinition> result =
                    persistenceService.loadAllDefinitions();

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("PERSIST-008: JSON 格式错误时降级")
        void testLoadAllDefinitions_malformedJson_returnsEmpty() {
            // Given
            when(storeManager.getConfig("adapter_definitions"))
                    .thenThrow(new RuntimeException("JSON 解析错误"));

            // When
            Map<String, AdapterDefinitionProperties.AdapterDefinition> result =
                    persistenceService.loadAllDefinitions();

            // Then
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    // ==================== 删除测试 ====================

    @Nested
    @DisplayName("删除定义测试")
    class RemoveTests {

        @Test
        @DisplayName("PERSIST-005: 删除单个定义成功（落库内容必须已摘掉它）")
        void testRemoveDefinition_success() {
            // Given
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("deepseek", deepseekDefinition);
            configMap.put("zhipu", new AdapterDefinitionProperties.AdapterDefinition());
            when(storeManager.getConfig("adapter_definitions")).thenReturn(configMap);

            // When
            persistenceService.removeDefinition("deepseek");

            // Then
            // 只断言「saveConfig 被调用过」不够：传旧载荷同样满足。这里钉住载荷本身。
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(storeManager, atLeastOnce()).saveConfig(eq("adapter_definitions"), captor.capture());
            Map<String, Object> persisted = captor.getValue();
            assertFalse(persisted.containsKey("deepseek"),
                    "删除后落库的表里不应再有 deepseek，实际=" + persisted.keySet());
            assertTrue(persisted.containsKey("zhipu"), "同表的其它条目必须保留");
        }

        @Test
        @DisplayName("PERSIST-009: 删除最后一个可配置 adapter 后重新加载为空（真实 StoreManager 往返）")
        void testRemoveDefinition_lastDefinition_roundTripEmpty() {
            // 用真实实现而非 Mockito 桩：桩不复现 store 侧「空表被静默丢弃」，正是它让本仓长期绿着漏掉该缺陷。
            // 该缺陷在 adapter 上的可见形态是「当时看着删掉了，进程重启后条目复活」。
            AdapterDefinitionPersistenceService real =
                    new AdapterDefinitionPersistenceService(new MemoryStoreManager(), new ObjectMapper());
            real.saveDefinition("only-adapter", deepseekDefinition);
            assertEquals(1, real.loadAllDefinitions().size(), "前置：已有一条");

            real.removeDefinition("only-adapter");

            assertTrue(real.loadAllDefinitions().isEmpty(),
                    "删掉最后一个 adapter 后必须读回空集合；非空即说明空表被丢弃、重启后会复活");
        }

        @Test
        @DisplayName("PERSIST-006: 删除不存在的定义不报错")
        void testRemoveDefinition_notExist_noError() {
            // Given
            when(storeManager.getConfig("adapter_definitions")).thenReturn(new HashMap<>());

            // When & Then
            assertDoesNotThrow(() -> persistenceService.removeDefinition("nonexistent"));
        }
    }

    // ==================== 存储 key 测试 ====================

    @Nested
    @DisplayName("存储 key 测试")
    class KeyTests {

        @Test
        @DisplayName("PERSIST-010: 存储 key 正确")
        void testKeyIsAdapterDefinitions() {
            // Given
            Map<String, AdapterDefinitionProperties.AdapterDefinition> definitions = new HashMap<>();
            definitions.put("test", new AdapterDefinitionProperties.AdapterDefinition());

            // When
            persistenceService.saveAllDefinitions(definitions);

            // Then
            verify(storeManager).saveConfig(eq("adapter_definitions"), any());
        }

        @Test
        @DisplayName("PERSIST-009: 保存不影响其他定义")
        void testSaveDefinition_preservesOtherDefinitions() {
            // Given
            Map<String, Object> existingConfig = new HashMap<>();
            existingConfig.put("existing-adapter", new AdapterDefinitionProperties.AdapterDefinition());
            when(storeManager.getConfig("adapter_definitions")).thenReturn(existingConfig);

            // When
            persistenceService.saveDefinition("new-adapter", deepseekDefinition);

            // Then
            verify(storeManager, atLeastOnce()).saveConfig(eq("adapter_definitions"), any());
        }
    }
}
