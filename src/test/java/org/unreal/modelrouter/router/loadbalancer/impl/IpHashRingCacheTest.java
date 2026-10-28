package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #692 ip-hash 环缓存与摘要复用测试。
 *
 * <p>缺陷：每次 {@code selectInstance} 都新建 {@code TreeMap} 环，且
 * {@code MessageDigest.getInstance(...)} 位于「每个虚拟节点」的循环里 —— 每请求
 * {@code Σ(weight × 100) × 实例数} 次摘要实例化。</p>
 *
 * <p>修复：环按候选集指纹缓存（构建后只读），摘要用 {@code ThreadLocal} 复用。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#692 ip-hash 环缓存与摘要复用")
class IpHashRingCacheTest {

    @Test
    @DisplayName("同一候选集只建一次环，且不同 IP 都能选到实例")
    void ringIsBuiltOncePerCandidateSet() {
        IpHashLoadBalancer loadBalancer = new IpHashLoadBalancer("md5");
        List<ModelRouterProperties.ModelInstance> instances = instances(4, 10);

        Set<String> hit = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            ModelRouterProperties.ModelInstance selected =
                    loadBalancer.selectInstance(instances, "203.0.113." + i, "chat");
            assertNotNull(selected);
            hit.add(selected.getBaseUrl());
        }

        assertTrue(hit.size() > 1, "ip-hash 在 4 个实例上不应只命中一个，实际命中：" + hit);
        assertEquals(1L, loadBalancer.getRingBuildCount(), "同一候选集只允许构建一次环");
        assertEquals(1, loadBalancer.getCachedRingCount());
        assertEquals(4 * 10 * 100, loadBalancer.ringFor(instances).size(), "虚拟节点数 = Σ(weight × 100)");
    }

    @Test
    @DisplayName("同一 IP 的选择结果稳定（摘要复用不得改变分布）")
    void sameIpKeepsHittingTheSameInstance() {
        IpHashLoadBalancer loadBalancer = new IpHashLoadBalancer("md5");
        List<ModelRouterProperties.ModelInstance> instances = instances(5, 3);

        String first = loadBalancer.selectInstance(instances, "198.51.100.23", "chat").getBaseUrl();
        for (int i = 0; i < 50; i++) {
            assertEquals(first, loadBalancer.selectInstance(instances, "198.51.100.23", "chat").getBaseUrl(),
                    "同一 IP 必须稳定命中同一实例");
        }
        assertEquals(1L, loadBalancer.getRingBuildCount(), "重复选择不得重建环");
    }

    @Test
    @DisplayName("权重变化会换环（环内容依赖 weight，不能只按 baseUrl 缓存）")
    void weightChangeInvalidatesTheRing() {
        IpHashLoadBalancer loadBalancer = new IpHashLoadBalancer("md5");
        List<ModelRouterProperties.ModelInstance> instances = instances(2, 1);
        loadBalancer.selectInstance(instances, "203.0.113.5", "chat");
        int sizeBefore = loadBalancer.ringFor(instances).size();

        instances.get(0).setWeight(5);
        int sizeAfter = loadBalancer.ringFor(instances).size();

        assertNotEquals(sizeBefore, sizeAfter, "权重变化后虚拟节点数必须变化");
        assertEquals(2L, loadBalancer.getRingBuildCount(), "权重变化必须重建一次环");
        assertTrue(loadBalancer.getCachedRingCount() >= 2);
    }

    @Test
    @DisplayName("并发选择每轮只构建一次环")
    void concurrentSelectionBuildsOneRing() throws Exception {
        IpHashLoadBalancer loadBalancer = new IpHashLoadBalancer("md5");
        int rounds = 10;
        for (int round = 1; round <= rounds; round++) {
            List<ModelRouterProperties.ModelInstance> instances = instances(4, 2);
            int threads = 8;
            CyclicBarrier barrier = new CyclicBarrier(threads);
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int offset = t;
                futures.add(pool.submit(() -> {
                    barrier.await();
                    for (int i = 0; i < 20; i++) {
                        loadBalancer.selectInstance(instances, "203.0.113." + (offset * 20 + i), "chat");
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "线程池必须在 30s 内结束");
            assertEquals((long) round, loadBalancer.getRingBuildCount(),
                    "第 " + round + " 轮并发选择只允许构建一次环");
        }
    }

    private static List<ModelRouterProperties.ModelInstance> instances(final int count, final int weight) {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
            instance.setInstanceId("ip-" + i);
            instance.setBaseUrl("http://10.0.1." + i + ":8080");
            instance.setName("gpt-4-" + i);
            instance.setHealthy(true);
            instance.setWeight(weight);
            instances.add(instance);
        }
        return instances;
    }
}
