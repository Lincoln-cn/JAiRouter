package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;

/**
 * 测试配置服务的并发安全性
 */
@ExtendWith(MockitoExtension.class)
public class ConfigurationServiceConcurrencyTest {

    private static final Logger logger = LoggerFactory.getLogger(ConfigurationServiceConcurrencyTest.class);

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

    private ConfigurationService configurationService;

    @BeforeEach
    void setUp() {
        // 使用lenient模式避免不必要的stubbing警告
        lenient().when(storeManager.exists(anyString())).thenReturn(false);
        lenient().when(storeManager.versionExists(anyString(), anyInt())).thenReturn(false);
        lenient().when(configMergeService.getPersistedConfig()).thenReturn(createTestConfig());
        lenient().doNothing().when(storeManager).saveConfigVersion(anyString(), any(), anyInt());
        lenient().doNothing().when(storeManager).saveConfig(anyString(), any());

        configurationService = new ConfigurationService(
                storeManager,
                configurationHelper,
                configMergeService,
                serviceStateManager,
                samplingValidator
        );
    }

    @Test
    void testConcurrentVersionCreation() throws InterruptedException {
        logger.info("测试并发版本创建的安全性");

        int threadCount = 10;
        int operationsPerThread = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // 用于收集所有创建的版本号
        Set<Integer> createdVersions = ConcurrentHashMap.newKeySet();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        // 启动多个线程同时创建版本
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            Map<String, Object> config = createTestConfig();
                            // 稍微修改配置以确保被认为是变化
                            config.put("threadId", threadId);
                            config.put("operationId", j);
                            config.put("timestamp", System.currentTimeMillis());

                            int version = configurationService.saveAsNewVersion(
                                    config,
                                    "并发测试 - 线程" + threadId + "操作" + j,
                                    "test-user-" + threadId
                            );

                            createdVersions.add(version);
                            successCount.incrementAndGet();
                            logger.debug("线程 {} 操作 {} 创建版本: {}", threadId, j, version);

                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            logger.error("线程 {} 操作失败: {}", threadId, e.getMessage());
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        // 等待所有线程完成
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "所有线程应该在30秒内完成");

        logger.info("并发测试结果 - 成功: {}, 失败: {}, 创建的版本数: {}",
                successCount.get(), errorCount.get(), createdVersions.size());

        // 验证结果
        assertTrue(successCount.get() > 0, "应该有成功的版本创建");
        assertEquals(0, errorCount.get(), "不应该有错误发生");

        // 验证版本号的唯一性
        assertEquals(successCount.get(), createdVersions.size(),
                "创建的版本号应该都是唯一的，不应该有重复");

        // 验证版本号都是正数且合理
        List<Integer> sortedVersions = new ArrayList<>(createdVersions);
        sortedVersions.sort(Integer::compareTo);

        for (Integer version : sortedVersions) {
            assertTrue(version > 0, "版本号应该是正数: " + version);
            assertTrue(version < Integer.MAX_VALUE, "版本号应该在合理范围内: " + version);
        }

        logger.info("版本号范围: {} - {}", sortedVersions.get(0), sortedVersions.get(sortedVersions.size() - 1));

        logger.info("并发版本创建测试通过");
    }

    @Test
    void testConcurrentVersionCreationWithChangedCheck() throws InterruptedException {
        logger.info("测试带配置变化检查的并发版本创建");

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        Set<Integer> createdVersions = ConcurrentHashMap.newKeySet();
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    Map<String, Object> config = createTestConfig();
                    config.put("threadId", threadId);
                    config.put("timestamp", System.currentTimeMillis());

                    int version = configurationService.saveAsNewVersionIfChanged(
                            config,
                            "并发变化检查测试 - 线程" + threadId,
                            "test-user-" + threadId
                    );

                    createdVersions.add(version);
                    successCount.incrementAndGet();
                    logger.debug("线程 {} 创建版本: {}", threadId, version);

                } catch (Exception e) {
                    logger.error("线程 {} 操作失败: {}", threadId, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(20, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "所有线程应该在20秒内完成");

        logger.info("并发变化检查测试结果 - 成功: {}, 创建的版本数: {}",
                successCount.get(), createdVersions.size());

        // 验证版本号唯一性
        assertEquals(successCount.get(), createdVersions.size(),
                "创建的版本号应该都是唯一的");

        logger.info("并发配置变化检查测试通过");
    }

    private Map<String, Object> createTestConfig() {
        Map<String, Object> config = new HashMap<>();

        Map<String, Object> services = new HashMap<>();
        Map<String, Object> chatService = new HashMap<>();

        List<Map<String, Object>> instances = new ArrayList<>();
        Map<String, Object> instance = new HashMap<>();
        instance.put("name", "test-instance");
        instance.put("baseUrl", "http://test.example.com");
        instance.put("status", "active");
        instances.add(instance);

        chatService.put("instances", instances);
        services.put("chat", chatService);
        config.put("services", services);

        return config;
    }
}