package org.unreal.modelrouter.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;

import org.unreal.modelrouter.security.metrics.CleanupMetricsService;
import org.unreal.modelrouter.security.metrics.StorageHealthMetricsService;
import org.unreal.modelrouter.security.service.JwtPersistenceService;
import org.unreal.modelrouter.security.service.JwtCleanupService;
import org.unreal.modelrouter.security.audit.SecurityAuditService;

import java.util.HashMap;
import java.util.Map;

@WebFluxTest(SystemMonitoringController.class)
public class SystemMonitoringControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private JwtPersistenceService jwtPersistenceService;

    @MockBean
    private JwtCleanupService jwtCleanupService;

    @MockBean
    private StorageHealthMetricsService storageHealthMetricsService;

    @MockBean
    private CleanupMetricsService cleanupMetricsService;

    @MockBean
    private SecurityAuditService securityAuditService;

    @Test
    public void testGetSystemStats() {
        // 准备测试数据
        Map<String, Object> cleanupStats = new HashMap<>();
        cleanupStats.put("totalTokensCleaned", 100L);
        
        when(cleanupMetricsService.getCleanupStats()).thenReturn(cleanupStats);

        // 执行测试
        webTestClient.get()
                .uri("/api/monitoring/system/stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    @Test
    public void testGetSystemHealth() {
        // 准备测试数据
        Map<String, Object> healthSummary = new HashMap<>();
        healthSummary.put("redisConnected", true);
        
        Map<String, Object> redisStats = new HashMap<>();
        redisStats.put("responseTimeMs", 10);
        redisStats.put("memoryUsageMB", 50);
        redisStats.put("keyCount", 1000);
        
        healthSummary.put("redisStats", redisStats);
        
        Map<String, Object> memoryStats = new HashMap<>();
        memoryStats.put("usagePercentage", 60);
        memoryStats.put("tokenCount", 500);
        memoryStats.put("blacklistCount", 10);
        
        healthSummary.put("memoryStats", memoryStats);
        healthSummary.put("memoryStatus", "healthy");
        
        when(storageHealthMetricsService.getHealthSummary()).thenReturn(healthSummary);

        // 执行测试
        webTestClient.get()
                .uri("/api/monitoring/system/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    @Test
    public void testGetCleanupStats() {
        // 准备测试数据
        Map<String, Object> cleanupStats = new HashMap<>();
        cleanupStats.put("lastTokensCleaned", 50);
        cleanupStats.put("lastBlacklistEntriesCleaned", 5);
        cleanupStats.put("lastCleanupTime", "2023-01-01 12:00:00");
        cleanupStats.put("totalTokensCleaned", 1000);
        cleanupStats.put("totalBlacklistEntriesCleaned", 100);
        
        when(cleanupMetricsService.getCleanupStats()).thenReturn(cleanupStats);

        // 执行测试
        webTestClient.get()
                .uri("/api/monitoring/system/cleanup-stats")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    @Test
    public void testGetSystemConfig() {
        // 执行测试
        webTestClient.get()
                .uri("/api/monitoring/system/config")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    @Test
    public void testGetRecentAuditEvents() {
        // 执行测试
        webTestClient.get()
                .uri("/api/monitoring/audit/recent")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }

    @Test
    public void testTriggerManualCleanup() {
        // 执行测试
        webTestClient.post()
                .uri("/api/monitoring/system/cleanup")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.success").isEqualTo(true);
    }
}