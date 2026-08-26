package org.unreal.modelrouter.router.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.checker.ServiceStateManager;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;
import org.unreal.modelrouter.router.pool.PoolDefinitionProperties;
import org.unreal.modelrouter.router.pool.PoolPersistenceService;
import org.unreal.modelrouter.router.pool.PoolSelector;
import org.unreal.modelrouter.router.pool.model.PoolDefinition;
import org.unreal.modelrouter.router.pool.model.PoolMember;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * PoolConfigController 资源池 API 测试
 * CRUD、校验、重名 409、未知 404、YAML 合并语义
 */
@DisplayName("PoolConfigController 资源池 API 测试")
class PoolConfigControllerTest {

    private PoolDefinitionProperties poolProperties;
    private InMemoryPoolPersistence persistenceService;
    private PoolSelector poolSelector;
    private PoolConfigController controller;

    /** 内存版持久化,避免依赖 StoreManager */
    private static class InMemoryPoolPersistence extends PoolPersistenceService {
        private final List<PoolDefinition> store = new ArrayList<>();

        InMemoryPoolPersistence() {
            super(null, null);
        }

        @Override
        public void saveAll(final List<PoolDefinition> pools) {
            store.clear();
            store.addAll(pools);
        }

        @Override
        public List<PoolDefinition> loadAll() {
            return new ArrayList<>(store);
        }

        @Override
        public void save(final PoolDefinition pool) {
            store.removeIf(p -> p.getPoolName() != null && p.getPoolName().equals(pool.getPoolName()));
            store.add(pool);
        }

        @Override
        public void remove(final String poolName) {
            store.removeIf(p -> p.getPoolName() != null && p.getPoolName().equals(poolName));
        }
    }

    @BeforeEach
    void setUp() {
        poolProperties = new PoolDefinitionProperties();
        persistenceService = new InMemoryPoolPersistence();
        poolSelector = new PoolSelector(mock(ServiceStateManager.class), mock(CircuitBreakerManager.class));
        controller = new PoolConfigController(
                poolProperties, persistenceService, poolSelector, new ServiceTypeResolver());
    }

    private PoolDefinition validPool(final String poolName) {
        PoolDefinition p = new PoolDefinition();
        p.setPoolName(poolName);
        p.setName(poolName);
        p.setServiceType("chat");
        p.setStrategy("weighted-random");
        PoolMember m = new PoolMember();
        m.setInstanceId("inst-gpt");
        m.setWeight(9);
        p.setMembers(List.of(m));
        return p;
    }

    @Nested
    @DisplayName("创建资源池测试")
    class CreateTests {

        @Test
        @DisplayName("POOL-API-001: 创建资源池成功")
        void testCreate_success() {
            ResponseEntity<RouterResponse<PoolDefinition>> response =
                    controller.createPool(validPool("auto-model"));

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertEquals("auto-model", response.getBody().getData().getPoolName());
        }

        @Test
        @DisplayName("POOL-API-002: 池名为空返回 400")
        void testCreate_blankName_400() {
            PoolDefinition p = validPool("auto-model");
            p.setPoolName("  ");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createPool(p));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("POOL-API-003: 非法 serviceType 返回 400")
        void testCreate_invalidServiceType_400() {
            PoolDefinition p = validPool("auto-model");
            p.setServiceType("bogus");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createPool(p));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("POOL-API-004: 不支持的策略返回 400")
        void testCreate_unsupportedStrategy_400() {
            PoolDefinition p = validPool("auto-model");
            p.setStrategy("magic");
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createPool(p));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        }

        @Test
        @DisplayName("POOL-API-005: 重名池返回 409")
        void testCreate_duplicate_409() {
            controller.createPool(validPool("auto-model"));
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.createPool(validPool("auto-model")));
            assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        }
    }

    @Nested
    @DisplayName("查询与更新测试")
    class QueryUpdateTests {

        @Test
        @DisplayName("POOL-API-006: 创建后列表包含该池")
        void testList_containsCreated() {
            controller.createPool(validPool("auto-model"));

            List<PoolDefinition> pools = controller.getAllPools().getBody().getData();

            assertEquals(1, pools.size());
            assertEquals("auto-model", pools.get(0).getPoolName());
        }

        @Test
        @DisplayName("POOL-API-007: 更新池生效")
        void testUpdate_updatesPool() {
            PoolDefinition created = controller.createPool(validPool("auto-model")).getBody().getData();
            PoolDefinition updated = validPool("auto-model");
            updated.setStrategy("least-connections");

            controller.updatePool(created.getPoolName(), updated);

            assertEquals("least-connections", controller.getPool("auto-model").getBody().getData().getStrategy());
        }

        @Test
        @DisplayName("POOL-API-008: 更新不存在的池返回 404")
        void testUpdate_missing_404() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.updatePool("nonexistent", validPool("auto-model")));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("POOL-API-009: 获取不存在的池返回 404")
        void testGet_missing_404() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.getPool("nonexistent"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }
    }

    @Nested
    @DisplayName("删除与 YAML 合并测试")
    class DeleteMergeTests {

        @Test
        @DisplayName("POOL-API-010: 删除池生效")
        void testDelete_removesPool() {
            controller.createPool(validPool("auto-model"));

            controller.deletePool("auto-model");

            assertTrue(controller.getAllPools().getBody().getData().isEmpty());
        }

        @Test
        @DisplayName("POOL-API-011: 删除不存在的池返回 404")
        void testDelete_missing_404() {
            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> controller.deletePool("nonexistent"));
            assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        }

        @Test
        @DisplayName("POOL-API-012: YAML 默认池 + 持久化合并,持久化同池名覆盖")
        void testYamlMerge_persistedOverridesYaml() {
            PoolDefinition yamlPool = validPool("auto-model");
            yamlPool.setStrategy("round-robin");
            poolProperties.setPools(List.of(yamlPool));

            // 控制器构造时已加载 YAML;新建同名持久化池覆盖
            controller.createPool(validPool("auto-model"));

            List<PoolDefinition> pools = controller.getAllPools().getBody().getData();
            assertEquals(1, pools.size());
            assertEquals("weighted-random", pools.get(0).getStrategy(),
                    "持久化池应覆盖 YAML 默认池");
        }

        @Test
        @DisplayName("POOL-API-013: YAML 池与持久化池并存")
        void testYamlMerge_distinctNames() {
            poolProperties.setPools(List.of(validPool("yaml-pool")));
            controller = new PoolConfigController(
                    poolProperties, persistenceService, poolSelector, new ServiceTypeResolver());
            controller.createPool(validPool("auto-model"));

            List<PoolDefinition> pools = controller.getAllPools().getBody().getData();
            assertEquals(2, pools.size());
        }
    }
}
