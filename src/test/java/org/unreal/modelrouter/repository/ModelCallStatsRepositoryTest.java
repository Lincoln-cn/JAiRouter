package org.unreal.modelrouter.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.persistence.repository.ModelCallStatsRepository;
import org.unreal.modelrouter.monitor.dto.ModelCallStats;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelCallStatsRepository 单元测试
 * 
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("ModelCallStatsRepository 测试")
class ModelCallStatsRepositoryTest {

    private ModelCallStatsRepository repository;

    @BeforeEach
    void setUp() {
        // 由于 StoreManager 需要 Spring 容器，这里使用 null 测试核心逻辑
        // 实际使用时会通过 Spring 注入
        repository = new ModelCallStatsRepository(null, null);
    }

    @Test
    @DisplayName("测试 1: 创建统计对象")
    void testGetOrCreate() {
        ModelCallStats stats = repository.getOrCreate("CHAT", "gpt-4");

        assertNotNull(stats);
        assertEquals("gpt-4", stats.getModelName());
        assertEquals("CHAT", stats.getServiceType());
        assertEquals(0, stats.getTotalCalls());
        assertEquals("HEALTHY", stats.getHealthStatus());
    }

    @Test
    @DisplayName("测试 2: 更新统计信息")
    void testUpdateStats() {
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", true, 150);
        repository.updateStats("CHAT", "gpt-4", false, 200);

        ModelCallStats stats = repository.get("CHAT", "gpt-4");

        assertNotNull(stats);
        assertEquals(3, stats.getTotalCalls());
        assertEquals(2, stats.getSuccessCount());
        assertEquals(1, stats.getFailureCount());
        assertTrue(stats.getSuccessRate() > 0.6);
        assertTrue(stats.isActive());
    }

    @Test
    @DisplayName("测试 3: 记录熔断事件")
    void testRecordCircuitBreaker() {
        repository.recordCircuitBreaker("CHAT", "gpt-4");
        repository.recordCircuitBreaker("CHAT", "gpt-4");

        ModelCallStats stats = repository.get("CHAT", "gpt-4");

        assertNotNull(stats);
        assertEquals(2, stats.getCircuitBreakerCount());
        assertEquals(2, stats.getTotalCalls());
    }

    @Test
    @DisplayName("测试 4: 记录限流事件")
    void testRecordRateLimit() {
        repository.recordRateLimit("CHAT", "gpt-4");
        repository.recordRateLimit("CHAT", "gpt-4");
        repository.recordRateLimit("CHAT", "gpt-4");

        ModelCallStats stats = repository.get("CHAT", "gpt-4");

        assertNotNull(stats);
        assertEquals(3, stats.getRateLimitCount());
    }

    @Test
    @DisplayName("测试 5: 记录错误码")
    void testRecordErrorCode() {
        repository.updateStats("CHAT", "gpt-4", false, 100);
        repository.recordErrorCode("CHAT", "gpt-4", "500");
        repository.recordErrorCode("CHAT", "gpt-4", "500");
        repository.recordErrorCode("CHAT", "gpt-4", "503");

        ModelCallStats stats = repository.get("CHAT", "gpt-4");

        assertNotNull(stats);
        assertNotNull(stats.getErrorCodeDistribution());
        assertEquals(2, stats.getErrorCodeDistribution().get("500").intValue());
        assertEquals(1, stats.getErrorCodeDistribution().get("503").intValue());
    }

    @Test
    @DisplayName("测试 6: 获取所有统计")
    void testGetAllStats() {
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-3.5", true, 80);
        repository.updateStats("EMBEDDING", "text-embedding-3", true, 50);

        Collection<ModelCallStats> allStats = repository.getAllStats();

        assertEquals(3, allStats.size());
    }

    @Test
    @DisplayName("测试 7: 按服务类型获取统计")
    void testGetStatsByServiceType() {
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-3.5", true, 80);
        repository.updateStats("EMBEDDING", "text-embedding-3", true, 50);

        List<ModelCallStats> chatStats = repository.getStatsByServiceType("CHAT");

        assertEquals(2, chatStats.size());
        assertTrue(chatStats.stream().allMatch(s -> "CHAT".equals(s.getServiceType())));
    }

    @Test
    @DisplayName("测试 8: 获取所有模型名称")
    void testGetAllModelNames() {
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-3.5", true, 80);
        repository.updateStats("EMBEDDING", "text-embedding-3", true, 50);

        java.util.Set<String> modelNames = repository.getAllModelNames();

        assertEquals(3, modelNames.size());
        assertTrue(modelNames.contains("gpt-4"));
        assertTrue(modelNames.contains("gpt-3.5"));
        assertTrue(modelNames.contains("text-embedding-3"));
    }

    @Test
    @DisplayName("测试 9: 获取总调用次数")
    void testGetTotalCalls() {
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", false, 100);

        assertEquals(3, repository.getTotalCalls());
        assertEquals(2, repository.getTotalSuccess());
        assertEquals(1, repository.getTotalFailure());
    }

    @Test
    @DisplayName("测试 10: 获取总体成功率")
    void testGetOverallSuccessRate() {
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", false, 100);

        double successRate = repository.getOverallSuccessRate();

        assertTrue(successRate >= 0.75);
    }

    @Test
    @DisplayName("测试 11: 删除统计")
    void testRemove() {
        repository.updateStats("CHAT", "gpt-4", true, 100);
        assertNotNull(repository.get("CHAT", "gpt-4"));

        repository.remove("CHAT", "gpt-4");
        assertNull(repository.get("CHAT", "gpt-4"));
    }

    @Test
    @DisplayName("测试 12: 清空所有统计")
    void testClear() {
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-3.5", true, 100);

        assertEquals(2, repository.getCount());

        repository.clear();

        assertEquals(0, repository.getCount());
        assertEquals(0, repository.getTotalCalls());
    }

    @Test
    @DisplayName("测试 13: 获取 Top N 活跃模型")
    void testGetTopActiveModels() {
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", true, 100);

        repository.updateStats("CHAT", "gpt-3.5", true, 100);
        repository.updateStats("CHAT", "gpt-3.5", true, 100);

        repository.updateStats("EMBEDDING", "text-embedding-3", true, 100);

        List<ModelCallStats> top2 = repository.getTopActiveModels(2);

        assertEquals(2, top2.size());
        assertEquals("gpt-4", top2.get(0).getModelName());
        assertEquals(3, top2.get(0).getTotalCalls());
    }

    @Test
    @DisplayName("测试 14: 获取健康状态异常的模型")
    void testGetUnhealthyModels() {
        // 创建健康模型
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", true, 100);
        repository.updateStats("CHAT", "gpt-4", true, 100);

        // 创建不健康模型 - 连续失败
        for (int i = 0; i < 10; i++) {
            repository.updateStats("CHAT", "broken-model", false, 100);
        }

        List<ModelCallStats> unhealthy = repository.getUnhealthyModels();

        assertTrue(unhealthy.stream().anyMatch(s -> "broken-model".equals(s.getModelName())));
    }

    @Test
    @DisplayName("测试 15: 统计对象方法测试")
    void testModelCallStatsMethods() {
        ModelCallStats stats = ModelCallStats.builder()
                .modelName("test-model")
                .serviceType("CHAT")
                .totalCalls(100)
                .successCount(95)
                .failureCount(5)
                .build();

        // 测试成功率计算
        double successRate = stats.calculateSuccessRate();
        assertEquals(0.95, successRate);

        // 测试失败率计算
        double failureRate = stats.calculateFailureRate();
        assertEquals(0.05, failureRate);

        // 测试健康状态判断
        String healthStatus = stats.determineHealthStatus();
        assertEquals("DEGRADED", healthStatus);
    }
}
