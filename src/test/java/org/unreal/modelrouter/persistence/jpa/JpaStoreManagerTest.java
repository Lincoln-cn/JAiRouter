package org.unreal.modelrouter.persistence.jpa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.unreal.modelrouter.persistence.jpa.entity.ConfigEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ConfigRepository;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * {@link JpaStoreManager} 的 store 契约守护。
 *
 * <p><b>为什么需要这个类（缺陷史）</b>：{@code saveConfig} 曾经写成
 * {@code if (config == null || config.isEmpty()) return;} —— 空表静默不落库。
 * 而本仓的 store 契约是「把这张表原样落库」：{@code BaseStoreManager} /
 * {@code MemoryStoreManager} 与 {@code FileStoreManager.doSaveConfig} 都是照原样写的。
 * JpaStoreManager 是唯一的偏离者，于是「把集合删空」这个意图无处表达：
 * 上层普遍是 {@code loadAll() → removeIf() → saveAll()}，删掉最后一个条目时正好传空表，
 * 落库被跳过 ⇒ **旧值仍在库里、条目复活，而 REST 接口仍返回 200 成功**。
 *
 * <p>这条路径此前没有任何测试：{@code PoolPersistenceServiceTest} 等用的是 Mockito 的
 * {@code StoreManager} 桩，桩上没有那段早退，断言「saveConfig 被调用过」恒真 ⇒ 绿灯掩盖了缺陷。
 * 因此本类**直接驱动真实的 JpaStoreManager**（只把 JPA 仓库换成有状态的假实现），
 * 断言的是「库里最后留下了什么」，而不是「某个方法被调用过」。
 *
 * <p><b>检测力自证</b>：把 {@code saveConfig} 里补回 {@code config.isEmpty()} 早退，
 * EMPTY-001 / EMPTY-002 必须变红（EMP-001 给出 {@code save never called}）。
 */
@DisplayName("JpaStoreManager 存储契约守护")
class JpaStoreManagerTest {

    private static final String KEY = "pool_definitions";

    private ConfigRepository repository;
    private JpaStoreManager storeManager;

    /** 单键、单版本的有状态替身：只保留「最新一行」，语义与真实仓库一致。 */
    private final AtomicReference<ConfigEntity> latestRow = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        repository = mock(ConfigRepository.class);
        latestRow.set(null);

        when(repository.findFirstByConfigKeyAndIsLatestTrue(anyString()))
                .thenAnswer(inv -> Optional.ofNullable(latestRow.get()));
        when(repository.save(any(ConfigEntity.class))).thenAnswer(inv -> {
            ConfigEntity entity = inv.getArgument(0);
            latestRow.set(entity);
            return entity;
        });

        storeManager = new JpaStoreManager(repository);
    }

    private Map<String, Object> onePool() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("auto-model", Map.of("poolName", "auto-model", "serviceType", "chat"));
        return m;
    }

    @Nested
    @DisplayName("空表的语义（缺陷回归守卫）")
    class EmptyMapTests {

        @Test
        @DisplayName("EMPTY-001: 空表必须落库，不得静默早退")
        void emptyMapIsPersisted() {
            storeManager.saveConfig(KEY, new HashMap<>());

            ArgumentCaptor<ConfigEntity> captor = ArgumentCaptor.forClass(ConfigEntity.class);
            verify(repository, description("空表也必须写库：早退会让「删除最后一个条目」静默丢失"))
                    .save(captor.capture());
            assertEquals("{}", captor.getValue().getConfigValue(),
                    "空表应序列化为 {} 落库（与 File/Memory 实现同口径）");
        }

        @Test
        @DisplayName("EMPTY-002: 空表覆盖旧值 —— 删空后读回必须是空，而不是旧值")
        void emptyMapOverwritesPreviousValue() {
            storeManager.saveConfig(KEY, onePool());
            assertFalse(storeManager.getConfig(KEY).isEmpty(), "前置：先有一行非空数据");

            // 上层「删掉最后一个条目」就是把集合清空后再 saveAll
            storeManager.saveConfig(KEY, new HashMap<>());

            Map<String, Object> after = storeManager.getConfig(KEY);
            assertNotNull(after, "删空后仍应能读到一行空表（而非 null 或旧值）");
            assertTrue(after.isEmpty(),
                    "删空后读回必须是空表；若仍是旧值，说明空表被静默丢弃 ⇒ 条目会复活");
        }

        @Test
        @DisplayName("EMPTY-003: null 配置不写库（与空表区分开）")
        void nullConfigIsNotPersisted() {
            storeManager.saveConfig(KEY, null);

            verify(repository, never()).save(any(ConfigEntity.class));
        }
    }

    @Nested
    @DisplayName("非空表的既有行为不变")
    class NonEmptyTests {

        @Test
        @DisplayName("SAVE-001: 首次写入创建 version=1 且 isLatest=true")
        void firstWriteCreatesVersionOne() {
            storeManager.saveConfig(KEY, onePool());

            ConfigEntity row = latestRow.get();
            assertNotNull(row);
            assertEquals(KEY, row.getConfigKey());
            assertEquals(1, row.getVersion());
            assertTrue(row.getIsLatest());
        }

        @Test
        @DisplayName("SAVE-002: 再次写入更新同一行，不新增版本")
        void secondWriteUpdatesInPlace() {
            storeManager.saveConfig(KEY, onePool());
            Map<String, Object> other = new LinkedHashMap<>();
            other.put("embed-pool", Map.of("poolName", "embed-pool", "serviceType", "embedding"));
            storeManager.saveConfig(KEY, other);

            assertEquals(1, latestRow.get().getVersion(), "应原地更新最新行，而不是创建新版本");
            verify(repository, times(2)).save(any(ConfigEntity.class));
        }

        @Test
        @DisplayName("SAVE-003: 非空表读回内容与写入一致（序列化往返）")
        void roundTrip() {
            storeManager.saveConfig(KEY, onePool());

            Map<String, Object> back = storeManager.getConfig(KEY);
            assertEquals(1, back.size());
            assertTrue(back.containsKey("auto-model"));
        }
    }
}
