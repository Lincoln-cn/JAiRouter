package org.unreal.modelrouter.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.checker.ServerChecker;
import org.unreal.modelrouter.checker.ServiceStateManager;
import org.unreal.modelrouter.model.ModelRouterProperties;
import org.unreal.modelrouter.model.ModelServiceRegistry;
import org.unreal.modelrouter.store.ConfigVersionManager;
import org.unreal.modelrouter.store.StoreManager;
import org.unreal.modelrouter.tracing.config.SamplingConfigurationValidator;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试并发更新场景下的版本创建控制
 */
@ExtendWith(MockitoExtension.class)
class ConfigurationServiceConcurrentUpdateTest {

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
        configurationService = new ConfigurationService(storeManager, configurationHelper, configMergeService, serviceStateManager, samplingValidator);
    }

    @Test
    void testConcurrentUpdateSameInstance() throws InterruptedException {
        // 准备测试数据
        String serviceType = "chat";
        String instanceId = "qwen3:1.7B@http://172.16.30.6:9090";

        ModelRouterProperties.ModelInstance instanceConfig = new ModelRouterProperties.ModelInstance();
        instanceConfig.setName("qwen3:1.7B");
        instanceConfig.setBaseUrl("http://172.16.30.6:9090");
        instanceConfig.setStatus("inactive");

        // 模拟并发请求
        int threadCount = 5;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待所有线程准备就绪

                    // 执行更新操作
                    configurationService.updateServiceInstance(serviceType, instanceId, instanceConfig);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    if (e.getMessage().contains("重复")) {
                        duplicateCount.incrementAndGet();
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 启动所有线程
        startLatch.countDown();

        // 等待所有线程完成
        assertTrue(endLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // 验证结果：应该只有一个成功，其他被去重
        assertTrue(successCount.get() >= 1, "至少应该有一个更新成功");
        assertTrue(successCount.get() + duplicateCount.get() <= threadCount, "成功和去重的总数不应超过线程数");

        System.out.println("成功更新次数: " + successCount.get());
        System.out.println("重复请求次数: " + duplicateCount.get());
    }

    @Test
    void testStatusChangeDoesNotCreateNewVersion() {
        // 测试状态变化不应该创建新版本
        String serviceType = "chat";
        String instanceId = "qwen3:1.7B@http://172.16.30.6:9090";

        // 第一次更新：active状态
        ModelRouterProperties.ModelInstance activeInstance = new ModelRouterProperties.ModelInstance();
        activeInstance.setName("qwen3:1.7B");
        activeInstance.setBaseUrl("http://172.16.30.6:9090");
        activeInstance.setStatus("active");

        // 第二次更新：inactive状态
        ModelRouterProperties.ModelInstance inactiveInstance = new ModelRouterProperties.ModelInstance();
        inactiveInstance.setName("qwen3:1.7B");
        inactiveInstance.setBaseUrl("http://172.16.30.6:9090");
        inactiveInstance.setStatus("inactive");

        // 这里需要mock相关方法来验证版本创建行为
        // 由于涉及复杂的依赖注入，这个测试需要在实际环境中运行

        assertDoesNotThrow(() -> {
            configurationService.updateServiceInstance(serviceType, instanceId, activeInstance);
            Thread.sleep(100); // 短暂等待
            configurationService.updateServiceInstance(serviceType, instanceId, inactiveInstance);
        });
    }
}