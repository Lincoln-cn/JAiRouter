package org.unreal.modelrouter.auth.security.audit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.unreal.modelrouter.common.dto.AuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventQuery;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.common.dto.SecurityAlert;
import org.unreal.modelrouter.common.dto.SecurityReport;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity.RiskLevel;
import org.unreal.modelrouter.persistence.jpa.repository.SecurityAuditEventRepository;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExtendedSecurityAuditServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ExtendedSecurityAuditServiceImpl 测试")
class ExtendedSecurityAuditServiceImplTest {

    @Mock
    private SecurityAuditEventRepository auditRepository;

    @Mock
    private AuditEntityMapper entityMapper;

    private ExtendedSecurityAuditServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ExtendedSecurityAuditServiceImpl(auditRepository, entityMapper);
    }

    // ========== JWT令牌审计方法测试 ==========

    @Nested
    @DisplayName("JWT令牌审计方法测试")
    class JwtTokenAuditTests {

        @Test
        @DisplayName("auditTokenIssued - 成功记录令牌颁发")
        void testAuditTokenIssued_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());

            // Act
            var result = service.auditTokenIssued("user-001", "token-123", "192.168.1.1", "Mozilla/5.0");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.JWT_TOKEN_ISSUED &&
                    "user-001".equals(entity.getUserId()) &&
                    "token-123".equals(entity.getResourceId())
            ));
        }

        @Test
        @DisplayName("auditTokenRefreshed - 成功记录令牌刷新")
        void testAuditTokenRefreshed_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());
            when(entityMapper.toJson(anyMap())).thenReturn("{\"oldTokenId\":\"old-123\"}");

            // Act
            var result = service.auditTokenRefreshed("user-001", "old-123", "new-456", "192.168.1.1");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.JWT_TOKEN_REFRESHED
            ));
        }

        @Test
        @DisplayName("auditTokenRevoked - 成功记录令牌撤销")
        void testAuditTokenRevoked_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());
            when(entityMapper.toJson(anyMap())).thenReturn("{\"reason\":\"user logout\"}");

            // Act
            var result = service.auditTokenRevoked("user-001", "token-123", "user logout", "admin");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.JWT_TOKEN_REVOKED &&
                    entity.getRiskLevel() == RiskLevel.MEDIUM
            ));
        }

        @Test
        @DisplayName("auditTokenValidated - 验证成功")
        void testAuditTokenValidated_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());

            // Act
            var result = service.auditTokenValidated("user-001", "token-123", true, "192.168.1.1");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getSuccess()
            ));
        }

        @Test
        @DisplayName("auditTokenValidated - 验证失败")
        void testAuditTokenValidated_Failure() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());

            // Act
            var result = service.auditTokenValidated("user-001", "token-123", false, "192.168.1.1");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    !entity.getSuccess() &&
                    entity.getFailureReason() != null
            ));
        }
    }

    // ========== API Key审计方法测试 ==========

    @Nested
    @DisplayName("API Key审计方法测试")
    class ApiKeyAuditTests {

        @Test
        @DisplayName("auditApiKeyCreated - 成功记录创建")
        void testAuditApiKeyCreated_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());

            // Act
            var result = service.auditApiKeyCreated("key-001", "admin", "192.168.1.1");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.API_KEY_CREATED &&
                    entity.getSuccess()
            ));
        }

        @Test
        @DisplayName("auditApiKeyUsed - 使用成功")
        void testAuditApiKeyUsed_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());
            when(entityMapper.toJson(anyMap())).thenReturn("{\"endpoint\":\"/api/v1/chat\"}");

            // Act
            var result = service.auditApiKeyUsed("key-001", "/api/v1/chat", "192.168.1.1", true);

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.API_KEY_USED &&
                    entity.getSuccess()
            ));
        }

        @Test
        @DisplayName("auditApiKeyUsed - 使用失败")
        void testAuditApiKeyUsed_Failure() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());
            when(entityMapper.toJson(anyMap())).thenReturn("{}");

            // Act
            var result = service.auditApiKeyUsed("key-001", "/api/v1/chat", "192.168.1.1", false);

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    !entity.getSuccess()
            ));
        }

        @Test
        @DisplayName("auditApiKeyRevoked - 成功记录撤销")
        void testAuditApiKeyRevoked_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());
            when(entityMapper.toJson(anyMap())).thenReturn("{\"reason\":\"security breach\"}");

            // Act
            var result = service.auditApiKeyRevoked("key-001", "security breach", "admin");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.API_KEY_REVOKED
            ));
        }

        @Test
        @DisplayName("auditApiKeyExpired - 成功记录过期")
        void testAuditApiKeyExpired_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());

            // Act
            var result = service.auditApiKeyExpired("key-001");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.API_KEY_EXPIRED
            ));
        }
    }

    // ========== 安全事件审计方法测试 ==========

    @Nested
    @DisplayName("安全事件审计方法测试")
    class SecurityEventAuditTests {

        @Test
        @DisplayName("auditSecurityEvent - 成功记录安全事件")
        void testAuditSecurityEvent_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());
            when(entityMapper.parseEventType(anyString())).thenReturn(AuditEventType.SECURITY_ALERT);
            when(entityMapper.determineRiskLevel(any(), anyBoolean())).thenReturn(RiskLevel.HIGH);

            // Act
            var result = service.auditSecurityEvent("SECURITY_ALERT", "Multiple failed logins", "user-001", "192.168.1.1");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    !entity.getSuccess()
            ));
        }

        @Test
        @DisplayName("auditSuspiciousActivity - 成功记录可疑活动")
        void testAuditSuspiciousActivity_Success() {
            // Arrange
            when(auditRepository.save(any(SecurityAuditEventEntity.class)))
                    .thenReturn(createMockEntity());
            when(entityMapper.toJson(anyMap())).thenReturn("{\"activity\":\"brute force\"}");

            // Act
            var result = service.auditSuspiciousActivity("brute force", "user-001", "192.168.1.1", "10 attempts in 1 minute");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.SUSPICIOUS_ACTIVITY &&
                    entity.getRiskLevel() == RiskLevel.HIGH
            ));
        }
    }

    // ========== 查询方法测试 ==========

    @Nested
    @DisplayName("查询方法测试")
    class QueryMethodTests {

        @Test
        @DisplayName("findAuditEvents - 成功查询")
        void testFindAuditEvents_Success() {
            // Arrange
            AuditEventQuery query = new AuditEventQuery();
            query.setPage(0);
            query.setSize(10);
            query.setSortBy("timestamp");
            query.setSortDirection("DESC");

            List<SecurityAuditEventEntity> entities = List.of(createMockEntity());
            Page<SecurityAuditEventEntity> page = new PageImpl<>(entities);
            
            when(auditRepository.findByConditions(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(page);
            when(entityMapper.entityToDto(any())).thenReturn(createMockAuditEvent());

            // Act
            var result = service.findAuditEvents(query);

            // Assert
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("countAuditEvents - 成功统计")
        void testCountAuditEvents_Success() {
            // Arrange
            AuditEventQuery query = new AuditEventQuery();
            when(auditRepository.countByConditions(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                    .thenReturn(100L);

            // Act
            var result = service.countAuditEvents(query);

            // Assert
            StepVerifier.create(result)
                    .expectNext(100L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("generateSecurityReport - 成功生成报告")
        void testGenerateSecurityReport_Success() {
            // Arrange
            LocalDateTime from = LocalDateTime.now().minusDays(1);
            LocalDateTime to = LocalDateTime.now();

            when(auditRepository.countByEventType(any(), any())).thenReturn(Arrays.asList(
                    new Object[]{AuditEventType.JWT_TOKEN_ISSUED, 50L},
                    new Object[]{AuditEventType.API_KEY_USED, 30L}
            ));
            when(auditRepository.countByUserId(any(), any(), anyInt())).thenReturn(
                    Collections.singletonList(new Object[]{"user-001", 20L})
            );
            when(auditRepository.countByClientIp(any(), any(), anyInt())).thenReturn(
                    Collections.singletonList(new Object[]{"192.168.1.1", 15L})
            );
            when(auditRepository.countFailedEventsInTimeWindow(any(), any(), any())).thenReturn(5L);
            when(auditRepository.countEventsInTimeWindow(any(AuditEventType.class), any(), any())).thenReturn(2L);
            when(auditRepository.findHighRiskEvents(any(), any())).thenReturn(Collections.emptyList());

            // Act
            var result = service.generateSecurityReport(from, to);

            // Assert
            StepVerifier.create(result)
                    .assertNext(report -> {
                        assertNotNull(report);
                        assertEquals(50L, report.getTotalJwtOperations());
                        assertEquals(30L, report.getTotalApiKeyOperations());
                        assertEquals(5L, report.getFailedAuthentications());
                        assertEquals(2L, report.getSuspiciousActivities());
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("getUserAuditEvents - 成功查询用户事件")
        void testGetUserAuditEvents_Success() {
            // Arrange
            SecurityAuditEventEntity entity = createMockEntity();
            entity.setUserId("user-001");
            
            when(auditRepository.findByTimeRange(any(), any())).thenReturn(List.of(entity));
            when(entityMapper.entityToDto(any())).thenReturn(createMockAuditEvent());

            // Act
            var result = service.getUserAuditEvents("user-001", LocalDateTime.now().minusDays(1), LocalDateTime.now(), 10);

            // Assert
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("getIpAuditEvents - 成功查询IP事件")
        void testGetIpAuditEvents_Success() {
            // Arrange
            SecurityAuditEventEntity entity = createMockEntity();
            entity.setClientIp("192.168.1.1");
            
            when(auditRepository.findByTimeRange(any(), any())).thenReturn(List.of(entity));
            when(entityMapper.entityToDto(any())).thenReturn(createMockAuditEvent());

            // Act
            var result = service.getIpAuditEvents("192.168.1.1", LocalDateTime.now().minusDays(1), LocalDateTime.now(), 10);

            // Assert
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }
    }

    // ========== SecurityAuditService方法测试 ==========

    @Nested
    @DisplayName("SecurityAuditService方法测试")
    class SecurityAuditServiceTests {

        @Test
        @DisplayName("recordEvent - 成功记录事件")
        void testRecordEvent_Success() {
            // Arrange
            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId("event-001")
                    .eventType("AUTHENTICATION_SUCCESS")
                    .userId("user-001")
                    .build();
            
            when(entityMapper.securityEventToEntity(any())).thenReturn(createMockEntity());
            when(auditRepository.save(any())).thenReturn(createMockEntity());

            // Act
            var result = service.recordEvent(event);

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(any());
        }

        @Test
        @DisplayName("recordAuthenticationEvent - 认证成功")
        void testRecordAuthenticationEvent_Success() {
            // Arrange
            when(auditRepository.save(any())).thenReturn(createMockEntity());

            // Act
            var result = service.recordAuthenticationEvent("user-001", "192.168.1.1", "Mozilla/5.0", true, null);

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.JWT_TOKEN_ISSUED &&
                    entity.getSuccess()
            ));
        }

        @Test
        @DisplayName("recordAuthenticationEvent - 认证失败")
        void testRecordAuthenticationEvent_Failure() {
            // Arrange
            when(auditRepository.save(any())).thenReturn(createMockEntity());

            // Act
            var result = service.recordAuthenticationEvent("user-001", "192.168.1.1", "Mozilla/5.0", false, "Invalid password");

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.AUTHENTICATION_FAILED &&
                    !entity.getSuccess() &&
                    entity.getRiskLevel() == RiskLevel.MEDIUM
            ));
        }

        @Test
        @DisplayName("recordSanitizationEvent - 成功记录脱敏事件")
        void testRecordSanitizationEvent_Success() {
            // Arrange
            when(auditRepository.save(any())).thenReturn(createMockEntity());
            when(entityMapper.toJson(anyMap())).thenReturn("{\"ruleId\":\"rule-001\"}");

            // Act
            var result = service.recordSanitizationEvent("user-001", "text/plain", "rule-001", 5);

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).save(argThat(entity ->
                    entity.getEventType() == AuditEventType.SYSTEM_MAINTENANCE &&
                    entity.getAction().equals("SANITIZE")
            ));
        }

        @Test
        @DisplayName("queryEvents - 成功查询事件")
        void testQueryEvents_Success() {
            // Arrange
            SecurityAuditEventEntity entity = createMockEntity();
            entity.setEventType(AuditEventType.JWT_TOKEN_ISSUED);
            
            when(auditRepository.findByTimeRange(any(), any())).thenReturn(List.of(entity));
            when(entityMapper.entityToSecurityEvent(any())).thenReturn(createMockSecurityAuditEvent());

            // Act
            var result = service.queryEvents(
                    LocalDateTime.now().minusDays(1),
                    LocalDateTime.now(),
                    "JWT_TOKEN_ISSUED",
                    null,
                    10
            );

            // Assert
            StepVerifier.create(result)
                    .expectNextCount(1)
                    .verifyComplete();
        }

        @Test
        @DisplayName("getSecurityStatistics - 成功获取统计")
        void testGetSecurityStatistics_Success() {
            // Arrange
            when(auditRepository.countByTimeRange(any(), any())).thenReturn(1000L);
            when(auditRepository.countByEventType(any(), any())).thenReturn(List.<Object[]>of(
                    new Object[]{AuditEventType.JWT_TOKEN_ISSUED, 500L}
            ));
            when(auditRepository.countSuccessEvents(any(), any())).thenReturn(800L);
            when(auditRepository.countFailedEvents(any(), any())).thenReturn(200L);
            when(auditRepository.countByEventCategory(any(), any())).thenReturn(List.<Object[]>of(
                    new Object[]{"JWT", 500L}
            ));

            // Act
            var result = service.getSecurityStatistics(LocalDateTime.now().minusDays(1), LocalDateTime.now());

            // Assert
            StepVerifier.create(result)
                    .assertNext(stats -> {
                        assertNotNull(stats);
                        assertEquals(1000L, stats.get("totalEvents"));
                        assertEquals(800L, stats.get("successCount"));
                        assertEquals(200L, stats.get("failureCount"));
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("cleanupExpiredLogs - 成功清理")
        void testCleanupExpiredLogs_Success() {
            // Arrange
            when(auditRepository.deleteLowRiskEventsBefore(any(), any())).thenReturn(100);

            // Act
            var result = service.cleanupExpiredLogs(90);

            // Assert
            StepVerifier.create(result)
                    .expectNext(100L)
                    .verifyComplete();
        }

        @Test
        @DisplayName("shouldTriggerAlert - 应触发告警")
        void testShouldTriggerAlert_ShouldTrigger() {
            // Arrange
            when(entityMapper.parseEventType(anyString())).thenReturn(AuditEventType.AUTHENTICATION_FAILED);
            when(auditRepository.countEventsInTimeWindow(any(), any(), any())).thenReturn(10L);

            // Act
            var result = service.shouldTriggerAlert("AUTHENTICATION_FAILED", 5, 5);

            // Assert
            StepVerifier.create(result)
                    .expectNext(true)
                    .verifyComplete();
        }

        @Test
        @DisplayName("shouldTriggerAlert - 不应触发告警")
        void testShouldTriggerAlert_ShouldNotTrigger() {
            // Arrange
            when(entityMapper.parseEventType(anyString())).thenReturn(AuditEventType.AUTHENTICATION_FAILED);
            when(auditRepository.countEventsInTimeWindow(any(), any(), any())).thenReturn(2L);

            // Act
            var result = service.shouldTriggerAlert("AUTHENTICATION_FAILED", 5, 5);

            // Assert
            StepVerifier.create(result)
                    .expectNext(false)
                    .verifyComplete();
        }
    }

    // ========== 批量操作测试 ==========

    @Nested
    @DisplayName("批量操作测试")
    class BatchOperationTests {

        @Test
        @DisplayName("recordAuditEvent - 成功记录单个审计事件")
        void testRecordAuditEvent_Success() {
            // Arrange
            AuditEvent event = createMockAuditEvent();
            when(entityMapper.dtoToEntity(any())).thenReturn(createMockEntity());
            when(auditRepository.save(any())).thenReturn(createMockEntity());

            // Act
            var result = service.recordAuditEvent(event);

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
        }

        @Test
        @DisplayName("batchRecordAuditEvents - 成功批量记录")
        void testBatchRecordAuditEvents_Success() {
            // Arrange
            List<AuditEvent> events = List.of(createMockAuditEvent(), createMockAuditEvent());
            when(entityMapper.dtoToEntity(any())).thenReturn(createMockEntity());
            when(auditRepository.saveAll(any())).thenReturn(List.of(createMockEntity(), createMockEntity()));

            // Act
            var result = service.batchRecordAuditEvents(events);

            // Assert
            StepVerifier.create(result)
                    .verifyComplete();
            verify(auditRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
        }
    }

    // ========== 辅助方法 ==========

    private SecurityAuditEventEntity createMockEntity() {
        return SecurityAuditEventEntity.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(AuditEventType.JWT_TOKEN_ISSUED)
                .userId("user-001")
                .clientIp("192.168.1.1")
                .action("ISSUE")
                .details("Test event")
                .success(true)
                .timestamp(LocalDateTime.now())
                .riskLevel(RiskLevel.LOW)
                .build();
    }

    private AuditEvent createMockAuditEvent() {
        return new AuditEvent(
                "event-001",
                AuditEventType.JWT_TOKEN_ISSUED,
                "user-001",
                "resource-001",
                "ISSUE",
                "Test event",
                "192.168.1.1",
                "Mozilla/5.0",
                true,
                LocalDateTime.now(),
                null
        );
    }

    private SecurityAuditEvent createMockSecurityAuditEvent() {
        return SecurityAuditEvent.builder()
                .eventId("event-001")
                .eventType("JWT_TOKEN_ISSUED")
                .userId("user-001")
                .clientIp("192.168.1.1")
                .action("ISSUE")
                .success(true)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
