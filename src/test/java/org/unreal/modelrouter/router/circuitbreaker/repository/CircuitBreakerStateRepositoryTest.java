package org.unreal.modelrouter.router.circuitbreaker.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.LockFreeCircuitBreaker;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * CircuitBreakerStateRepository 单元测试
 *
 * 测试内容：
 * 1. 状态保存测试
 * 2. 状态加载测试
 * 3. 状态恢复测试
 * 4. 状态删除测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CircuitBreakerStateRepository 测试")
class CircuitBreakerStateRepositoryTest {

    @Mock
    private StoreManager storeManager;

    private ObjectMapper objectMapper;
    private CircuitBreakerStateRepository repository;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        repository = new CircuitBreakerStateRepository(storeManager, objectMapper);
    }

    @Test
    @DisplayName("测试 1: 保存 LockFreeCircuitBreaker 状态")
    void testSaveLockFreeCircuitBreaker() {
        // 创建熔断器
        LockFreeCircuitBreaker circuitBreaker = new LockFreeCircuitBreaker("test-instance-1", 3, 5000, 2);

        // 保存状态
        boolean result = repository.save(circuitBreaker);

        assertTrue(result);
        verify(storeManager, times(1)).saveConfig(anyString(), any(Map.class));
    }

    @Test
    @DisplayName("测试 2: 保存非 LockFreeCircuitBreaker 返回 false")
    void testSaveNonLockFreeCircuitBreaker() {
        // 创建非 LockFreeCircuitBreaker 实例
        CircuitBreaker otherCircuitBreaker = mock(CircuitBreaker.class);

        boolean result = repository.save(otherCircuitBreaker);

        assertFalse(result);
        verify(storeManager, never()).saveConfig(anyString(), any());
    }

    @Test
    @DisplayName("测试 3: 从缓存加载状态")
    void testLoadFromCache() {
        // 先保存一个状态
        LockFreeCircuitBreaker circuitBreaker = new LockFreeCircuitBreaker("test-instance-2", 3, 5000, 2);
        repository.save(circuitBreaker);

        // 从缓存加载
        Map<String, Object> stateData = repository.load("test-instance-2");

        assertNotNull(stateData);
        assertEquals("test-instance-2", stateData.get("instanceId"));
    }

    @Test
    @DisplayName("测试 4: 从 StoreManager 加载状态")
    void testLoadFromStoreManager() {
        // 准备 mock 数据
        Map<String, Object> mockData = new HashMap<>();
        mockData.put("instanceId", "test-instance-3");
        mockData.put("state", "CLOSED");
        when(storeManager.getConfig(anyString())).thenReturn(mockData);

        // 加载状态
        Map<String, Object> stateData = repository.load("test-instance-3");

        assertNotNull(stateData);
        assertEquals("test-instance-3", stateData.get("instanceId"));
        verify(storeManager, times(1)).getConfig(anyString());
    }

    @Test
    @DisplayName("测试 5: 加载不存在的状态返回 null")
    void testLoadNonExistent() {
        when(storeManager.getConfig(anyString())).thenReturn(null);

        Map<String, Object> stateData = repository.load("non-existent");

        assertNull(stateData);
    }

    @Test
    @DisplayName("测试 6: 恢复熔断器状态")
    void testRestoreCircuitBreaker() {
        // 创建并保存熔断器状态
        LockFreeCircuitBreaker original = new LockFreeCircuitBreaker("test-instance-4", 3, 5000, 2);
        original.onFailure();
        original.onFailure();
        original.onFailure(); // 打开熔断器

        repository.save(original);

        // 创建新熔断器并恢复状态
        LockFreeCircuitBreaker restored = new LockFreeCircuitBreaker("test-instance-4", 3, 5000, 2);
        boolean result = repository.restore(restored);

        assertTrue(result);
        assertEquals(CircuitBreaker.State.OPEN, restored.getState());
    }

    @Test
    @DisplayName("测试 7: 恢复非 LockFreeCircuitBreaker 返回 false")
    void testRestoreNonLockFreeCircuitBreaker() {
        CircuitBreaker otherCircuitBreaker = mock(CircuitBreaker.class);

        boolean result = repository.restore(otherCircuitBreaker);

        assertFalse(result);
    }

    @Test
    @DisplayName("测试 8: 删除状态")
    void testDelete() {
        // 先保存状态
        LockFreeCircuitBreaker circuitBreaker = new LockFreeCircuitBreaker("test-instance-5", 3, 5000, 2);
        repository.save(circuitBreaker);

        // 删除状态
        boolean result = repository.delete("test-instance-5");

        assertTrue(result);
        verify(storeManager, times(1)).deleteConfig(anyString());

        // 缓存应该被清除
        assertNull(repository.load("test-instance-5"));
    }

    @Test
    @DisplayName("测试 9: 清除所有状态")
    void testClearAll() {
        // 保存多个状态
        repository.save(new LockFreeCircuitBreaker("instance-1", 3, 5000, 2));
        repository.save(new LockFreeCircuitBreaker("instance-2", 3, 5000, 2));

        // 清除所有
        repository.clearAll();

        assertEquals(0, repository.getCacheSize());
    }

    @Test
    @DisplayName("测试 10: 检查状态是否存在")
    void testExists() {
        // 保存状态
        LockFreeCircuitBreaker circuitBreaker = new LockFreeCircuitBreaker("test-instance-6", 3, 5000, 2);
        repository.save(circuitBreaker);

        // 检查存在
        assertTrue(repository.exists("test-instance-6"));
        assertFalse(repository.exists("non-existent"));
    }

    @Test
    @DisplayName("测试 11: 获取缓存大小")
    void testGetCacheSize() {
        assertEquals(0, repository.getCacheSize());

        repository.save(new LockFreeCircuitBreaker("instance-1", 3, 5000, 2));
        assertEquals(1, repository.getCacheSize());

        repository.save(new LockFreeCircuitBreaker("instance-2", 3, 5000, 2));
        assertEquals(2, repository.getCacheSize());
    }

    @Test
    @DisplayName("测试 12: 获取所有状态")
    void testGetAllStates() {
        // 保存状态
        repository.save(new LockFreeCircuitBreaker("instance-1", 3, 5000, 2));
        repository.save(new LockFreeCircuitBreaker("instance-2", 3, 5000, 2));

        // 获取所有状态
        Map<String, Map<String, Object>> allStates = repository.getAllStates();

        assertEquals(2, allStates.size());
        assertTrue(allStates.containsKey("instance-1"));
        assertTrue(allStates.containsKey("instance-2"));
    }

    @Test
    @DisplayName("测试 13: 从 StoreManager 检查存在")
    void testExistsFromStoreManager() {
        when(storeManager.exists(anyString())).thenReturn(true);

        // 清除缓存，强制从 StoreManager 检查
        repository.clearAll();
        assertTrue(repository.exists("instance-from-store"));

        verify(storeManager, times(1)).exists(anyString());
    }
}
