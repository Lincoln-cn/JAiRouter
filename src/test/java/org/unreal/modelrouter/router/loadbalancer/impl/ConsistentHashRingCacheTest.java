package org.unreal.modelrouter.router.loadbalancer.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #687 一致性哈希环缓存测试。
 *
 * <p>此前的实现把哈希环放在本类级别的一个可变 {@code TreeMap} 里，每次请求按入参增删节点：
 * ① 并发请求下 {@code TreeMap} 可能抛 {@code ConcurrentModificationException} 或读到结构性损坏；
 * ② 不同模型/服务类型共用同一个环，互相把对方的节点摘掉（跨模型驱逐）；
 * ③ 每请求重建 O(实例数 × 虚拟节点数) 的节点，纯浪费。</p>
 *
 * <p>修复后：环按「服务类型 + 实例集合指纹」缓存且构建后只读，同一集合只构建一次。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.5
 */
@DisplayName("#687 一致性哈希环缓存")
class ConsistentHashRingCacheTest {

    private static ConsistentHashLoadBalancer balancer() {
        return new ConsistentHashLoadBalancer(20);
    }

    @Test
    @DisplayName("同一实例集合复用同一个环，且只构建一次")
    void ringIsReusedForTheSameInstanceSet() {
        ConsistentHashLoadBalancer loadBalancer = balancer();
        List<ModelRouterProperties.ModelInstance> instances = instances("a", 3);

        ConsistentHashLoadBalancer.HashRing first = loadBalancer.ringFor(instances, "chat");
        ConsistentHashLoadBalancer.HashRing second = loadBalancer.ringFor(instances, "chat");

        assertSame(first, second, "同一实例集合必须复用同一个环对象");
        assertEquals(1L, loadBalancer.getRingBuildCount(), "同一实例集合只允许构建一次");
        assertEquals(3, first.getInstanceCount());
        assertTrue(first.size() > 0, "环上必须有虚拟节点");
    }

    @Test
    @DisplayName("不同实例集合各自成环，构建 B 不会改动 A 的环")
    void differentInstanceSetsDoNotAffectEachOther() {
        ConsistentHashLoadBalancer loadBalancer = balancer();
        List<ModelRouterProperties.ModelInstance> modelA = instances("a", 3);
        List<ModelRouterProperties.ModelInstance> modelB = instances("b", 3);

        ConsistentHashLoadBalancer.HashRing ringA = loadBalancer.ringFor(modelA, "chat");
        int sizeOfA = ringA.size();

        ConsistentHashLoadBalancer.HashRing ringB = loadBalancer.ringFor(modelB, "chat");

        assertNotSame(ringA, ringB, "不同实例集合必须是不同的环");
        assertEquals(sizeOfA, ringA.size(), "B 的环构建不得改动 A 的环（跨模型驱逐回归）");
        assertEquals(3, ringA.getInstanceCount());
        assertEquals(2L, loadBalancer.getRingBuildCount());
        assertEquals(2, loadBalancer.getCachedRingCount());
    }

    @Test
    @DisplayName("构建次数不随请求数增长；同一 IP 的选择结果稳定")
    void buildCountDoesNotGrowWithRequestCount() {
        ConsistentHashLoadBalancer loadBalancer = balancer();
        List<ModelRouterProperties.ModelInstance> instances = instances("a", 4);

        for (int i = 0; i < 50; i++) {
            assertNotNull(loadBalancer.selectInstance(instances, "10.0.0." + i, "chat"));
        }

        assertEquals(1L, loadBalancer.getRingBuildCount(), "50 次请求只允许构建 1 个环");

        ModelRouterProperties.ModelInstance first = loadBalancer.selectInstance(instances, "10.0.0.7", "chat");
        ModelRouterProperties.ModelInstance second = loadBalancer.selectInstance(instances, "10.0.0.7", "chat");
        assertEquals(first.getInstanceId(), second.getInstanceId(), "同一 IP 必须映射到同一实例");
    }

    @Test
    @DisplayName("并发选择共享同一个环，不抛异常且只构建一次")
    void concurrentSelectionSharesASingleRing() throws Exception {
        ConsistentHashLoadBalancer loadBalancer = balancer();
        List<ModelRouterProperties.ModelInstance> instances = instances("a", 5);

        int threads = 8;
        int perThread = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < threads; t++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    int selected = 0;
                    for (int i = 0; i < perThread; i++) {
                        if (loadBalancer.selectInstance(instances, "10.0.1." + i, "chat") != null) {
                            selected++;
                        }
                    }
                    return selected;
                }));
            }
            start.countDown();

            int total = 0;
            for (Future<Integer> future : futures) {
                total += future.get(30, TimeUnit.SECONDS);
            }
            assertEquals(threads * perThread, total, "每次选择都必须返回实例");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1L, loadBalancer.getRingBuildCount(), "并发选择只允许构建一次环");
        assertTrue(loadBalancer.getCachedRingCount() <= ConsistentHashLoadBalancer.getMaxCachedRings());
    }

    @Test
    @DisplayName("环缓存有界：大量不同实例集合不会无界增长")
    void ringCacheIsBounded() {
        ConsistentHashLoadBalancer loadBalancer = balancer();
        int limit = ConsistentHashLoadBalancer.getMaxCachedRings();

        for (int i = 0; i < limit + 40; i++) {
            assertNotNull(loadBalancer.selectInstance(instances("m" + i, 2), "10.0.2.1", "chat"));
        }

        assertTrue(loadBalancer.getCachedRingCount() <= limit,
                "环缓存条目数不得超过上限，实际=" + loadBalancer.getCachedRingCount());
    }

    private static List<ModelRouterProperties.ModelInstance> instances(final String prefix, final int count) {
        List<ModelRouterProperties.ModelInstance> instances = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            instances.add(instance(prefix + "-" + i));
        }
        return instances;
    }

    private static ModelRouterProperties.ModelInstance instance(final String id) {
        ModelRouterProperties.ModelInstance instance = new ModelRouterProperties.ModelInstance();
        instance.setId(id);
        instance.setInstanceId(id);
        instance.setName(id);
        instance.setBaseUrl("http://" + id + ":8080");
        instance.setHealthy(true);
        instance.setWeight(1);
        return instance;
    }
}
