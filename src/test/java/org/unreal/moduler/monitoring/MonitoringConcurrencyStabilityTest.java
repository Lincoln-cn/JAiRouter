package org.unreal.moduler.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.unreal.modelrouter.monitoring.collector.DefaultMetricsCollector;
import org.unreal.modelrouter.monitoring.AsyncMetricsProcessor;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 高并发场景下的监控稳定性测试
 * 验证监控系统在高并发负载下的稳定性和数据一致性
 */
@SpringBootTest(classes = org.unreal.modelrouter.ModelRouterApplication.class)
@TestPropertySource(properties = {
    "monitoring.metrics.enabled=true",
    "monitoring.metrics.prefix=concurrency",
    "monitoring.metrics.performance.async-processing=true",
    "monitoring.metrics.performance.batch-size=50",
    "monitoring.metrics.performance.buffer-size=1000"
})
public class MonitoringConcurrencyStabilityTest {

    @Autowired
    private DefaultMetricsCollector metricsCollector;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired(required = false)
    private AsyncMetricsProcessor asyncMetricsProcessor;

    private static final int HIGH_CONCURRENCY_THREADS = 50;
    private static final int OPERATIONS_PER_THREAD = 1000;
    private static final int STRESS_TEST_DURATION_SECONDS = 30;

    @Test
    @Timeout(60)
    void testHighConcurrencyDataIntegrity() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(HIGH_CONCURRENCY_THREADS);
        AtomicLong totalRequestsRecorded = new AtomicLong(0);
        AtomicLong totalBackendCallsRecorded = new AtomicLong(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(HIGH_CONCURRENCY_THREADS);

        // 启动多个并发线程
        for (int t = 0; t < HIGH_CONCURRENCY_THREADS; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await(); // 等待统一开始信号
                    
                    for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                        // 记录请求指标
                        metricsCollector.recordRequest("concurrency_test", "POST", 
                            100 + i % 100, i % 10 == 0 ? "500" : "200");
                        totalRequestsRecorded.incrementAndGet();
                        
                        // 记录后端调用指标
                        metricsCollector.recordBackendCall("concurrent_adapter", 
                            "instance_" + (threadId % 5), 50 + i % 50, i % 20 != 0);
                        totalBackendCallsRecorded.incrementAndGet();
                        
                        // 记录基础设施指标
                        if (i % 10 == 0) {
                            metricsCollector.recordRateLimit("concurrency_test", 
                                "token-bucket", i % 5 != 0);
                            metricsCollector.recordCircuitBreaker("concurrency_test", 
                                i % 30 == 0 ? "OPEN" : "CLOSED", 
                                i % 30 == 0 ? "FAILURE" : "SUCCESS");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // 统一开始
        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        
        // 等待所有线程完成
        assertTrue(completionLatch.await(45, TimeUnit.SECONDS), 
            "所有并发线程应在45秒内完成");
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        // 等待异步处理完成
        Thread.sleep(2000);
        
        // 验证数据完整性
        verifyDataIntegrity(totalRequestsRecorded.get(), totalBackendCallsRecorded.get());
        
        System.out.printf("高并发测试完成 - 线程数: %d, 每线程操作数: %d, 执行时间: %d ms%n", 
            HIGH_CONCURRENCY_THREADS, OPERATIONS_PER_THREAD, executionTime);
        
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), 
            "线程池应正常关闭");
    }

    private void verifyDataIntegrity(long expectedRequests, long expectedBackendCalls) {
        // 验证请求计数器
        Counter requestCounter = meterRegistry.find("concurrency_requests_total")
            .tag("service", "concurrency_test")
            .counter();
        
        if (requestCounter != null) {
            double actualRequests = requestCounter.count();
            double tolerance = expectedRequests * 0.01; // 1%容错率
            
            assertTrue(Math.abs(actualRequests - expectedRequests) <= tolerance,
                String.format("请求计数不匹配 - 期望: %d, 实际: %.0f, 容错范围: %.0f", 
                    expectedRequests, actualRequests, tolerance));
        }

        // 验证后端调用计数器
        Counter backendCounter = meterRegistry.find("concurrency_backend_calls_total")
            .tag("adapter", "concurrent_adapter")
            .counter();
        
        if (backendCounter != null) {
            double actualBackendCalls = backendCounter.count();
            double tolerance = expectedBackendCalls * 0.01; // 1%容错率
            
            assertTrue(Math.abs(actualBackendCalls - expectedBackendCalls) <= tolerance,
                String.format("后端调用计数不匹配 - 期望: %d, 实际: %.0f, 容错范围: %.0f", 
                    expectedBackendCalls, actualBackendCalls, tolerance));
        }
    }

    @Test
    @Timeout(120)
    void testStressTestStability() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(20);
        AtomicInteger activeThreads = new AtomicInteger(0);
        AtomicLong totalOperations = new AtomicLong(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        long testStartTime = System.currentTimeMillis();
        long testEndTime = testStartTime + (STRESS_TEST_DURATION_SECONDS * 1000);

        // 启动压力测试线程
        for (int t = 0; t < 20; t++) {
            final int threadId = t;
            executor.submit(() -> {
                activeThreads.incrementAndGet();
                Random random = new Random();
                
                try {
                    while (System.currentTimeMillis() < testEndTime) {
                        try {
                            // 随机选择操作类型
                            int operationType = random.nextInt(4);
                            switch (operationType) {
                                case 0:
                                    metricsCollector.recordRequest("stress_test", "GET", 
                                        50 + random.nextInt(200), "200");
                                    break;
                                case 1:
                                    metricsCollector.recordBackendCall("stress_adapter", 
                                        "instance_" + random.nextInt(10), 
                                        30 + random.nextInt(100), random.nextBoolean());
                                    break;
                                case 2:
                                    metricsCollector.recordRateLimit("stress_test", 
                                        "token-bucket", random.nextBoolean());
                                    break;
                                case 3:
                                    metricsCollector.recordCircuitBreaker("stress_test", 
                                        random.nextBoolean() ? "OPEN" : "CLOSED", 
                                        random.nextBoolean() ? "SUCCESS" : "FAILURE");
                                    break;
                            }
                            
                            totalOperations.incrementAndGet();
                            
                            // 随机短暂休眠，模拟真实负载
                            if (random.nextInt(100) < 5) {
                                Thread.sleep(random.nextInt(10));
                            }
                            
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            if (errorCount.get() > 100) {
                                break; // 错误过多，退出
                            }
                        }
                    }
                } finally {
                    activeThreads.decrementAndGet();
                }
            });
        }

        // 监控测试进度
        while (activeThreads.get() > 0 && System.currentTimeMillis() < testEndTime + 5000) {
            Thread.sleep(1000);
            System.out.printf("压力测试进行中 - 活跃线程: %d, 总操作数: %d, 错误数: %d%n", 
                activeThreads.get(), totalOperations.get(), errorCount.get());
        }

        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), 
            "压力测试线程池应正常关闭");

        // 等待异步处理完成
        Thread.sleep(3000);

        long actualDuration = System.currentTimeMillis() - testStartTime;
        double operationsPerSecond = totalOperations.get() / (actualDuration / 1000.0);

        System.out.printf("压力测试完成 - 持续时间: %d ms, 总操作数: %d, 错误数: %d, 操作率: %.2f ops/s%n", 
            actualDuration, totalOperations.get(), errorCount.get(), operationsPerSecond);

        // 验证稳定性指标
        assertTrue(errorCount.get() < totalOperations.get() * 0.01, 
            String.format("错误率应小于1%%, 实际: %.2f%%", 
                (errorCount.get() / (double) totalOperations.get()) * 100));
        
        assertTrue(operationsPerSecond > 100, 
            String.format("操作率应大于100 ops/s，实际: %.2f ops/s", operationsPerSecond));
    }

    @Test
    @Timeout(60)
    void testMemoryLeakUnderConcurrency() throws InterruptedException {
        Runtime runtime = Runtime.getRuntime();
        
        // 初始内存状态
        System.gc();
        Thread.sleep(100);
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();

        ExecutorService executor = Executors.newFixedThreadPool(30);
        List<Future<?>> futures = new ArrayList<>();

        // 启动内存压力测试
        for (int t = 0; t < 30; t++) {
            futures.add(executor.submit(() -> {
                for (int i = 0; i < 2000; i++) {
                    metricsCollector.recordRequest("memory_leak_test", "POST", 
                        100 + i % 100, "200");
                    metricsCollector.recordBackendCall("memory_adapter", 
                        "instance_" + (i % 20), 50 + i % 50, true);
                    
                    // 创建一些临时对象模拟真实场景
                    String tempData = "test_data_" + i + "_" + System.nanoTime();
                    tempData.hashCode(); // 使用对象避免被优化掉
                }
            }));
        }

        // 等待所有任务完成
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new RuntimeException("Task execution failed", e);
            } catch (TimeoutException e) {
                throw new RuntimeException("Task execution timeout", e);
            }
        }

        // 等待异步处理完成
        Thread.sleep(2000);

        // 强制垃圾回收并检查内存
        System.gc();
        Thread.sleep(200);
        System.gc();
        Thread.sleep(200);
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;

        System.out.printf("内存泄漏测试 - 初始内存: %d MB, 最终内存: %d MB, 增长: %d MB%n", 
            initialMemory / (1024 * 1024), finalMemory / (1024 * 1024), 
            memoryIncrease / (1024 * 1024));

        // 验证没有严重的内存泄漏（增长小于100MB）
        assertTrue(memoryIncrease < 100 * 1024 * 1024, 
            String.format("内存增长过大，可能存在内存泄漏 - 增长: %d MB", 
                memoryIncrease / (1024 * 1024)));

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), 
            "内存测试线程池应正常关闭");
    }

    @Test
    @Timeout(45)
    void testDeadlockDetection() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger completedTasks = new AtomicInteger(0);
        CountDownLatch allTasksStarted = new CountDownLatch(10);
        
        // 启动可能导致死锁的并发任务
        for (int t = 0; t < 10; t++) {
            final int threadId = t;
            executor.submit(() -> {
                allTasksStarted.countDown();
                
                try {
                    // 等待所有线程都启动
                    allTasksStarted.await(5, TimeUnit.SECONDS);
                    
                    // 执行可能导致竞争条件的操作
                    for (int i = 0; i < 500; i++) {
                        metricsCollector.recordRequest("deadlock_test_" + threadId, 
                            "GET", 100, "200");
                        metricsCollector.recordBackendCall("deadlock_adapter", 
                            "instance_" + threadId, 50, true);
                        
                        // 交替访问不同的指标类型，增加竞争可能性
                        if (i % 2 == 0) {
                            metricsCollector.recordRateLimit("deadlock_test_" + threadId, 
                                "token-bucket", true);
                        } else {
                            metricsCollector.recordCircuitBreaker("deadlock_test_" + threadId, 
                                "CLOSED", "SUCCESS");
                        }
                    }
                    
                    completedTasks.incrementAndGet();
                    
                } catch (Exception e) {
                    System.err.println("线程 " + threadId + " 发生异常: " + e.getMessage());
                }
            });
        }

        // 等待所有任务完成或超时
        executor.shutdown();
        boolean completed = executor.awaitTermination(30, TimeUnit.SECONDS);
        
        if (!completed) {
            // 如果超时，可能存在死锁
            executor.shutdownNow();
            fail("检测到可能的死锁 - 任务未在预期时间内完成");
        }

        System.out.printf("死锁检测测试完成 - 完成任务数: %d/10%n", completedTasks.get());
        
        // 验证所有任务都成功完成
        assertEquals(10, completedTasks.get(), "所有任务都应该成功完成，没有死锁");
    }

    @Test
    @Timeout(30)
    void testResourceContentionHandling() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(50);
        AtomicLong successfulOperations = new AtomicLong(0);
        AtomicLong failedOperations = new AtomicLong(0);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(50);

        // 创建高资源竞争场景
        for (int t = 0; t < 50; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int i = 0; i < 200; i++) {
                        try {
                            // 所有线程同时访问相同的指标名称，增加竞争
                            metricsCollector.recordRequest("contention_test", "POST", 
                                100 + i, "200");
                            metricsCollector.recordBackendCall("contention_adapter", 
                                "shared_instance", 50 + i, true);
                            
                            successfulOperations.incrementAndGet();
                            
                        } catch (Exception e) {
                            failedOperations.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // 统一开始，最大化资源竞争
        startLatch.countDown();
        
        // 等待完成
        assertTrue(completionLatch.await(20, TimeUnit.SECONDS), 
            "资源竞争测试应在20秒内完成");

        // 等待异步处理
        Thread.sleep(1000);

        long totalOperations = successfulOperations.get() + failedOperations.get();
        double successRate = (successfulOperations.get() / (double) totalOperations) * 100;

        System.out.printf("资源竞争测试 - 成功操作: %d, 失败操作: %d, 成功率: %.2f%%%n", 
            successfulOperations.get(), failedOperations.get(), successRate);

        // 验证在高竞争下仍有良好的成功率
        assertTrue(successRate > 95.0, 
            String.format("在资源竞争下成功率应大于95%%，实际: %.2f%%", successRate));

        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), 
            "资源竞争测试线程池应正常关闭");
    }
}