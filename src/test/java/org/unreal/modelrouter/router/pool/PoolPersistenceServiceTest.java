package org.unreal.modelrouter.router.pool;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.unreal.modelrouter.persistence.store.MemoryStoreManager;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.router.pool.model.PoolDefinition;
import org.unreal.modelrouter.router.pool.model.PoolMember;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * PoolPersistenceService 持久化服务测试
 * save/load 往返、异常降级、删除
 */
@DisplayName("PoolPersistenceService 持久化服务测试")
class PoolPersistenceServiceTest {

    private StoreManager storeManager;
    private PoolPersistenceService service;

    @BeforeEach
    void setUp() {
        storeManager = Mockito.mock(StoreManager.class);
        service = new PoolPersistenceService(storeManager, new ObjectMapper());
    }

    private PoolDefinition pool(final String poolName, final String serviceType) {
        PoolDefinition p = new PoolDefinition();
        p.setPoolName(poolName);
        p.setName(poolName);
        p.setServiceType(serviceType);
        p.setStrategy("weighted-random");
        PoolMember m = new PoolMember();
        m.setInstanceId("inst-1");
        m.setWeight(9);
        p.setMembers(List.of(m));
        return p;
    }

    @Nested
    @DisplayName("保存资源池测试")
    class SaveTests {

        @Test
        @DisplayName("POOL-PERSIST-001: 保存全部资源池写入 StoreManager")
        void testSaveAll_writesToStore() {
            service.saveAll(List.of(pool("auto-model", "chat"), pool("embed-pool", "embedding")));

            verify(storeManager).saveConfig(eq("pool_definitions"), any(Map.class));
        }

        @Test
        @DisplayName("POOL-PERSIST-002: 保存失败降级为仅内存模式,不抛异常")
        void testSaveAll_storeFailure_degrades() {
            doThrow(new RuntimeException("store down")).when(storeManager)
                    .saveConfig(any(String.class), any(Map.class));

            assertDoesNotThrow(() -> service.saveAll(List.of(pool("auto-model", "chat"))));
        }

        @Test
        @DisplayName("POOL-PERSIST-003: 保存单条资源池(同 poolName 覆盖)")
        void testSave_single() {
            when(storeManager.getConfig("pool_definitions")).thenReturn(new HashMap<>());

            service.save(pool("auto-model", "chat"));

            verify(storeManager).saveConfig(eq("pool_definitions"), any(Map.class));
        }
    }

    @Nested
    @DisplayName("加载资源池测试")
    class LoadTests {

        @Test
        @DisplayName("POOL-PERSIST-004: 加载空配置返回空列表")
        void testLoadAll_empty() {
            when(storeManager.getConfig("pool_definitions")).thenReturn(null);

            assertTrue(service.loadAll().isEmpty());
        }

        @Test
        @DisplayName("POOL-PERSIST-005: 加载 JSON 序列化的资源池往返")
        void testLoadAll_roundTrip() throws Exception {
            PoolDefinition p = pool("auto-model", "chat");
            Map<String, Object> stored = new HashMap<>();
            stored.put(p.getPoolName(), new ObjectMapper().convertValue(p, Map.class));
            when(storeManager.getConfig("pool_definitions")).thenReturn(stored);

            List<PoolDefinition> loaded = service.loadAll();

            assertEquals(1, loaded.size());
            PoolDefinition back = loaded.get(0);
            assertEquals("auto-model", back.getPoolName());
            assertEquals("chat", back.getServiceType());
            assertEquals(1, back.getMembers().size());
            assertEquals("inst-1", back.getMembers().get(0).getInstanceId());
            assertEquals(9, back.getMembers().get(0).getWeight());
        }

        @Test
        @DisplayName("POOL-PERSIST-006: 坏数据跳过不中断")
        void testLoadAll_badData_skipped() throws Exception {
            Map<String, Object> stored = new HashMap<>();
            stored.put("good", new ObjectMapper().convertValue(pool("auto-model", "chat"), Map.class));
            stored.put("bad", "not-a-pool");
            when(storeManager.getConfig("pool_definitions")).thenReturn(stored);

            List<PoolDefinition> loaded = service.loadAll();

            assertEquals(1, loaded.size());
            assertEquals("auto-model", loaded.get(0).getPoolName());
        }
    }

    @Nested
    @DisplayName("删除资源池测试")
    class RemoveTests {

        @Test
        @DisplayName("POOL-PERSIST-007: 删除存在的资源池（落库内容必须已摘掉它）")
        void testRemove_existing() {
            Map<String, Object> stored = new HashMap<>();
            stored.put("auto-model", pool("auto-model", "chat"));
            when(storeManager.getConfig("pool_definitions")).thenReturn(stored);

            assertTrue(service.remove("auto-model"), "持久化层确有该条目时 remove 必须返回 true");

            // 只断言「saveConfig 被调用过」是不够的：调用方传旧内容过来同样满足。
            // 这里钉住**载荷**——落库的那张表里不能再有 auto-model。
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
            verify(storeManager).saveConfig(eq("pool_definitions"), captor.capture());
            assertFalse(captor.getValue().containsKey("auto-model"),
                    "删除后落库的表里不应再有 auto-model，实际=" + captor.getValue().keySet());
        }

        @Test
        @DisplayName("POOL-PERSIST-009: 删除最后一个资源池后重新加载为空（真实 StoreManager 往返）")
        void testRemove_lastPool_roundTripEmpty() {
            // 用真实实现而不是 Mockito 桩：桩不会复现「空表被静默丢弃」这类 store 侧行为，
            // 正是它让本仓长期绿着漏掉「删最后一个条目不生效」。
            PoolPersistenceService real = new PoolPersistenceService(new MemoryStoreManager(), new ObjectMapper());
            real.save(pool("only-pool", "chat"));
            assertEquals(1, real.loadAll().size(), "前置：已有一条");

            assertTrue(real.remove("only-pool"), "真实 StoreManager 下删除也必须返回 true");

            assertTrue(real.loadAll().isEmpty(),
                    "删掉最后一个池后必须读回空集合；非空即说明空表被丢弃、条目会复活");
        }

        @Test
        @DisplayName("POOL-PERSIST-008: 删除不存在的资源池不触发保存")
        void testRemove_missing_noSave() {
            when(storeManager.getConfig("pool_definitions")).thenReturn(new HashMap<>());

            // #708: 返回值是调用方区分「YAML 只读 / 持久化层没有该条目」的唯一信号，
            // 之前 remove 是 void，静默 no-op 也照样回 200。
            assertFalse(service.remove("nonexistent"),
                    "持久化层没有该条目时 remove 必须返回 false");

            verify(storeManager, never()).saveConfig(any(String.class), any(Map.class));
        }
    }
}
