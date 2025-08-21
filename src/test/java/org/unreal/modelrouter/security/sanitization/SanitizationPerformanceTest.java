package org.unreal.modelrouter.security.sanitization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.unreal.modelrouter.security.model.RuleType;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.security.model.SanitizationStrategy;
import org.unreal.modelrouter.sanitization.impl.DefaultSanitizationRuleEngine;
import org.unreal.modelrouter.sanitization.impl.OptimizedSanitizationRuleEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脱敏性能测试
 * 测试脱敏引擎的性能表现和优化效果
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
class SanitizationPerformanceTest {
    
    private DefaultSanitizationRuleEngine defaultEngine;
    private OptimizedSanitizationRuleEngine optimizedEngine;
    private List<SanitizationRule> testRules;
    private String testContent;
    private String largeTestContent;
    
    @BeforeEach
    void setUp() {
        defaultEngine = new DefaultSanitizationRuleEngine();
        optimizedEngine = new OptimizedSanitizationRuleEngine();
        testRules = createTestRules();
        testContent = createTestContent();
        largeTestContent = createLargeTestContent();
    }
    
    /**
     * 测试基本脱敏性能对比
     */
    @Test
    void testBasicSanitizationPerformance() {
        int iterations = 1000;
        
        // 预编译规则
        defaultEngine.compileRules(testRules).block();
        optimizedEngine.compileRules(testRules).block();
        
        // 测试默认引擎性能
        long defaultStartTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            defaultEngine.applySanitizationRules(testContent, testRules, "application/json").block();
        }
        long defaultDuration = System.currentTimeMillis() - defaultStartTime;
        
        // 测试优化引擎性能
        long optimizedStartTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            optimizedEngine.applySanitizationRules(testContent, testRules, "application/json").block();
        }
        long optimizedDuration = System.currentTimeMillis() - optimizedStartTime;
        
        System.out.printf("基本脱敏性能测试 (%d 次迭代):%n", iterations);
        System.out.printf("默认引擎耗时: %d ms%n", defaultDuration);
        System.out.printf("优化引擎耗时: %d ms%n", optimizedDuration);
        System.out.printf("性能提升: %.2f%%%n", ((double)(defaultDuration - optimizedDuration) / defaultDuration) * 100);
        
        assertTrue(optimizedDuration <= defaultDuration, "优化引擎应该不慢于默认引擎");
    }
    
    /**
     * 测试大文件脱敏性能
     */
    @Test
    void testLargeContentSanitizationPerformance() {
        // 预编译规则
        optimizedEngine.compileRules(testRules).block();
        
        long startTime = System.currentTimeMillis();
        
        String result = optimizedEngine.applySanitizationRules(largeTestContent, testRules, "text/plain").block();
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.printf("大文件脱敏性能测试:%n");
        System.out.printf("文件大小: %.2f MB%n", largeTestContent.length() / (1024.0 * 1024.0));
        System.out.printf("处理耗时: %d ms%n", duration);
        System.out.printf("处理速度: %.2f MB/s%n", (largeTestContent.length() / (1024.0 * 1024.0)) / (duration / 1000.0));
        
        assertNotNull(result, "脱敏结果不应为空");
        assertTrue(duration < 10000, "大文件处理应该在10秒内完成");
    }
    
    /**
     * 测试并发脱敏性能
     */
    @Test
    void testConcurrentSanitizationPerformance() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        
        // 预编译规则
        optimizedEngine.compileRules(testRules).block();
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        optimizedEngine.applySanitizationRules(testContent, testRules, "application/json").block();
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
        
        System.out.printf("并发脱敏性能测试:%n");
        System.out.printf("线程数: %d, 每线程操作数: %d%n", threadCount, operationsPerThread);
        System.out.printf("总操作数: %d, 耗时: %d ms%n", totalOps, duration);
        System.out.printf("QPS: %.2f%n", opsPerSecond);
        
        assertTrue(opsPerSecond > 100, "QPS应该超过100");
        
        executor.shutdown();
    }
    
    /**
     * 测试规则编译性能
     */
    @Test
    void testRuleCompilationPerformance() {
        List<SanitizationRule> largeRuleSet = createLargeRuleSet(1000);
        
        // 测试默认引擎编译性能
        long defaultStartTime = System.currentTimeMillis();
        defaultEngine.compileRules(largeRuleSet).block();
        long defaultDuration = System.currentTimeMillis() - defaultStartTime;
        
        // 测试优化引擎编译性能
        long optimizedStartTime = System.currentTimeMillis();
        optimizedEngine.compileRules(largeRuleSet).block();
        long optimizedDuration = System.currentTimeMillis() - optimizedStartTime;
        
        System.out.printf("规则编译性能测试 (%d 个规则):%n", largeRuleSet.size());
        System.out.printf("默认引擎编译耗时: %d ms%n", defaultDuration);
        System.out.printf("优化引擎编译耗时: %d ms%n", optimizedDuration);
        System.out.printf("编译速度提升: %.2f%%%n", ((double)(defaultDuration - optimizedDuration) / defaultDuration) * 100);
        
        assertTrue(defaultDuration < 10000, "规则编译应该在10秒内完成");
        assertTrue(optimizedDuration < 10000, "优化引擎规则编译应该在10秒内完成");
    }
    
    /**
     * 测试批量处理性能
     */
    @Test
    void testBatchProcessingPerformance() {
        List<String> contents = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            contents.add(testContent + " batch-" + i);
        }
        
        // 预编译规则
        optimizedEngine.compileRules(testRules).block();
        
        long startTime = System.currentTimeMillis();
        
        List<String> results = optimizedEngine.applySanitizationRulesBatch(contents, testRules, "application/json")
                .collectList()
                .block();
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.printf("批量处理性能测试:%n");
        System.out.printf("处理内容数: %d%n", contents.size());
        System.out.printf("处理耗时: %d ms%n", duration);
        System.out.printf("平均每个内容耗时: %.2f ms%n", (double) duration / contents.size());
        
        assertNotNull(results, "批量处理结果不应为空");
        assertEquals(contents.size(), results.size(), "结果数量应该与输入数量一致");
        assertTrue(duration < 5000, "批量处理应该在5秒内完成");
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
        
        // 编译大量规则
        List<SanitizationRule> largeRuleSet = createLargeRuleSet(500);
        optimizedEngine.compileRules(largeRuleSet).block();
        
        // 记录编译后内存使用
        runtime.gc();
        long afterCompileMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // 执行大量脱敏操作
        for (int i = 0; i < 100; i++) {
            optimizedEngine.applySanitizationRules(testContent, largeRuleSet, "application/json").block();
        }
        
        // 记录处理后内存使用
        runtime.gc();
        long afterProcessMemory = runtime.totalMemory() - runtime.freeMemory();
        
        long compileMemoryUsed = afterCompileMemory - initialMemory;
        long processMemoryUsed = afterProcessMemory - afterCompileMemory;
        
        System.out.printf("内存使用测试:%n");
        System.out.printf("规则编译内存使用: %d bytes (%.2f MB)%n", compileMemoryUsed, compileMemoryUsed / (1024.0 * 1024.0));
        System.out.printf("脱敏处理内存使用: %d bytes (%.2f MB)%n", processMemoryUsed, processMemoryUsed / (1024.0 * 1024.0));
        System.out.printf("平均每个规则内存使用: %.2f KB%n", compileMemoryUsed / (1024.0 * largeRuleSet.size()));
        
        // 清除缓存
        optimizedEngine.clearAllCaches();
        
        runtime.gc();
        long afterClearMemory = runtime.totalMemory() - runtime.freeMemory();
        
        System.out.printf("清除缓存后内存使用: %d bytes%n", afterClearMemory - initialMemory);
        
        assertTrue(compileMemoryUsed / (1024.0 * largeRuleSet.size()) < 10, "每个规则的内存使用应该小于10KB");
    }
    
    /**
     * 测试缓存效果
     */
    @Test
    void testCacheEffectiveness() {
        // 预编译规则
        optimizedEngine.compileRules(testRules).block();
        
        // 第一次执行（缓存未命中）
        long firstStartTime = System.currentTimeMillis();
        optimizedEngine.applySanitizationRules(testContent, testRules, "application/json").block();
        long firstDuration = System.currentTimeMillis() - firstStartTime;
        
        // 第二次执行（缓存命中）
        long secondStartTime = System.currentTimeMillis();
        optimizedEngine.applySanitizationRules(testContent, testRules, "application/json").block();
        long secondDuration = System.currentTimeMillis() - secondStartTime;
        
        // 多次执行测试缓存稳定性
        long multiStartTime = System.currentTimeMillis();
        for (int i = 0; i < 100; i++) {
            optimizedEngine.applySanitizationRules(testContent, testRules, "application/json").block();
        }
        long multiDuration = System.currentTimeMillis() - multiStartTime;
        double avgDuration = (double) multiDuration / 100;
        
        System.out.printf("缓存效果测试:%n");
        System.out.printf("首次执行耗时: %d ms%n", firstDuration);
        System.out.printf("二次执行耗时: %d ms%n", secondDuration);
        System.out.printf("100次执行平均耗时: %.2f ms%n", avgDuration);
        
        OptimizedSanitizationRuleEngine.CacheStatistics stats = optimizedEngine.getCacheStatistics();
        System.out.printf("缓存统计: %s%n", stats);
        
        assertTrue(avgDuration <= firstDuration, "缓存应该提升性能");
        assertTrue(stats.getCompiledRulesCount() > 0, "应该有编译后的规则缓存");
    }
    
    /**
     * 创建测试规则
     */
    private List<SanitizationRule> createTestRules() {
        List<SanitizationRule> rules = new ArrayList<>();
        
        // 敏感词规则
        rules.add(SanitizationRule.builder()
                .ruleId("sensitive-word-1")
                .name("密码脱敏")
                .type(RuleType.SENSITIVE_WORD)
                .pattern("password")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .applicableContentTypes(List.of("application/json", "text/plain"))
                .replacementChar("*")
                .build());
        
        // PII数据规则
        rules.add(SanitizationRule.builder()
                .ruleId("pii-email")
                .name("邮箱脱敏")
                .type(RuleType.PII_PATTERN)
                .pattern("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                .strategy(SanitizationStrategy.REPLACE)
                .enabled(true)
                .priority(2)
                .applicableContentTypes(List.of("application/json", "text/plain"))
                .replacementText("[EMAIL]")
                .build());
        
        // 手机号规则
        rules.add(SanitizationRule.builder()
                .ruleId("pii-phone")
                .name("手机号脱敏")
                .type(RuleType.PII_PATTERN)
                .pattern("1[3-9]\\d{9}")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(3)
                .applicableContentTypes(List.of("application/json", "text/plain"))
                .replacementChar("*")
                .build());
        
        return rules;
    }
    
    /**
     * 创建大规模规则集
     */
    private List<SanitizationRule> createLargeRuleSet(int count) {
        List<SanitizationRule> rules = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            rules.add(SanitizationRule.builder()
                    .ruleId("rule-" + i)
                    .name("测试规则 " + i)
                    .type(RuleType.SENSITIVE_WORD)
                    .pattern("test" + i)
                    .strategy(SanitizationStrategy.MASK)
                    .enabled(true)
                    .priority(i)
                    .applicableContentTypes(List.of("application/json"))
                    .replacementChar("*")
                    .build());
        }
        
        return rules;
    }
    
    /**
     * 创建测试内容
     */
    private String createTestContent() {
        return """
                {
                    "user": {
                        "name": "张三",
                        "email": "zhangsan@example.com",
                        "phone": "13812345678",
                        "password": "secret123"
                    },
                    "data": {
                        "content": "这是一些测试数据，包含敏感信息",
                        "timestamp": "2024-01-01T00:00:00Z"
                    }
                }
                """;
    }
    
    /**
     * 创建大文件测试内容
     */
    private String createLargeTestContent() {
        StringBuilder sb = new StringBuilder();
        String baseContent = createTestContent();
        
        // 创建约2MB的内容
        for (int i = 0; i < 1000; i++) {
            sb.append(baseContent);
            sb.append("\n--- Record ").append(i).append(" ---\n");
            sb.append("Additional data with email test").append(i).append("@example.com and phone 138").append(String.format("%08d", i)).append("\n");
        }
        
        return sb.toString();
    }
}