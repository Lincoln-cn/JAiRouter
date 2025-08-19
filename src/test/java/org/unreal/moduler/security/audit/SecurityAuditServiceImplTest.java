package org.unreal.moduler.security.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.security.audit.SecurityAuditServiceImpl;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * SecurityAuditServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SecurityAuditServiceImplTest {
    
    private SecurityAuditServiceImpl auditService;
    
    @BeforeEach
    void setUp() {
        auditService = new SecurityAuditServiceImpl();
    }
    
    @Test
    void testRecordEvent() {
        // 准备测试数据
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType("TEST_EVENT")
                .userId("testUser")
                .clientIp("192.168.1.1")
                .action("TEST_ACTION")
                .success(true)
                .build();
        
        // 执行测试
        StepVerifier.create(auditService.recordEvent(event))
                .verifyComplete();
        
        // 验证事件已记录
        StepVerifier.create(auditService.queryEvents(
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1),
                "TEST_EVENT",
                "testUser",
                10
        ))
                .expectNextMatches(recordedEvent -> 
                        "TEST_EVENT".equals(recordedEvent.getEventType()) &&
                        "testUser".equals(recordedEvent.getUserId()) &&
                        recordedEvent.getEventId() != null &&
                        recordedEvent.getTimestamp() != null
                )
                .verifyComplete();
    }
    
    @Test
    void testRecordAuthenticationEvent_Success() {
        // 执行测试
        StepVerifier.create(auditService.recordAuthenticationEvent(
                "testUser", "192.168.1.1", "TestAgent", true, null))
                .verifyComplete();
        
        // 验证事件已记录
        StepVerifier.create(auditService.queryEvents(
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1),
                "AUTHENTICATION_SUCCESS",
                "testUser",
                10
        ))
                .expectNextMatches(event -> 
                        "AUTHENTICATION_SUCCESS".equals(event.getEventType()) &&
                        "testUser".equals(event.getUserId()) &&
                        "192.168.1.1".equals(event.getClientIp()) &&
                        event.isSuccess()
                )
                .verifyComplete();
    }
    
    @Test
    void testRecordAuthenticationEvent_Failure() {
        // 执行测试
        StepVerifier.create(auditService.recordAuthenticationEvent(
                "testUser", "192.168.1.1", "TestAgent", false, "Invalid API Key"))
                .verifyComplete();
        
        // 验证事件已记录
        StepVerifier.create(auditService.queryEvents(
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1),
                "AUTHENTICATION_FAILURE",
                "testUser",
                10
        ))
                .expectNextMatches(event -> 
                        "AUTHENTICATION_FAILURE".equals(event.getEventType()) &&
                        "testUser".equals(event.getUserId()) &&
                        !event.isSuccess() &&
                        "Invalid API Key".equals(event.getFailureReason())
                )
                .verifyComplete();
    }
    
    @Test
    void testRecordSanitizationEvent() {
        // 执行测试
        StepVerifier.create(auditService.recordSanitizationEvent(
                "testUser", "application/json", "rule-001", 3))
                .verifyComplete();
        
        // 验证事件已记录
        StepVerifier.create(auditService.queryEvents(
                LocalDateTime.now().minusMinutes(1),
                LocalDateTime.now().plusMinutes(1),
                "DATA_SANITIZATION",
                "testUser",
                10
        ))
                .expectNextMatches(event -> {
                    Map<String, Object> additionalData = event.getAdditionalData();
                    return "DATA_SANITIZATION".equals(event.getEventType()) &&
                           "testUser".equals(event.getUserId()) &&
                           event.isSuccess() &&
                           "application/json".equals(additionalData.get("contentType")) &&
                           "rule-001".equals(additionalData.get("ruleId")) &&
                           Integer.valueOf(3).equals(additionalData.get("matchCount"));
                })
                .verifyComplete();
    }
    
    @Test
    void testQueryEvents_WithFilters() {
        // 准备测试数据
        LocalDateTime now = LocalDateTime.now();
        
        SecurityAuditEvent event1 = SecurityAuditEvent.builder()
                .eventType("TYPE_A")
                .userId("user1")
                .timestamp(now.minusMinutes(5))
                .success(true)
                .build();
                
        SecurityAuditEvent event2 = SecurityAuditEvent.builder()
                .eventType("TYPE_B")
                .userId("user2")
                .timestamp(now.minusMinutes(3))
                .success(true)
                .build();
                
        SecurityAuditEvent event3 = SecurityAuditEvent.builder()
                .eventType("TYPE_A")
                .userId("user1")
                .timestamp(now.minusMinutes(1))
                .success(false)
                .build();
        
        // 记录事件
        Mono.when(
                auditService.recordEvent(event1),
                auditService.recordEvent(event2),
                auditService.recordEvent(event3)
        ).block();
        
        // 测试按事件类型过滤
        StepVerifier.create(auditService.queryEvents(
                now.minusMinutes(10),
                now.plusMinutes(1),
                "TYPE_A",
                null,
                10
        ))
                .expectNextCount(2)
                .verifyComplete();
        
        // 测试按用户ID过滤
        StepVerifier.create(auditService.queryEvents(
                now.minusMinutes(10),
                now.plusMinutes(1),
                null,
                "user1",
                10
        ))
                .expectNextCount(2)
                .verifyComplete();
        
        // 测试限制数量
        StepVerifier.create(auditService.queryEvents(
                now.minusMinutes(10),
                now.plusMinutes(1),
                null,
                null,
                1
        ))
                .expectNextCount(1)
                .verifyComplete();
    }
    
    @Test
    void testGetSecurityStatistics() {
        // 准备测试数据
        LocalDateTime now = LocalDateTime.now();
        
        // 记录一些测试事件
        Mono.when(
                auditService.recordAuthenticationEvent("user1", "192.168.1.1", "Agent1", true, null),
                auditService.recordAuthenticationEvent("user2", "192.168.1.2", "Agent2", false, "Invalid key"),
                auditService.recordSanitizationEvent("user1", "application/json", "rule-001", 2),
                auditService.recordSanitizationEvent("user2", "text/plain", "rule-002", 1)
        ).block();
        
        // 执行测试
        StepVerifier.create(auditService.getSecurityStatistics(
                now.minusMinutes(5),
                now.plusMinutes(1)
        ))
                .expectNextMatches(stats -> {
                    Integer totalEvents = (Integer) stats.get("totalEvents");
                    Map<String, Long> eventTypeStats = (Map<String, Long>) stats.get("eventTypeStatistics");
                    Map<String, Long> authStats = (Map<String, Long>) stats.get("authenticationStatistics");
                    Long sanitizationCount = (Long) stats.get("sanitizationCount");
                    
                    return totalEvents == 4 &&
                           eventTypeStats.get("AUTHENTICATION_SUCCESS") == 1 &&
                           eventTypeStats.get("AUTHENTICATION_FAILURE") == 1 &&
                           eventTypeStats.get("DATA_SANITIZATION") == 2 &&
                           authStats.get("successCount") == 1 &&
                           authStats.get("failureCount") == 1 &&
                           sanitizationCount == 2;
                })
                .verifyComplete();
    }
    
    @Test
    void testShouldTriggerAlert() {
        // 记录多个相同类型的事件
        Mono.when(
                auditService.recordAuthenticationEvent("user1", "192.168.1.1", "Agent1", false, "Invalid key"),
                auditService.recordAuthenticationEvent("user2", "192.168.1.2", "Agent2", false, "Invalid key"),
                auditService.recordAuthenticationEvent("user3", "192.168.1.3", "Agent3", false, "Invalid key")
        ).block();
        
        // 测试告警触发 - 应该触发（3个事件 >= 阈值3）
        StepVerifier.create(auditService.shouldTriggerAlert("AUTHENTICATION_FAILURE", 5, 3))
                .expectNext(true)
                .verifyComplete();
        
        // 测试告警不触发 - 不应该触发（3个事件 < 阈值5）
        StepVerifier.create(auditService.shouldTriggerAlert("AUTHENTICATION_FAILURE", 5, 5))
                .expectNext(false)
                .verifyComplete();
        
        // 测试不存在的事件类型
        StepVerifier.create(auditService.shouldTriggerAlert("NON_EXISTENT_EVENT", 5, 1))
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    void testCleanupExpiredLogs() {
        // 准备测试数据 - 创建一个过期的事件
        SecurityAuditEvent oldEvent = SecurityAuditEvent.builder()
                .eventType("OLD_EVENT")
                .userId("oldUser")
                .timestamp(LocalDateTime.now().minusDays(10))
                .success(true)
                .build();
                
        SecurityAuditEvent recentEvent = SecurityAuditEvent.builder()
                .eventType("RECENT_EVENT")
                .userId("recentUser")
                .timestamp(LocalDateTime.now().minusMinutes(1))
                .success(true)
                .build();
        
        // 记录事件
        Mono.when(
                auditService.recordEvent(oldEvent),
                auditService.recordEvent(recentEvent)
        ).block();
        
        // 执行清理（保留7天）
        StepVerifier.create(auditService.cleanupExpiredLogs(7))
                .expectNextMatches(count -> count >= 1) // 至少清理了1个过期事件
                .verifyComplete();
        
        // 验证最近的事件仍然存在
        StepVerifier.create(auditService.queryEvents(
                LocalDateTime.now().minusMinutes(5),
                LocalDateTime.now().plusMinutes(1),
                "RECENT_EVENT",
                null,
                10
        ))
                .expectNextCount(1)
                .verifyComplete();
    }
}