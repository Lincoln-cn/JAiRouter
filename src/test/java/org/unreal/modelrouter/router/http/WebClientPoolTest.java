package org.unreal.modelrouter.router.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebClientPool 单元测试 - v2.7.0
 * 
 * 测试目标：
 * - 验证缓存正确性
 * - 验证相同 URL 返回同一实例
 * - 验证缓存统计
 */
@DisplayName("WebClientPool v2.7.0 测试")
class WebClientPoolTest {

    private WebClientPool webClientPool;

    @BeforeEach
    void setUp() {
        webClientPool = new WebClientPool();
    }

    @Test
    @DisplayName("测试 1: 相同 baseUrl 应返回同一 WebClient 实例")
    void getOrCreate_sameBaseUrl_shouldReturnSameInstance() {
        String baseUrl = "http://localhost:8080";

        WebClient client1 = webClientPool.getOrCreate(baseUrl);
        WebClient client2 = webClientPool.getOrCreate(baseUrl);

        assertSame(client1, client2, "相同 baseUrl 应返回同一 WebClient 实例");
    }

    @Test
    @DisplayName("测试 2: 不同 baseUrl 应返回不同 WebClient 实例")
    void getOrCreate_differentBaseUrl_shouldReturnDifferentInstance() {
        WebClient client1 = webClientPool.getOrCreate("http://localhost:8080");
        WebClient client2 = webClientPool.getOrCreate("http://localhost:9090");

        assertNotSame(client1, client2, "不同 baseUrl 应返回不同 WebClient 实例");
    }

    @Test
    @DisplayName("测试 3: evict 应移除指定缓存")
    void evict_shouldRemoveCachedClient() {
        String baseUrl = "http://localhost:8080";
        webClientPool.getOrCreate(baseUrl);

        webClientPool.evict(baseUrl);

        WebClientPool.PoolStats stats = webClientPool.getStats();
        assertEquals(0, stats.size(), "缓存应被清空");
    }

    @Test
    @DisplayName("测试 4: evictAll 应清理所有缓存")
    void evictAll_shouldClearAllCache() {
        webClientPool.getOrCreate("http://localhost:8080");
        webClientPool.getOrCreate("http://localhost:9090");

        webClientPool.evictAll();

        WebClientPool.PoolStats stats = webClientPool.getStats();
        assertEquals(0, stats.size(), "所有缓存应被清空");
    }

    @Test
    @DisplayName("测试 5: 缓存统计应正确记录命中和未命中")
    void getStats_shouldRecordHitAndMiss() {
        String baseUrl = "http://localhost:8080";

        // 第一次访问 (miss)
        webClientPool.getOrCreate(baseUrl);
        // 第二次访问 (hit)
        webClientPool.getOrCreate(baseUrl);

        WebClientPool.PoolStats stats = webClientPool.getStats();
        assertEquals(1, stats.size(), "缓存大小应为 1");
        assertEquals(1, stats.missCount(), "未命中次数应为 1");
        assertEquals(1, stats.hitCount(), "命中次数应为 1");
    }

    @Test
    @DisplayName("测试 6: 命中率计算应正确")
    void poolStats_hitRate_shouldBeCorrect() {
        String baseUrl = "http://localhost:8080";

        webClientPool.getOrCreate(baseUrl);  // miss
        webClientPool.getOrCreate(baseUrl);  // hit
        webClientPool.getOrCreate(baseUrl);  // hit

        WebClientPool.PoolStats stats = webClientPool.getStats();
        assertEquals(2.0 / 3.0, stats.hitRate(), 0.01, "命中率应为 2/3");
    }

    @Test
    @DisplayName("测试 7: 空 baseUrl 应正常处理")
    void getOrCreate_emptyBaseUrl_shouldWork() {
        WebClient client = webClientPool.getOrCreate("");
        assertNotNull(client, "空 baseUrl 也应返回 WebClient 实例");
    }

    @Test
    @DisplayName("测试 8: 多次获取相同 URL 不应增加缓存大小")
    void getOrCreate_multipleAccess_shouldNotIncreaseSize() {
        String baseUrl = "http://localhost:8080";

        for (int i = 0; i < 10; i++) {
            webClientPool.getOrCreate(baseUrl);
        }

        WebClientPool.PoolStats stats = webClientPool.getStats();
        assertEquals(1, stats.size(), "缓存大小应仍为 1");
    }

    @Test
    @DisplayName("测试 9: 并发访问应线程安全")
    void getOrCreate_concurrentAccess_shouldBeThreadSafe() throws Exception {
        String baseUrl = "http://localhost:8080";
        int threadCount = 50;
        WebClient[] clients = new WebClient[threadCount];
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            threads[i] = new Thread(() -> {
                clients[index] = webClientPool.getOrCreate(baseUrl);
            });
        }

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // 所有线程应获取到同一个实例
        WebClient first = clients[0];
        for (int i = 1; i < threadCount; i++) {
            assertSame(first, clients[i], "所有线程应获取同一 WebClient 实例");
        }
    }

    @Test
    @DisplayName("测试 10: 多个不同 URL 应分别缓存")
    void getOrCreate_multipleUrls_shouldCacheSeparately() {
        webClientPool.getOrCreate("http://localhost:8080");
        webClientPool.getOrCreate("http://localhost:9090");
        webClientPool.getOrCreate("http://192.168.1.1:8080");

        WebClientPool.PoolStats stats = webClientPool.getStats();
        assertEquals(3, stats.size(), "应缓存 3 个 WebClient 实例");
    }
}
