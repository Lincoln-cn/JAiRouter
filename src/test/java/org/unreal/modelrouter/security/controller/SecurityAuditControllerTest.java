package org.unreal.modelrouter.security.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.controller.SecurityAuditController;
import org.unreal.modelrouter.dto.SecurityAuditQueryRequest;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.monitoring.security.SecurityAlertService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * SecurityAuditController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SecurityAuditControllerTest {
    
    @Mock
    private SecurityAuditService auditService;
    
    @Mock
    private SecurityAlertService alertService;
    
    private WebTestClient webTestClient;
    
    @BeforeEach
    void setUp() {
        SecurityAuditController controller = new SecurityAuditController(auditService, alertService);
        webTestClient = WebTestClient.bindToController(controller).build();
    }
    
    @Test
    void testQueryAuditLogs() {
        // 准备测试数据
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventId("test-event-1")
                .eventType("AUTHENTICATION_SUCCESS")
                .userId("testUser")
                .clientIp("192.168.1.1")
                .timestamp(LocalDateTime.now())
                .success(true)
                .build();
        
        when(auditService.queryEvents(any(), any(), any(), any(), anyInt()))
                .thenReturn(Flux.just(event));
        
        // 执行测试
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/security/audit/logs")
                        .queryParam("eventType", "AUTHENTICATION_SUCCESS")
                        .queryParam("userId", "testUser")
                        .queryParam("page", 0)
                        .queryParam("size", 10)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events").isArray()
                .jsonPath("$.events[0].eventId").isEqualTo("test-event-1")
                .jsonPath("$.events[0].eventType").isEqualTo("AUTHENTICATION_SUCCESS")
                .jsonPath("$.events[0].userId").isEqualTo("testUser")
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(10);
    }
    
    @Test
    void testQueryAuditLogsAdvanced() {
        // 准备测试数据
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventId("test-event-2")
                .eventType("AUTHENTICATION_FAILURE")
                .userId("testUser")
                .clientIp("192.168.1.100")
                .timestamp(LocalDateTime.now())
                .success(false)
                .failureReason("Invalid API Key")
                .build();
        
        when(auditService.queryEvents(any(), any(), any(), any(), anyInt()))
                .thenReturn(Flux.just(event));
        
        // 准备请求体
        SecurityAuditQueryRequest request = new SecurityAuditQueryRequest();
        request.setEventType("AUTHENTICATION_FAILURE");
        request.setUserId("testUser");
        request.setClientIp("192.168.1.100");
        request.setSuccess(false);
        request.setPage(0);
        request.setSize(20);
        
        // 执行测试
        webTestClient.post()
                .uri("/api/v1/security/audit/logs/query")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events").isArray()
                .jsonPath("$.events[0].eventId").isEqualTo("test-event-2")
                .jsonPath("$.events[0].eventType").isEqualTo("AUTHENTICATION_FAILURE")
                .jsonPath("$.events[0].success").isEqualTo(false)
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20);
    }
    
    @Test
    void testGetSecurityStatistics() {
        // 准备测试数据
        Map<String, Object> auditStats = new HashMap<>();
        auditStats.put("totalEvents", 100);
        auditStats.put("successCount", 80);
        auditStats.put("failureCount", 20);
        
        Map<String, Object> alertStats = new HashMap<>();
        alertStats.put("totalAlerts", 5);
        alertStats.put("activeAlerts", 2);
        
        when(auditService.getSecurityStatistics(any(), any()))
                .thenReturn(Mono.just(auditStats));
        when(alertService.getAlertStatistics())
                .thenReturn(alertStats);
        
        // 执行测试
        webTestClient.get()
                .uri("/api/v1/security/audit/statistics")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.auditStatistics.totalEvents").isEqualTo(100)
                .jsonPath("$.auditStatistics.successCount").isEqualTo(80)
                .jsonPath("$.auditStatistics.failureCount").isEqualTo(20)
                .jsonPath("$.alertStatistics.totalAlerts").isEqualTo(5)
                .jsonPath("$.alertStatistics.activeAlerts").isEqualTo(2)
                .jsonPath("$.generatedAt").exists();
    }
    
    @Test
    void testCleanupExpiredLogs() {
        // 准备测试数据
        when(auditService.cleanupExpiredLogs(anyInt()))
                .thenReturn(Mono.just(50L));
        
        // 执行测试
        webTestClient.delete()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/security/audit/logs/cleanup")
                        .queryParam("retentionDays", 30)
                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.deletedCount").isEqualTo(50)
                .jsonPath("$.retentionDays").isEqualTo(30)
                .jsonPath("$.cleanupTime").exists();
    }
    
    @Test
    void testCheckAlertStatus() {
        // 准备测试数据
        when(auditService.shouldTriggerAlert(anyString(), anyInt(), anyInt()))
                .thenReturn(Mono.just(true));
        
        // 执行测试
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/security/audit/alerts/check")
                        .queryParam("eventType", "AUTHENTICATION_FAILURE")
                        .queryParam("timeWindowMinutes", 5)
                        .queryParam("threshold", 10)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.eventType").isEqualTo("AUTHENTICATION_FAILURE")
                .jsonPath("$.timeWindowMinutes").isEqualTo(5)
                .jsonPath("$.threshold").isEqualTo(10)
                .jsonPath("$.shouldTriggerAlert").isEqualTo(true)
                .jsonPath("$.checkTime").exists();
    }
    
    @Test
    void testGetAlertStatistics() {
        // 准备测试数据
        Map<String, Object> alertStats = new HashMap<>();
        alertStats.put("totalAlerts", 10);
        alertStats.put("activeAlertsLast24Hours", 3);
        
        when(alertService.getAlertStatistics())
                .thenReturn(alertStats);
        
        // 执行测试
        webTestClient.get()
                .uri("/api/v1/security/audit/alerts/statistics")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalAlerts").isEqualTo(10)
                .jsonPath("$.activeAlertsLast24Hours").isEqualTo(3)
                .jsonPath("$.retrievedAt").exists();
    }
    
    @Test
    void testResetAlertStatistics() {
        // 执行测试
        webTestClient.post()
                .uri("/api/v1/security/audit/alerts/reset")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.message").isEqualTo("告警统计已重置")
                .jsonPath("$.resetTime").exists();
    }
    
    @Test
    void testQueryAuditLogsWithDefaultParameters() {
        // 准备测试数据
        when(auditService.queryEvents(any(), any(), isNull(), isNull(), anyInt()))
                .thenReturn(Flux.empty());
        
        // 执行测试 - 不提供任何查询参数
        webTestClient.get()
                .uri("/api/v1/security/audit/logs")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events").isArray()
                .jsonPath("$.page").isEqualTo(0)
                .jsonPath("$.size").isEqualTo(20)
                .jsonPath("$.startTime").exists()
                .jsonPath("$.endTime").exists();
    }
    
    @Test
    void testQueryAuditLogsWithFilters() {
        // 准备测试数据
        SecurityAuditEvent successEvent = SecurityAuditEvent.builder()
                .eventId("success-event")
                .eventType("AUTHENTICATION_SUCCESS")
                .userId("user1")
                .clientIp("192.168.1.1")
                .success(true)
                .build();
        
        SecurityAuditEvent failureEvent = SecurityAuditEvent.builder()
                .eventId("failure-event")
                .eventType("AUTHENTICATION_FAILURE")
                .userId("user2")
                .clientIp("192.168.1.2")
                .success(false)
                .build();
        
        when(auditService.queryEvents(any(), any(), any(), any(), anyInt()))
                .thenReturn(Flux.just(successEvent, failureEvent));
        
        // 执行测试 - 只查询成功的事件
        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/security/audit/logs")
                        .queryParam("success", true)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.events").isArray()
                .jsonPath("$.events[0].success").isEqualTo(true);
    }
}