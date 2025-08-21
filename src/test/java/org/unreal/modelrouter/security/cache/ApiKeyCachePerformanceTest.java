package org.unreal.modelrouter.security.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.unreal.modelrouter.security.cache.impl.InMemoryApiKeyCache;
import org.unreal.modelrouter.security.cache.impl.RedisApiKeyCache;
import org.unreal.modelrouter.security.model.ApiKeyInfo;
import org.unreal.modelrouter.security.model.UsageStatistics;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API Key缓存性能测试
 * 测试缓存机制的性能表现和并发访问能力
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class ApiKeyCachePerformanceTest {
    
    private InMemoryApiKeyCache inMemoryCache;
    private List<ApiKeyInfo> testApiKeys;
    
    @BeforeEach
    void setUp() {
        inMemoryCache = new InMemoryApiKeyCache(null);
        testApiKeys = generateTestApiKeys(1000);
    }
    
    /**
     * 测试内存缓存的写入性能
     */
    @Test
    void testInMemoryCacheWritePerformance() {
        long startTime = System.currentTimeMillis();
        
        Flux.fromIterable(testApiKeys)
                .flatMap(apiKey -> inMemoryCache.put(apiKey.getKeyValue(), apiKey))
                .blockLast();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.printf("内存缓存写入 %d 个API Key耗时: %d ms%n", testApiKeys.size(), duration);
        assertTrue(duration < 5000, "写入性能应该在5秒内完成");
    }
    
    /**
     * 测试内存缓存的读取性能
     */
    @Test
    void testInMemoryCacheReadPerformance() {
        // 先写入测试数据
        Flux.fromIterable(testApiKeys)
                .flatMap(apiKey -> inMemoryCache.put(apiKey.getKeyValue(), apiKey))
                .blockLast();
        
        long startTime = System.currentTimeMillis();
        
        // 读取所有API Key
        Flux.fromIterable(testApiKeys)
                .flatMap(apiKey -> inMemoryCache.get(apiKey.getKeyValue()))
                .collectList()
                .block();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.printf("内存缓存读取 %d 个API Key耗时: %d ms%n", testApiKeys.size(), duration);
        assertTrue(duration < 2000, "读取性能应该在2秒内完成");
    }
    
    /**
     * 测试缓存的并发访问性能
     */
    @Test
    void testConcurrentAccess() throws InterruptedException {
        // 先写入测试数据
        Flux.fromIterable(testApiKeys.subList(0, 100))
                .flatMap(apiKey -> inMemoryCache.put(apiKey.getKeyValue(), apiKey))
                .blockLast();
        
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        ApiKeyInfo testKey = testApiKeys.get(j % 100);
                        
                        // 随机执行读取或写入操作
                        if (j % 2 == 0) {
                            inMemoryCache.get(testKey.getKeyValue()).block();
                        } else {
                            inMemoryCache.put(testKey.getKeyValue(), testKey).block();
                        }
                        
                        totalOperations.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(30, TimeUnit.SECONDS), "并发测试应该在30秒内完成");
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        long totalOps = totalOperations.get();
        double opsPerSecond = (double) totalOps / (duration / 1000.0);
        
        System.out.printf("并发测试: %d 个线程执行 %d 次操作，耗时: %d ms，QPS: %.2f%n", 
                threadCount, totalOps, duration, opsPerSecond);
        
        assertTrue(opsPerSecond > 1000, "QPS应该超过1000");
        
        executor.shutdown();
    }
    
    /**
     * 测试缓存失效和更新策略的性能
     */
    @Test
    void testCacheEvictionPerformance() {
        // 写入测试数据
        Flux.fromIterable(testApiKeys.subList(0, 100))
                .flatMap(apiKey -> inMemoryCache.put(apiKey.getKeyValue(), apiKey, Duration.ofSeconds(1)))
                .blockLast();
        
        // 验证数据存在
        long existingCount = Flux.fromIterable(testApiKeys.subList(0, 100))
                .flatMap(apiKey -> inMemoryCache.exists(apiKey.getKeyValue()))
                .filter(exists -> exists)
                .count()
                .block();
        
        assertEquals(100, existingCount, "应该有100个缓存条目");
        
        // 等待缓存过期
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long startTime = System.currentTimeMillis();
        
        // 尝试读取过期的缓存条目
        long validCount = Flux.fromIterable(testApiKeys.subList(0, 100))
                .flatMap(apiKey -> inMemoryCache.get(apiKey.getKeyValue()))
                .count()
                .block();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.printf("缓存失效检查耗时: %d ms，有效条目: %d%n", duration, validCount);
        
        assertEquals(0, validCount, "所有缓存条目应该已过期");
        assertTrue(duration < 1000, "失效检查应该在1秒内完成");
    }
    
    /**
     * 测试大量数据的缓存性能
     */
    @Test
    void testLargeDatasetPerformance() {
        List<ApiKeyInfo> largeDataset = generateTestApiKeys(10000);
        
        long startTime = System.currentTimeMillis();
        
        // 批量写入
        Flux.fromIterable(largeDataset)
                .flatMap(apiKey -> inMemoryCache.put(apiKey.getKeyValue(), apiKey))
                .blockLast();
        
        long writeEndTime = System.currentTimeMillis();
        long writeDuration = writeEndTime - startTime;
        
        // 随机读取1000个条目
        List<ApiKeyInfo> randomSample = largeDataset.subList(0, 1000);
        
        long readStartTime = System.currentTimeMillis();
        
        Flux.fromIterable(randomSample)
                .flatMap(apiKey -> inMemoryCache.get(apiKey.getKeyValue()))
                .collectList()
                .block();
        
        long readEndTime = System.currentTimeMillis();
        long readDuration = readEndTime - readStartTime;
        
        System.out.printf("大数据集测试 - 写入 %d 条记录耗时: %d ms，读取 %d 条记录耗时: %d ms%n", 
                largeDataset.size(), writeDuration, randomSample.size(), readDuration);
        
        assertTrue(writeDuration < 30000, "大数据集写入应该在30秒内完成");
        assertTrue(readDuration < 5000, "随机读取应该在5秒内完成");
    }
    
    /**
     * 测试内存使用情况
     */
    @Test
    void testMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        
        // 记录初始内存使用
        runtime.gc();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 写入大量数据
        List<ApiKeyInfo> largeDataset = generateTestApiKeys(5000);
        Flux.fromIterable(largeDataset)
                .flatMap(apiKey -> inMemoryCache.put(apiKey.getKeyValue(), apiKey))
                .blockLast();
        
        // 记录缓存后内存使用
        runtime.gc();
        long afterCacheMemory = runtime.totalMemory() - runtime.freeMemory();
        
        long memoryUsed = afterCacheMemory - initialMemory;
        double memoryPerEntry = (double) memoryUsed / largeDataset.size();
        
        System.out.printf("内存使用测试 - 缓存 %d 个条目使用内存: %d bytes，平均每条目: %.2f bytes%n", 
                largeDataset.size(), memoryUsed, memoryPerEntry);
        
        // 清空缓存
        inMemoryCache.clear().block();
        
        runtime.gc();
        long afterClearMemory = runtime.totalMemory() - runtime.freeMemory();
        
        System.out.printf("清空缓存后内存使用: %d bytes%n", afterClearMemory - initialMemory);
        
        assertTrue(memoryPerEntry < 1000, "每个缓存条目的内存使用应该小于1KB");
    }
    
    /**
     * 生成测试用的API Key数据
     */
    private List<ApiKeyInfo> generateTestApiKeys(int count) {
        List<ApiKeyInfo> apiKeys = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            ApiKeyInfo apiKey = ApiKeyInfo.builder()
                    .keyId("test-key-" + i)
                    .keyValue("test-value-" + i + "-" + System.currentTimeMillis())
                    .description("测试API Key " + i)
                    .createdAt(LocalDateTime.now())
                    .expiresAt(LocalDateTime.now().plusDays(30))
                    .enabled(true)
                    .permissions(List.of("read", "write"))
                    .usage(UsageStatistics.builder()
                            .totalRequests(0L)
                            .successfulRequests(0L)
                            .failedRequests(0L)
                            .lastUsedAt(LocalDateTime.now())
                            .build())
                    .build();
            
            apiKeys.add(apiKey);
        }
        
        return apiKeys;
    }
}