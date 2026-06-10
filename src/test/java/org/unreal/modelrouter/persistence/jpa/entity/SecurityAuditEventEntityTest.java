package org.unreal.modelrouter.persistence.jpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity.RiskLevel;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityAuditEventEntity 单元测试
 */
@DisplayName("SecurityAuditEventEntity 测试")
class SecurityAuditEventEntityTest {

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() {
        SecurityAuditEventEntity entity = new SecurityAuditEventEntity();

        assertNull(entity.getId());
        assertNull(entity.getEventId());
        assertNull(entity.getEventType());
        assertNull(entity.getUserId());
        assertNull(entity.getClientIp());
    }

    @Test
    @DisplayName("测试Builder模式")
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();
        SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                .id(1L)
                .eventId("evt-123")
                .eventType(AuditEventType.JWT_TOKEN_ISSUED)
                .eventCategory("JWT_TOKEN")
                .userId("user-001")
                .resourceId("token-abc")
                .clientIp("192.168.1.100")
                .userAgent("Mozilla/5.0")
                .timestamp(now)
                .resource("/api/config")
                .action("READ")
                .details("Token issued successfully")
                .success(true)
                .requestId("req-456")
                .sessionId("session-789")
                .riskLevel(RiskLevel.LOW)
                .build();

        assertEquals(1L, entity.getId());
        assertEquals("evt-123", entity.getEventId());
        assertEquals(AuditEventType.JWT_TOKEN_ISSUED, entity.getEventType());
        assertEquals("JWT_TOKEN", entity.getEventCategory());
        assertEquals("user-001", entity.getUserId());
        assertEquals("token-abc", entity.getResourceId());
        assertEquals("192.168.1.100", entity.getClientIp());
        assertEquals("Mozilla/5.0", entity.getUserAgent());
        assertEquals(now, entity.getTimestamp());
        assertEquals("/api/config", entity.getResource());
        assertEquals("READ", entity.getAction());
        assertEquals("Token issued successfully", entity.getDetails());
        assertTrue(entity.getSuccess());
        assertEquals("req-456", entity.getRequestId());
        assertEquals("session-789", entity.getSessionId());
        assertEquals(RiskLevel.LOW, entity.getRiskLevel());
    }

    @Test
    @DisplayName("测试不同事件类型")
    void testDifferentEventTypes() {
        SecurityAuditEventEntity loginEvent = SecurityAuditEventEntity.builder()
                .eventType(AuditEventType.JWT_TOKEN_ISSUED)
                .userId("user1")
                .success(true)
                .build();

        SecurityAuditEventEntity revokedEvent = SecurityAuditEventEntity.builder()
                .eventType(AuditEventType.JWT_TOKEN_REVOKED)
                .userId("user1")
                .success(true)
                .build();

        SecurityAuditEventEntity authFailureEvent = SecurityAuditEventEntity.builder()
                .eventType(AuditEventType.AUTHENTICATION_FAILED)
                .userId("attacker")
                .clientIp("10.0.0.1")
                .success(false)
                .failureReason("Invalid credentials")
                .build();

        assertEquals(AuditEventType.JWT_TOKEN_ISSUED, loginEvent.getEventType());
        assertEquals(AuditEventType.JWT_TOKEN_REVOKED, revokedEvent.getEventType());
        assertEquals(AuditEventType.AUTHENTICATION_FAILED, authFailureEvent.getEventType());
        assertFalse(authFailureEvent.getSuccess());
        assertEquals("Invalid credentials", authFailureEvent.getFailureReason());
    }

    @Test
    @DisplayName("测试风险等级")
    void testRiskLevels() {
        SecurityAuditEventEntity lowRiskEvent = SecurityAuditEventEntity.builder()
                .eventType(AuditEventType.JWT_TOKEN_VALIDATED)
                .success(true)
                .riskLevel(RiskLevel.LOW)
                .build();

        SecurityAuditEventEntity highRiskEvent = SecurityAuditEventEntity.builder()
                .eventType(AuditEventType.SUSPICIOUS_ACTIVITY)
                .success(false)
                .riskLevel(RiskLevel.HIGH)
                .build();

        SecurityAuditEventEntity criticalEvent = SecurityAuditEventEntity.builder()
                .eventType(AuditEventType.SECURITY_ALERT)
                .success(false)
                .riskLevel(RiskLevel.CRITICAL)
                .build();

        assertEquals(RiskLevel.LOW, lowRiskEvent.getRiskLevel());
        assertEquals(RiskLevel.HIGH, highRiskEvent.getRiskLevel());
        assertEquals(RiskLevel.CRITICAL, criticalEvent.getRiskLevel());
    }

    @Test
    @DisplayName("测试API Key事件")
    void testApiKeyEvents() {
        SecurityAuditEventEntity apiKeyCreated = SecurityAuditEventEntity.builder()
                .eventType(AuditEventType.API_KEY_CREATED)
                .userId("admin")
                .resourceId("key-123")
                .success(true)
                .build();

        SecurityAuditEventEntity apiKeyRevoked = SecurityAuditEventEntity.builder()
                .eventType(AuditEventType.API_KEY_REVOKED)
                .userId("admin")
                .resourceId("key-123")
                .success(true)
                .build();

        assertEquals(AuditEventType.API_KEY_CREATED, apiKeyCreated.getEventType());
        assertEquals(AuditEventType.API_KEY_REVOKED, apiKeyRevoked.getEventType());
    }
}
