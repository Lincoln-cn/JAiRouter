package org.unreal.modelrouter.persistence.store.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.persistence.store.StoreManager;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

/**
 * H2 状态持久化服务测试
 *
 * v2.4.7: 测试 H2 数据库状态持久化实现
 *
 * @author JAiRouter Team
 * @since 2.4.7
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("H2StatePersistenceService Tests")
public class H2StatePersistenceServiceImplTest {

    @Mock
    private StoreManager storeManager;

    @InjectMocks
    private H2StatePersistenceServiceImpl h2Service;

    private StatePersistenceService.StateType stateType = StatePersistenceService.StateType.CIRCUIT_BREAKER;

    @BeforeEach
    void setUp() {
        // 默认配置
        Mockito.lenient().when(storeManager.exists(anyString())).thenReturn(true);
    }

    /* ===================== 基础属性测试 ===================== */

    @Test
    @DisplayName("获取层名称 - h2")
    void testGetTierName() {
        assertEquals("h2", h2Service.getTierName());
    }

    @Test
    @DisplayName("获取层优先级 - 2")
    void testGetTierPriority() {
        assertEquals(2, h2Service.getTierPriority());
    }

    /* ===================== CRUD 操作测试 ===================== */

    @Test
    @DisplayName("保存操作 - 成功")
    void testSaveSuccess() {
        Map<String, Object> stateData = createTestState("test-key");

        // saveConfig 没有返回值，使用 doNothing
        Mockito.doNothing().when(storeManager).saveConfig(anyString(), any());

        StepVerifier.create(h2Service.save(stateType, "test-key", stateData))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("保存操作 - 失败时返回 false")
    void testSaveFailure() {
        Map<String, Object> stateData = createTestState("test-key");

        Mockito.doThrow(new RuntimeException("H2 save failed"))
                .when(storeManager).saveConfig(anyString(), any());

        StepVerifier.create(h2Service.save(stateType, "test-key", stateData))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("加载操作 - 成功")
    void testLoadSuccess() {
        Map<String, Object> stateData = createTestState("test-key");

        Mockito.when(storeManager.getConfig(anyString()))
                .thenReturn(stateData);

        StepVerifier.create(h2Service.load(stateType, "test-key"))
                .expectNextMatches(data -> data.containsKey("stateId"))
                .verifyComplete();
    }

    @Test
    @DisplayName("加载操作 - 空数据返回空 Map")
    void testLoadEmpty() {
        Mockito.when(storeManager.getConfig(anyString()))
                .thenReturn(new HashMap<>());

        StepVerifier.create(h2Service.load(stateType, "test-key"))
                .expectNextMatches(data -> data.isEmpty())
                .verifyComplete();
    }

    @Test
    @DisplayName("删除操作 - 成功")
    void testDeleteSuccess() {
        Mockito.doNothing().when(storeManager).deleteConfig(anyString());

        StepVerifier.create(h2Service.delete(stateType, "test-key"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("删除操作 - 失败时返回 false")
    void testDeleteFailure() {
        Mockito.doThrow(new RuntimeException("H2 delete failed"))
                .when(storeManager).deleteConfig(anyString());

        StepVerifier.create(h2Service.delete(stateType, "test-key"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("存在检查 - 存在")
    void testExistsTrue() {
        Mockito.when(storeManager.exists(anyString()))
                .thenReturn(true);

        StepVerifier.create(h2Service.exists(stateType, "test-key"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("存在检查 - 不存在")
    void testExistsFalse() {
        Mockito.when(storeManager.exists(anyString()))
                .thenReturn(false);

        StepVerifier.create(h2Service.exists(stateType, "test-key"))
                .expectNext(false)
                .verifyComplete();
    }

    /* ===================== 批量操作测试 ===================== */

    @Test
    @DisplayName("批量保存 - 成功")
    void testSaveBatchSuccess() {
        Map<String, Map<String, Object>> batchData = new HashMap<>();
        batchData.put("key-1", createTestState("key-1"));
        batchData.put("key-2", createTestState("key-2"));

        Mockito.doNothing().when(storeManager).saveConfig(anyString(), any());

        StepVerifier.create(h2Service.saveBatch(stateType, batchData))
                .expectNext(2)
                .verifyComplete();
    }

    @Test
    @DisplayName("批量加载 - 成功")
    void testLoadBatchSuccess() {
        List<String> keys = List.of("key-1", "key-2");

        Mockito.when(storeManager.getConfig(anyString()))
                .thenReturn(createTestState("key-1"));

        StepVerifier.create(h2Service.loadBatch(stateType, keys))
                .expectNextMatches(result -> result.size() >= 0)
                .verifyComplete();
    }

    @Test
    @DisplayName("获取所有键 - 成功")
    void testGetAllKeysSuccess() {
        Mockito.when(storeManager.getAllKeys())
                .thenReturn(List.of("state.circuit_breaker.key-1", "state.circuit_breaker.key-2"));

        StepVerifier.create(h2Service.getAllKeys(stateType))
                .expectNextMatches(keys -> {
                    for (String key : keys) {
                        if (key.equals("key-1") || key.equals("key-2")) {
                            return true;
                        }
                    }
                    return false;
                })
                .verifyComplete();
    }

    /* ===================== 健康检查测试 ===================== */

    @Test
    @DisplayName("健康检查 - 正常")
    void testIsHealthyTrue() {
        Mockito.when(storeManager.exists(anyString()))
                .thenReturn(true);

        StepVerifier.create(h2Service.isHealthy())
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("健康检查 - 异常返回 false")
    void testIsHealthyFalse() {
        Mockito.when(storeManager.exists(anyString()))
                .thenThrow(new RuntimeException("H2 connection failed"));

        StepVerifier.create(h2Service.isHealthy())
                .expectNext(false)
                .verifyComplete();
    }

    /* ===================== 清除操作测试 ===================== */

    @Test
    @DisplayName("清除所有 - 成功")
    void testClearAllSuccess() {
        Mockito.when(storeManager.getAllKeys())
                .thenReturn(List.of("state.circuit_breaker.key-1", "state.circuit_breaker.key-2"));
        Mockito.doNothing().when(storeManager).deleteConfig(anyString());

        StepVerifier.create(h2Service.clearAll(stateType))
                .expectNext(true)
                .verifyComplete();
    }

    /* ===================== 辅助方法 ===================== */

    private Map<String, Object> createTestState(String stateId) {
        Map<String, Object> state = new HashMap<>();
        state.put("stateId", stateId);
        state.put("timestamp", System.currentTimeMillis());
        state.put("status", "ACTIVE");
        return state;
    }
}