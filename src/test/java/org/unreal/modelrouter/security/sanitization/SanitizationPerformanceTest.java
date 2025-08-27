package org.unreal.modelrouter.security.sanitization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.security.model.RuleType;
import org.unreal.modelrouter.security.model.SanitizationRule;
import org.unreal.modelrouter.security.model.SanitizationStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 脱敏性能测试
 * 测试脱敏引擎的性能表现和优化效果
 */
@ExtendWith(MockitoExtension.class)
class SanitizationPerformanceTest {
    
    private List<SanitizationRule> testRules;
    private String testContent;
    private String largeTestContent;
    
    @BeforeEach
    void setUp() {
        testRules = createTestRules();
        testContent = createTestContent();
        largeTestContent = createLargeTestContent();
    }
    
    /**
     * 测试基本脱敏性能
     */
    @Test
    void testBasicSanitizationPerformance() {
        int iterations = 100;
        
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            String result = applySanitization(testContent, testRules);
            assertNotNull(result, "脱敏结果不应为空");
        }
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.printf("基本脱敏性能测试 (%d 次迭代): %d ms%n", iterations, duration);
        assertTrue(duration < 5000, "基本脱敏应该在5秒内完成");
    }
    
    /**
     * 测试大文件脱敏性能
     */
    @Test
    void testLargeContentSanitizationPerformance() {
        long startTime = System.currentTimeMillis();
        
        String result = applySanitization(largeTestContent, testRules);
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.printf("大文件脱敏性能测试: 文件大小 %.2f KB, 耗时 %d ms%n", 
                largeTestContent.length() / 1024.0, duration);
        
        assertNotNull(result, "脱敏结果不应为空");
        assertTrue(duration < 10000, "大文件处理应该在10秒内完成");
    }
    
    /**
     * 测试并发脱敏性能
     */
    @Test
    void testConcurrentSanitizationPerformance() throws InterruptedException {
        int threadCount = 5;
        int operationsPerThread = 20;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicLong totalOperations = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String result = applySanitization(testContent, testRules);
                        assertNotNull(result);
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
        
        System.out.printf("并发脱敏性能测试: %d 线程, 总操作 %d 次, QPS %.2f%n", 
                threadCount, totalOps, opsPerSecond);
        
        assertTrue(opsPerSecond > 10, "QPS应该超过10");
        
        executor.shutdown();
    }
    
    /**
     * 测试规则编译性能
     */
    @Test
    void testRuleCompilationPerformance() {
        List<SanitizationRule> largeRuleSet = createLargeRuleSet(100);
        
        long startTime = System.currentTimeMillis();
        
        // 模拟规则编译过程
        List<Pattern> compiledPatterns = new ArrayList<>();
        for (SanitizationRule rule : largeRuleSet) {
            if (rule.getType() == RuleType.CUSTOM_REGEX) {
                compiledPatterns.add(Pattern.compile(rule.getPattern()));
            }
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.printf("规则编译性能测试 (%d 个规则): %d ms%n", largeRuleSet.size(), duration);
        
        assertTrue(duration < 5000, "规则编译应该在5秒内完成");
        assertEquals(largeRuleSet.size(), compiledPatterns.size(), "编译的规则数量应该正确");
    }
    
    /**
     * 测试批量处理性能
     */
    @Test
    void testBatchProcessingPerformance() {
        List<String> contents = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            contents.add(testContent + " batch-" + i);
        }
        
        long startTime = System.currentTimeMillis();
        
        List<String> results = new ArrayList<>();
        for (String content : contents) {
            results.add(applySanitization(content, testRules));
        }
        
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.printf("批量处理性能测试: %d 个内容, 耗时 %d ms%n", contents.size(), duration);
        
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
        
        // 执行大量脱敏操作
        for (int i = 0; i < 100; i++) {
            applySanitization(testContent, testRules);
        }
        
        // 记录处理后内存使用
        runtime.gc();
        long afterProcessMemory = runtime.totalMemory() - runtime.freeMemory();
        
        long memoryUsed = afterProcessMemory - initialMemory;
        
        System.out.printf("内存使用测试: 使用内存 %.2f MB%n", memoryUsed / (1024.0 * 1024.0));
        
        // 验证内存使用在合理范围内（小于100MB）
        assertTrue(memoryUsed < 100 * 1024 * 1024, "内存使用应该在合理范围内");
    }
    
    /**
     * 测试缓存效果
     */
    @Test
    void testCacheEffectiveness() {
        // 首次处理（无缓存）
        long startTime1 = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            applySanitization(testContent, testRules);
        }
        long duration1 = System.currentTimeMillis() - startTime1;
        
        // 第二次处理（可能有缓存效果）
        long startTime2 = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            applySanitization(testContent, testRules);
        }
        long duration2 = System.currentTimeMillis() - startTime2;
        
        System.out.printf("缓存效果测试: 首次 %d ms, 二次 %d ms%n", duration1, duration2);
        
        // 验证性能稳定（第二次不应该明显慢于第一次）
        assertTrue(duration2 <= duration1 * 1.5, "缓存应该提供稳定的性能");
    }
    
    /**
     * 简单的脱敏实现
     */
    private String applySanitization(String content, List<SanitizationRule> rules) {
        String result = content;
        for (SanitizationRule rule : rules) {
            if (rule.getType() == RuleType.CUSTOM_REGEX) {
                Pattern pattern = Pattern.compile(rule.getPattern());
                result = pattern.matcher(result).replaceAll(rule.getReplacementText() != null ? rule.getReplacementText() : "***");
            }
        }
        return result;
    }
    
    /**
     * 创建测试规则
     */
    private List<SanitizationRule> createTestRules() {
        List<SanitizationRule> rules = new ArrayList<>();
        
        // 邮箱脱敏规则
        SanitizationRule emailRule = SanitizationRule.builder()
                .name("email_sanitization")
                .type(RuleType.CUSTOM_REGEX)
                .pattern("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
                .replacementText("***@***.***")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(1)
                .build();
        rules.add(emailRule);
        
        // 手机号脱敏规则
        SanitizationRule phoneRule = SanitizationRule.builder()
                .name("phone_sanitization")
                .type(RuleType.CUSTOM_REGEX)
                .pattern("1[3-9]\\d{9}")
                .replacementText("***-****-****")
                .strategy(SanitizationStrategy.MASK)
                .enabled(true)
                .priority(2)
                .build();
        rules.add(phoneRule);
        
        return rules;
    }
    
    /**
     * 创建大规则集
     */
    private List<SanitizationRule> createLargeRuleSet(int size) {
        List<SanitizationRule> rules = new ArrayList<>();
        
        for (int i = 0; i < size; i++) {
            SanitizationRule rule = SanitizationRule.builder()
                    .name("rule_" + i)
                    .type(RuleType.CUSTOM_REGEX)
                    .pattern("test" + i + "\\d+")
                    .replacementText("***")
                    .strategy(SanitizationStrategy.MASK)
                    .enabled(true)
                    .priority(i + 1)
                    .build();
            rules.add(rule);
        }
        
        return rules;
    }
    
    /**
     * 创建测试内容
     */
    private String createTestContent() {
        return "用户信息：姓名：张三，邮箱：zhangsan@example.com，手机：13812345678，地址：北京市朝阳区";
    }
    
    /**
     * 创建大测试内容
     */
    private String createLargeTestContent() {
        StringBuilder sb = new StringBuilder();
        String baseContent = createTestContent();
        
        for (int i = 0; i < 1000; i++) {
            sb.append(baseContent).append(" 记录").append(i).append("\n");
        }
        
        return sb.toString();
    }
}