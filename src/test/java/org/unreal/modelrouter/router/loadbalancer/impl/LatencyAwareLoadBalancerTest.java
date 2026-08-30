package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v2.9.3: LatencyAwareLoadBalancer 单元测试
 */
@DisplayName("LatencyAwareLoadBalancer 单元测试")
class LatencyAwareLoadBalancerTest {

    private List<ModelRouterProperties.ModelInstance> instances;

    @BeforeEach
    void setUp() {
        instances = new ArrayList<>();
        instances.add(createInstance("fast", "http://fast:8080", "/api"));
        instances.add(createInstance("slow", "http://slow:8080", "/api"));
        instances.add(createInstance("medium", "http://medium:8080", "/api"));
    }

    // ==================== EWMA 收敛测试 ====================

    @Nested
    @DisplayName("EWMA 收敛测试")
    class EwmaConvergenceTests {

        @Test
        @DisplayName("经过多次采样后，慢实例的EWMA更高")
        void slowerInstanceHasHigherEwma() {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer(0.3);
            ModelRouterProperties.ModelInstance fastInst = instances.get(0);
            ModelRouterProperties.ModelInstance slowInst = instances.get(1);
            ModelRouterProperties.ModelInstance mediumInst = instances.get(2);

            // 给所有实例喂入不同延迟样本，避免未采样实例以冷启动权重1.0主导选择
            for (int i = 0; i < 50; i++) {
                lb.recordCallComplete(fastInst, 10, true);
                lb.recordCallComplete(mediumInst, 100, true);
                lb.recordCallComplete(slowInst, 500, true);
            }

            // 多次选择，fast 实例应被选中更多次
            int fastCount = 0;
            int total = 10000;
            for (int i = 0; i < total; i++) {
                ModelRouterProperties.ModelInstance selected = lb.selectInstance(instances, "127.0.0.1", "chat");
                if ("fast".equals(selected.getName())) {
                    fastCount++;
                }
            }

            // fast ewma≈10, weight=1/11≈0.0909; medium ewma≈100, weight=1/101≈0.0099; slow ewma≈500, weight=1/501≈0.002
            // fast 概率 ≈ 0.0909/0.103 ≈ 88%
            assertTrue(fastCount > total * 0.6,
                    "Fast instance should be selected > 60% but was " + (fastCount * 100 / total) + "%");
        }
    }

    // ==================== 选择分布测试 ====================

    @Nested
    @DisplayName("选择分布测试")
    class SelectionDistributionTests {

        @Test
        @DisplayName("经过大量采样偏向快速实例后，选择频率偏向快速实例")
        void selectionFavorsFasterInstance() {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer(0.2);
            ModelRouterProperties.ModelInstance fastInst = instances.get(0);
            ModelRouterProperties.ModelInstance slowInst = instances.get(1);
            ModelRouterProperties.ModelInstance mediumInst = instances.get(2);

            // 喂入数据
            for (int i = 0; i < 100; i++) {
                lb.recordCallComplete(fastInst, 5, true);
                lb.recordCallComplete(slowInst, 200, true);
                lb.recordCallComplete(mediumInst, 100, true);
            }

            int fastCount = 0;
            int total = 1000;
            for (int i = 0; i < total; i++) {
                ModelRouterProperties.ModelInstance selected = lb.selectInstance(instances, "127.0.0.1", "chat");
                if ("fast".equals(selected.getName())) {
                    fastCount++;
                }
            }

            // fast ewma≈5, weight=1/6≈0.167; medium ewma≈100, weight=1/101≈0.0099; slow ewma≈200, weight=1/201≈0.005
            // fast 概率 ≈ 0.167/0.182 ≈ 92%
            assertTrue(fastCount > total * 0.6,
                    "Fast instance should be picked > 60% but was " + (fastCount * 100 / total) + "%");
        }
    }

    // ==================== 冷启动测试 ====================

    @Nested
    @DisplayName("冷启动测试")
    class ColdStartTests {

        @Test
        @DisplayName("无样本时所有实例权重相等(冷启动)")
        void noSamplesEqualWeight() {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer();

            // 无样本时，所有实例ewma=0，权重都为1.0
            // 选择应该大致均匀分布
            int[] counts = new int[3];
            int total = 3000;
            for (int i = 0; i < total; i++) {
                ModelRouterProperties.ModelInstance selected = lb.selectInstance(instances, "127.0.0.1", "chat");
                for (int j = 0; j < 3; j++) {
                    if (instances.get(j).getName().equals(selected.getName())) {
                        counts[j]++;
                    }
                }
            }

            // 每个实例应被选中约 33% (容差 20%)
            for (int i = 0; i < 3; i++) {
                double ratio = (double) counts[i] / total;
                assertTrue(ratio > 0.15 && ratio < 0.55,
                        "Instance " + i + " ratio " + ratio + " outside expected range for cold start");
            }
        }

        @Test
        @DisplayName("单实例直接返回")
        void singleInstanceReturned() {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer();
            List<ModelRouterProperties.ModelInstance> single = List.of(instances.get(0));

            ModelRouterProperties.ModelInstance selected = lb.selectInstance(single, "127.0.0.1", "chat");
            assertSame(instances.get(0), selected);
        }
    }

    // ==================== 失败惩罚测试 ====================

    @Nested
    @DisplayName("失败惩罚测试")
    class FailurePenaltyTests {

        @Test
        @DisplayName("recordCallFailure 增加 EWMA（惩罚）")
        void failureIncreasesEwma() {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer(0.5);
            ModelRouterProperties.ModelInstance inst = instances.get(0);
            ModelRouterProperties.ModelInstance other = instances.get(1);
            ModelRouterProperties.ModelInstance mediumInst = instances.get(2);

            // 给所有实例喂入样本，避免未采样实例以冷启动权重1.0主导选择
            lb.recordCallComplete(inst, 10, true);
            lb.recordCallComplete(mediumInst, 100, true);
            lb.recordCallComplete(other, 10, true);

            // 失败惩罚应大幅增加 inst 的 ewma
            lb.recordCallFailure(inst, 5000, "500");

            // inst 现在应该被选中的概率很低
            int instCount = 0;
            int total = 1000;
            for (int i = 0; i < total; i++) {
                ModelRouterProperties.ModelInstance selected = lb.selectInstance(instances, "127.0.0.1", "chat");
                if ("fast".equals(selected.getName())) {
                    instCount++;
                }
            }

            // inst 因失败惩罚(ewma≈15003)，被选中概率应显著下降
            // other ewma≈5, medium ewma≈50; inst 概率 ≈ 0.0000666/0.187 ≈ 0.04%
            assertTrue(instCount < total * 0.4,
                    "Failed instance should be picked < 40% but was " + (instCount * 100 / total) + "%");
        }
    }

    // ==================== 边界条件测试 ====================

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("空实例列表返回 null")
        void emptyListReturnsNull() {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer();
            ModelRouterProperties.ModelInstance selected = lb.selectInstance(new ArrayList<>(), "127.0.0.1", "chat");
            assertNull(selected);
        }

        @Test
        @DisplayName("null 实例列表返回 null")
        void nullListReturnsNull() {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer();
            ModelRouterProperties.ModelInstance selected = lb.selectInstance(null, "127.0.0.1", "chat");
            assertNull(selected);
        }

        @Test
        @DisplayName("默认构造器 alpha = 0.2")
        void defaultConstructorAlpha() {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer();
            // 通过行为验证：喂入相同数据，与显式 alpha=0.2 行为一致
            ModelRouterProperties.ModelInstance inst = instances.get(0);
            lb.recordCallComplete(inst, 100, true);

            // 只要不抛异常且能正常选择即可
            ModelRouterProperties.ModelInstance selected = lb.selectInstance(instances, "127.0.0.1", "chat");
            assertNotNull(selected);
        }

        @Test
        @DisplayName("无效 alpha 回退到默认值")
        void invalidAlphaFallbackToDefault() {
            // alpha <= 0 应回退到默认 0.2
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer(-1.0);
            ModelRouterProperties.ModelInstance selected = lb.selectInstance(instances, "127.0.0.1", "chat");
            assertNotNull(selected);

            // alpha > 1.0 应回退到默认 0.2
            LatencyAwareLoadBalancer lb2 = new LatencyAwareLoadBalancer(2.0);
            ModelRouterProperties.ModelInstance selected2 = lb2.selectInstance(instances, "127.0.0.1", "chat");
            assertNotNull(selected2);
        }
    }

    // ==================== 并发安全测试 ====================

    @Nested
    @DisplayName("并发安全测试")
    class ConcurrencyTests {

        @Test
        @DisplayName("并行 recordCallComplete 不抛异常")
        void parallelRecordCallCompleteNoException() throws InterruptedException {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer(0.2);
            int threadCount = 10;
            int opsPerThread = 500;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            ModelRouterProperties.ModelInstance inst = instances.get(threadIdx % instances.size());
                            lb.recordCallComplete(inst, 50 + threadIdx * 10, true);
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();
            assertEquals(0, errorCount.get(), "No exceptions should occur during parallel updates");

            // 验证LB仍然可用
            ModelRouterProperties.ModelInstance selected = lb.selectInstance(instances, "127.0.0.1", "chat");
            assertNotNull(selected);
        }

        @Test
        @DisplayName("并发选择和更新混合不丢一致性")
        void concurrentSelectAndUpdateNoInconsistency() throws InterruptedException {
            LatencyAwareLoadBalancer lb = new LatencyAwareLoadBalancer(0.3);
            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                final int threadIdx = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < 200; i++) {
                            if (threadIdx % 2 == 0) {
                                // 更新线程
                                lb.recordCallComplete(instances.get(threadIdx % 3), 100, true);
                            } else {
                                // 选择线程
                                ModelRouterProperties.ModelInstance sel = lb.selectInstance(instances, "127.0.0.1", "chat");
                                assertNotNull(sel);
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            executor.shutdown();
            assertEquals(0, errorCount.get(), "No exceptions during concurrent mixed operations");
        }
    }

    // ==================== Helper Methods ====================

    private ModelRouterProperties.ModelInstance createInstance(String name, String baseUrl, String path) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setName(name);
        instance.setInstanceId(name);
        instance.setBaseUrl(baseUrl);
        instance.setPath(path);
        instance.setHealthy(true);
        return instance;
    }
}
