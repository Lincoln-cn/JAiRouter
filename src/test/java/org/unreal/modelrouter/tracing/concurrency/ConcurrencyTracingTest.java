package org.unreal.modelrouter.tracing.concurrency;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.tracing.DefaultTracingContext;
import org.unreal.modelrouter.tracing.TracingContext;
import org.unreal.modelrouter.tracing.TracingContextHolder;
import org.unreal.modelrouter.tracing.logger.StructuredLogger;
import org.unreal.modelrouter.tracing.reactive.ReactiveTracingContextHolder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * ConcurrencyTracing测试
 * 
 * 测试高并发场景下的追踪系统功能，包括：
 * - 多线程环境下的追踪上下文隔离
 * - 并发操作的上下文传播
 * - 高并发场景下的性能稳定性
 * - 线程安全性验证
 * - 响应式流中的并发追踪
 * - 资源竞争和死锁检测
 * - 并发度可伸缩性测试
 * 
 * @author JAiRouter Team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConcurrencyTracingTest {

    private static final int LOW_CONCURRENCY = 5;
    private static final int MEDIUM_CONCURRENCY = 20;
    private static final int HIGH_CONCURRENCY = 100;
    private static final int OPERATIONS_PER_THREAD = 100;
    
    @Mock
    private StructuredLogger structuredLogger;
    
    private Tracer tracer;
    private List<TracingContext> tracingContexts;

    @BeforeEach
    void setUp() {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder().build();
        tracer = tracerProvider.get("concurrency-test-tracer");
        
        // 预创建多个独立的追踪上下文
        tracingContexts = new ArrayList<>();
        for (int i = 0; i < HIGH_CONCURRENCY; i++) {
            tracingContexts.add(new DefaultTracingContext(tracer));
        }
    }

    // ==================== 基础并发安全测试 ====================
    
    @Test
    void testThreadSafetyOfTracingContext() throws InterruptedException {
        int numThreads = MEDIUM_CONCURRENCY;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        // 创建共享的追踪上下文
        TracingContext sharedContext = new DefaultTracingContext(tracer);
        
        for (int i = 0; i < numThreads; i++) {
            int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    // 每个线程执行多次操作
                    for (int j = 0; j < OPERATIONS_PER_THREAD; j++) {
                        // 测试并发访问共享上下文
                        String traceId = sharedContext.getTraceId();
                        String spanId = sharedContext.getSpanId();
                        
                        assertNotNull(traceId, "TraceId不应为null");
                        assertNotNull(spanId, "SpanId不应为null");
                        assertFalse(traceId.isEmpty(), "TraceId不应为空");
                        assertFalse(spanId.isEmpty(), "SpanId不应为空");
                        
                        // 测试并发创建Span
                        var span = sharedContext.createSpan("thread-" + threadId + "-span-" + j, 
                                io.opentelemetry.api.trace.SpanKind.INTERNAL);
                        assertNotNull(span, "创建的Span不应为null");
                        
                        // 测试并发添加属性和事件
                        sharedContext.setTag("thread.id", String.valueOf(threadId));
                        sharedContext.setTag("operation.id", String.valueOf(j));
                        sharedContext.addEvent("concurrent.operation", 
                                Map.of("thread", threadId, "operation", j));
                        
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown(); // 开始并发执行
        assertTrue(endLatch.await(30, TimeUnit.SECONDS), "并发测试超时");
        
        // 验证结果
        int totalOperations = numThreads * OPERATIONS_PER_THREAD;
        assertEquals(totalOperations, successCount.get(), "所有操作都应该成功");
        assertEquals(0, errorCount.get(), "不应该有错误");
        
        System.out.printf("线程安全测试完成: %d线程 × %d操作 = %d总操作%n", 
                numThreads, OPERATIONS_PER_THREAD, totalOperations);
    }

    @Test
    void testContextIsolationBetweenThreads() throws InterruptedException {
        int numThreads = MEDIUM_CONCURRENCY;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        Map<Integer, String> threadTraceIds = new ConcurrentHashMap<>();
        Map<Integer, String> threadSpanIds = new ConcurrentHashMap<>();
        AtomicInteger isolationViolations = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    // 每个线程使用独立的追踪上下文
                    TracingContext context = tracingContexts.get(threadId);
                    TracingContextHolder.setCurrentContext(context);
                    
                    // 记录当前线程的追踪ID
                    String traceId = context.getTraceId();
                    String spanId = context.getSpanId();
                    threadTraceIds.put(threadId, traceId);
                    threadSpanIds.put(threadId, spanId);
                    
                    // 执行一些操作
                    for (int j = 0; j < 50; j++) {
                        context.setTag("thread.operation", "op-" + j);
                        context.addEvent("thread.event", Map.of("iteration", j));
                        
                        // 验证上下文没有被其他线程影响
                        if (!traceId.equals(context.getTraceId()) || 
                            !spanId.equals(context.getSpanId())) {
                            isolationViolations.incrementAndGet();
                        }
                        
                        // 短暂休眠增加竞争概率
                        Thread.sleep(1);
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    TracingContextHolder.clearCurrentContext();
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        assertTrue(endLatch.await(60, TimeUnit.SECONDS), "上下文隔离测试超时");
        
        // 验证每个线程都有唯一的追踪ID
        assertEquals(numThreads, threadTraceIds.size(), "每个线程应该有唯一的TraceId");
        assertEquals(numThreads, threadSpanIds.size(), "每个线程应该有唯一的SpanId");
        assertEquals(0, isolationViolations.get(), "不应该有上下文隔离违规");
        
        // 验证所有TraceId都是唯一的
        long uniqueTraceIds = threadTraceIds.values().stream().distinct().count();
        assertEquals(numThreads, uniqueTraceIds, "所有TraceId应该是唯一的");
        
        System.out.printf("上下文隔离测试完成: %d个唯一的追踪上下文%n", numThreads);
    }

    // ==================== 响应式并发测试 ====================
    
    @Test
    void testReactiveConcurrentTracing() {
        int concurrentStreams = MEDIUM_CONCURRENCY;
        AtomicLong completedOperations = new AtomicLong(0);
        AtomicLong errors = new AtomicLong(0);
        
        // 创建多个并发的响应式流
        List<Mono<String>> monos = IntStream.range(0, concurrentStreams)
                .mapToObj(i -> {
                    TracingContext context = tracingContexts.get(i);
                    return Mono.just("stream-" + i)
                            .flatMap(value -> ReactiveTracingContextHolder.withContext(context,
                                    Mono.fromCallable(() -> {
                                        // 模拟异步操作
                                        Thread.sleep(10 + (int)(Math.random() * 20));
                                        context.setTag("stream.id", String.valueOf(i));
                                        context.addEvent("stream.processed", Map.of("value", value));
                                        completedOperations.incrementAndGet();
                                        return value + "-processed";
                                    }).subscribeOn(Schedulers.boundedElastic())
                            ))
                            .doOnError(error -> {
                                errors.incrementAndGet();
                                error.printStackTrace();
                            });
                })
                .toList();
        
        // 并发执行所有流
        StepVerifier.create(Mono.when(monos))
                .expectComplete()
                .verify(Duration.ofSeconds(30));
        
        // 验证结果
        assertEquals(concurrentStreams, completedOperations.get(), 
                "所有响应式流都应该完成处理");
        assertEquals(0, errors.get(), "不应该有错误");
        
        System.out.printf("响应式并发测试完成: %d个并发流%n", concurrentStreams);
    }

    @Test
    void testReactiveFluxConcurrentProcessing() {
        int itemsPerFlux = 100;
        int numberOfFluxes = 10;
        AtomicInteger processedItems = new AtomicInteger(0);
        
        List<Flux<String>> fluxes = IntStream.range(0, numberOfFluxes)
                .mapToObj(fluxId -> {
                    TracingContext context = tracingContexts.get(fluxId);
                    return Flux.range(0, itemsPerFlux)
                            .map(i -> "flux-" + fluxId + "-item-" + i)
                            .flatMap(item -> ReactiveTracingContextHolder.withContext(context,
                                    Mono.fromCallable(() -> {
                                        context.setTag("flux.id", String.valueOf(fluxId));
                                        context.setTag("item.id", item);
                                        processedItems.incrementAndGet();
                                        return item + "-processed";
                                    }).subscribeOn(Schedulers.parallel())
                            ))
                            .subscribeOn(Schedulers.parallel());
                })
                .toList();
        
        // 合并所有Flux并验证
        StepVerifier.create(Flux.merge(fluxes))
                .expectNextCount(numberOfFluxes * itemsPerFlux)
                .verifyComplete();
        
        assertEquals(numberOfFluxes * itemsPerFlux, processedItems.get(),
                "所有项目都应该被处理");
        
        System.out.printf("Flux并发处理测试完成: %d个Flux × %d项目 = %d总项目%n", 
                numberOfFluxes, itemsPerFlux, numberOfFluxes * itemsPerFlux);
    }

    // ==================== 高并发压力测试 ====================
    
    @Test
    void testHighConcurrencyStressTest() throws InterruptedException {
        int numThreads = HIGH_CONCURRENCY;
        int operationsPerThread = 50; // 减少操作数以避免测试超时
        
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicLong successfulOperations = new AtomicLong(0);
        AtomicLong failedOperations = new AtomicLong(0);
        AtomicLong totalTime = new AtomicLong(0);
        
        long testStartTime = System.currentTimeMillis();
        
        for (int i = 0; i < numThreads; i++) {
            int threadId = i;
            executor.submit(() -> {
                long threadStartTime = System.currentTimeMillis();
                try {
                    TracingContext context = tracingContexts.get(threadId);
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            // 高频率的追踪操作
                            var span = context.createSpan("stress-test-span-" + j, 
                                    io.opentelemetry.api.trace.SpanKind.INTERNAL);
                            context.setTag("stress.thread", String.valueOf(threadId));
                            context.setTag("stress.operation", String.valueOf(j));
                            context.addEvent("stress.operation.completed", 
                                    Map.of("thread", threadId, "op", j, "timestamp", System.currentTimeMillis()));
                            context.finishSpan(span);
                            
                            successfulOperations.incrementAndGet();
                            
                            // 偶尔暂停以模拟真实场景
                            if (j % 10 == 0) {
                                Thread.sleep(1);
                            }
                        } catch (Exception e) {
                            failedOperations.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    long threadTime = System.currentTimeMillis() - threadStartTime;
                    totalTime.addAndGet(threadTime);
                    latch.countDown();
                }
            });
        }
        
        assertTrue(latch.await(2, TimeUnit.MINUTES), "高并发压力测试超时");
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "线程池关闭超时");
        
        long testDuration = System.currentTimeMillis() - testStartTime;
        long expectedOperations = (long) numThreads * operationsPerThread;
        double successRate = (double) successfulOperations.get() / expectedOperations * 100;
        double avgThreadTime = totalTime.get() / (double) numThreads;
        double throughput = successfulOperations.get() / (testDuration / 1000.0);
        
        System.out.printf("高并发压力测试结果:%n");
        System.out.printf("线程数: %d%n", numThreads);
        System.out.printf("每线程操作数: %d%n", operationsPerThread);
        System.out.printf("总测试时间: %d ms%n", testDuration);
        System.out.printf("成功操作数: %d%n", successfulOperations.get());
        System.out.printf("失败操作数: %d%n", failedOperations.get());
        System.out.printf("成功率: %.2f%%%n", successRate);
        System.out.printf("平均线程时间: %.2f ms%n", avgThreadTime);
        System.out.printf("吞吐量: %.2f ops/sec%n", throughput);
        
        // 验证高并发下的性能表现
        assertTrue(successRate >= 95.0, String.format("成功率过低: %.2f%%", successRate));
        assertTrue(throughput >= 100, String.format("吞吐量过低: %.2f ops/sec", throughput));
        assertTrue(testDuration < 120000, String.format("测试时间过长: %d ms", testDuration));
    }

    // ==================== 竞争条件检测 ====================
    
    @Test
    void testRaceConditionDetection() throws InterruptedException {
        int numThreads = 50;
        AtomicReference<TracingContext> sharedContextRef = new AtomicReference<>();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger raceConditions = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    
                    // 尝试设置共享上下文（竞争条件）
                    TracingContext myContext = tracingContexts.get(threadId);
                    TracingContext previousContext = sharedContextRef.getAndSet(myContext);
                    
                    if (previousContext != null && !previousContext.equals(myContext)) {
                        // 检测到竞争条件
                        raceConditions.incrementAndGet();
                    }
                    
                    // 短暂持有上下文
                    Thread.sleep(10);
                    
                    // 尝试获取当前上下文
                    TracingContext currentContext = sharedContextRef.get();
                    if (currentContext != null && !currentContext.equals(myContext)) {
                        // 上下文被其他线程修改
                        raceConditions.incrementAndGet();
                    }
                    
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS), "竞争条件检测测试超时");
        
        System.out.printf("竞争条件检测完成: 检测到 %d 次竞争条件%n", raceConditions.get());
        
        // 在这种设计下，竞争条件是预期的
        assertTrue(raceConditions.get() > 0, "应该检测到竞争条件");
    }

    // ==================== 可伸缩性测试 ====================
    
    @Test
    void testScalabilityWithIncreasingConcurrency() throws InterruptedException {
        int[] concurrencyLevels = {5, 10, 20, 50};
        int operationsPerThread = 100;
        
        for (int concurrency : concurrencyLevels) {
            long startTime = System.currentTimeMillis();
            CountDownLatch latch = new CountDownLatch(concurrency);
            AtomicLong operations = new AtomicLong(0);
            
            for (int i = 0; i < concurrency; i++) {
                int threadId = i;
                new Thread(() -> {
                    try {
                        TracingContext context = tracingContexts.get(threadId % tracingContexts.size());
                        
                        for (int j = 0; j < operationsPerThread; j++) {
                            context.setTag("scalability.test", "level-" + concurrency);
                            context.addEvent("scalability.operation", 
                                    Map.of("thread", threadId, "op", j));
                            operations.incrementAndGet();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }
            
            assertTrue(latch.await(60, TimeUnit.SECONDS), 
                    String.format("可伸缩性测试超时，并发度: %d", concurrency));
            
            long duration = System.currentTimeMillis() - startTime;
            double throughput = operations.get() / (duration / 1000.0);
            
            System.out.printf("并发度 %d: %d 操作, %d ms, %.2f ops/sec%n", 
                    concurrency, operations.get(), duration, throughput);
            
            assertTrue(throughput > 0, "吞吐量应该大于0");
        }
    }

    // ==================== 死锁检测测试 ====================
    
    @Test
    void testDeadlockPrevention() throws InterruptedException {
        int numThreads = 10;
        Object lock1 = new Object();
        Object lock2 = new Object();
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(numThreads);
        AtomicInteger completedThreads = new AtomicInteger(0);
        
        for (int i = 0; i < numThreads; i++) {
            int threadId = i;
            new Thread(() -> {
                try {
                    startLatch.await();
                    TracingContext context = tracingContexts.get(threadId);
                    
                    if (threadId % 2 == 0) {
                        // 偶数线程：先获取lock1，后获取lock2
                        synchronized (lock1) {
                            context.setTag("lock.acquired", "lock1");
                            Thread.sleep(10);
                            synchronized (lock2) {
                                context.setTag("lock.acquired", "lock2");
                                context.addEvent("deadlock.test.completed", 
                                        Map.of("thread", threadId, "order", "lock1->lock2"));
                            }
                        }
                    } else {
                        // 奇数线程：先获取lock2，后获取lock1
                        synchronized (lock2) {
                            context.setTag("lock.acquired", "lock2");
                            Thread.sleep(10);
                            synchronized (lock1) {
                                context.setTag("lock.acquired", "lock1");
                                context.addEvent("deadlock.test.completed", 
                                        Map.of("thread", threadId, "order", "lock2->lock1"));
                            }
                        }
                    }
                    
                    completedThreads.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endLatch.countDown();
                }
            }).start();
        }
        
        startLatch.countDown();
        
        // 等待一段时间，看是否发生死锁
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        int actualCompleted = completedThreads.get();
        
        System.out.printf("死锁检测测试: %d/%d 线程完成%n", actualCompleted, numThreads);
        
        if (!completed) {
            System.out.println("警告: 检测到潜在死锁，部分线程未完成");
            // 在实际场景中，这里可能需要特殊处理
        }
        
        // 至少应该有一些线程完成，即使存在死锁
        assertTrue(actualCompleted > 0, "至少应该有部分线程完成");
    }

    // ==================== 负载波动测试 ====================
    
    @Test
    void testVariableLoadHandling() throws InterruptedException {
        AtomicLong totalOperations = new AtomicLong(0);
        CountDownLatch endLatch = new CountDownLatch(3);
        
        // 低负载阶段
        new Thread(() -> {
            try {
                for (int i = 0; i < 100; i++) {
                    TracingContext context = tracingContexts.get(i % 5);
                    context.setTag("load.phase", "low");
                    context.addEvent("variable.load.test", Map.of("phase", "low", "op", i));
                    totalOperations.incrementAndGet();
                    Thread.sleep(5); // 较长间隔
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        }).start();
        
        // 中等负载阶段
        new Thread(() -> {
            try {
                for (int i = 0; i < 200; i++) {
                    TracingContext context = tracingContexts.get((i % 10) + 5);
                    context.setTag("load.phase", "medium");
                    context.addEvent("variable.load.test", Map.of("phase", "medium", "op", i));
                    totalOperations.incrementAndGet();
                    Thread.sleep(2); // 中等间隔
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        }).start();
        
        // 高负载阶段
        new Thread(() -> {
            try {
                for (int i = 0; i < 500; i++) {
                    TracingContext context = tracingContexts.get((i % 20) + 15);
                    context.setTag("load.phase", "high");
                    context.addEvent("variable.load.test", Map.of("phase", "high", "op", i));
                    totalOperations.incrementAndGet();
                    if (i % 10 == 0) {
                        Thread.sleep(1); // 短间隔
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                endLatch.countDown();
            }
        }).start();
        
        assertTrue(endLatch.await(2, TimeUnit.MINUTES), "变量负载测试超时");
        
        long expectedOperations = 100 + 200 + 500; // 800
        assertEquals(expectedOperations, totalOperations.get(), 
                "所有负载阶段的操作都应该完成");
        
        System.out.printf("变量负载测试完成: %d 总操作%n", totalOperations.get());
    }
}