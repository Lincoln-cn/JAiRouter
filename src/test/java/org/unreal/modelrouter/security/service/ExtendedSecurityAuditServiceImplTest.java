package org.unreal.modelrouter.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.dto.AuditEvent;
import org.unreal.modelrouter.dto.AuditEventQuery;
import org.unreal.modelrouter.dto.AuditEventType;
import org.unreal.modelrouter.dto.SecurityReport;
import org.unreal.modelrouter.security.audit.ExtendedSecurityAuditServiceImpl;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ExtendedSecurityAuditServiceImpl 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ExtendedSecurityAuditServiceImplTest {
    
    private ExtendedSecurityAuditServiceImpl auditService;
    
    @BeforeEach
    void setUp() {
        auditService = new ExtendedSecurityAuditServiceImpl();
    }
    
    @Test
    void testAuditTokenIssued() {
        // Given
        String userId = "user123";
        String tokenId = "token456";
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";
        
        // When & Then
        StepVerifier.create(auditService.auditTokenIssued(userId, tokenId, ipAddress, userAgent))
                .verifyComplete();
        
        // Verify the event was recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setUserId(userId);
        query.setEventTypes(List.of(AuditEventType.JWT_TOKEN_ISSUED));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextMatches(event -> 
                    event.getUserId().equals(userId) &&
                    event.getResourceId().equals(tokenId) &&
                    event.getIpAddress().equals(ipAddress) &&
                    event.getType() == AuditEventType.JWT_TOKEN_ISSUED &&
                    event.isSuccess()
                )
                .verifyComplete();
    }
    
    @Test
    void testAuditTokenRefreshed() {
        // Given
        String userId = "user123";
        String oldTokenId = "oldToken456";
        String newTokenId = "newToken789";
        String ipAddress = "192.168.1.1";
        
        // When & Then
        StepVerifier.create(auditService.auditTokenRefreshed(userId, oldTokenId, newTokenId, ipAddress))
                .verifyComplete();
        
        // Verify the event was recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setUserId(userId);
        query.setEventTypes(List.of(AuditEventType.JWT_TOKEN_REFRESHED));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextMatches(event -> 
                    event.getUserId().equals(userId) &&
                    event.getResourceId().equals(newTokenId) &&
                    event.getIpAddress().equals(ipAddress) &&
                    event.getType() == AuditEventType.JWT_TOKEN_REFRESHED &&
                    event.isSuccess() &&
                    event.getMetadata().containsKey("oldTokenId") &&
                    event.getMetadata().get("oldTokenId").equals(oldTokenId)
                )
                .verifyComplete();
    }
    
    @Test
    void testAuditTokenRevoked() {
        // Given
        String userId = "user123";
        String tokenId = "token456";
        String reason = "Security breach";
        String revokedBy = "admin";
        
        // When & Then
        StepVerifier.create(auditService.auditTokenRevoked(userId, tokenId, reason, revokedBy))
                .verifyComplete();
        
        // Verify the event was recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setUserId(userId);
        query.setEventTypes(List.of(AuditEventType.JWT_TOKEN_REVOKED));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextMatches(event -> 
                    event.getUserId().equals(userId) &&
                    event.getResourceId().equals(tokenId) &&
                    event.getType() == AuditEventType.JWT_TOKEN_REVOKED &&
                    event.isSuccess() &&
                    event.getMetadata().containsKey("revokeReason") &&
                    event.getMetadata().get("revokeReason").equals(reason) &&
                    event.getMetadata().get("revokedBy").equals(revokedBy)
                )
                .verifyComplete();
    }
    
    @Test
    void testAuditTokenValidated() {
        // Given
        String userId = "user123";
        String tokenId = "token456";
        boolean isValid = true;
        String ipAddress = "192.168.1.1";
        
        // When & Then
        StepVerifier.create(auditService.auditTokenValidated(userId, tokenId, isValid, ipAddress))
                .verifyComplete();
        
        // Verify the event was recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setUserId(userId);
        query.setEventTypes(List.of(AuditEventType.JWT_TOKEN_VALIDATED));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextMatches(event -> 
                    event.getUserId().equals(userId) &&
                    event.getResourceId().equals(tokenId) &&
                    event.getIpAddress().equals(ipAddress) &&
                    event.getType() == AuditEventType.JWT_TOKEN_VALIDATED &&
                    event.isSuccess() == isValid
                )
                .verifyComplete();
    }
    
    @Test
    void testAuditApiKeyCreated() {
        // Given
        String keyId = "key123";
        String createdBy = "admin";
        String ipAddress = "192.168.1.1";
        
        // When & Then
        StepVerifier.create(auditService.auditApiKeyCreated(keyId, createdBy, ipAddress))
                .verifyComplete();
        
        // Verify the event was recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setUserId(createdBy);
        query.setEventTypes(List.of(AuditEventType.API_KEY_CREATED));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextMatches(event -> 
                    event.getUserId().equals(createdBy) &&
                    event.getResourceId().equals(keyId) &&
                    event.getIpAddress().equals(ipAddress) &&
                    event.getType() == AuditEventType.API_KEY_CREATED &&
                    event.isSuccess()
                )
                .verifyComplete();
    }
    
    @Test
    void testAuditApiKeyUsed() {
        // Given
        String keyId = "key123";
        String endpoint = "/api/test";
        String ipAddress = "192.168.1.1";
        boolean success = true;
        
        // When & Then
        StepVerifier.create(auditService.auditApiKeyUsed(keyId, endpoint, ipAddress, success))
                .verifyComplete();
        
        // Verify the event was recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setResourceId(keyId);
        query.setEventTypes(List.of(AuditEventType.API_KEY_USED));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextMatches(event -> 
                    event.getResourceId().equals(keyId) &&
                    event.getIpAddress().equals(ipAddress) &&
                    event.getType() == AuditEventType.API_KEY_USED &&
                    event.isSuccess() == success &&
                    event.getMetadata().containsKey("endpoint") &&
                    event.getMetadata().get("endpoint").equals(endpoint)
                )
                .verifyComplete();
    }
    
    @Test
    void testAuditApiKeyRevoked() {
        // Given
        String keyId = "key123";
        String reason = "Compromised";
        String revokedBy = "admin";
        
        // When & Then
        StepVerifier.create(auditService.auditApiKeyRevoked(keyId, reason, revokedBy))
                .verifyComplete();
        
        // Verify the event was recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setUserId(revokedBy);
        query.setEventTypes(List.of(AuditEventType.API_KEY_REVOKED));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextMatches(event -> 
                    event.getUserId().equals(revokedBy) &&
                    event.getResourceId().equals(keyId) &&
                    event.getType() == AuditEventType.API_KEY_REVOKED &&
                    event.isSuccess() &&
                    event.getMetadata().containsKey("revokeReason") &&
                    event.getMetadata().get("revokeReason").equals(reason)
                )
                .verifyComplete();
    }
    
    @Test
    void testAuditSecurityEvent() {
        // Given
        String eventType = "BRUTE_FORCE_ATTACK";
        String details = "Multiple failed login attempts detected";
        String userId = "user123";
        String ipAddress = "192.168.1.1";
        
        // When & Then
        StepVerifier.create(auditService.auditSecurityEvent(eventType, details, userId, ipAddress))
                .verifyComplete();
        
        // Verify the event was recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setUserId(userId);
        query.setEventTypes(List.of(AuditEventType.SECURITY_ALERT));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextMatches(event -> 
                    event.getUserId().equals(userId) &&
                    event.getIpAddress().equals(ipAddress) &&
                    event.getType() == AuditEventType.SECURITY_ALERT &&
                    event.getDetails().equals(details) &&
                    event.getMetadata().containsKey("securityEventType") &&
                    event.getMetadata().get("securityEventType").equals(eventType)
                )
                .verifyComplete();
    }
    
    @Test
    void testAuditSuspiciousActivity() {
        // Given
        String activity = "Unusual access pattern";
        String userId = "user123";
        String ipAddress = "192.168.1.1";
        String details = "Access from multiple countries within 1 hour";
        
        // When & Then
        StepVerifier.create(auditService.auditSuspiciousActivity(activity, userId, ipAddress, details))
                .verifyComplete();
        
        // Verify the event was recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setUserId(userId);
        query.setEventTypes(List.of(AuditEventType.SUSPICIOUS_ACTIVITY));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextMatches(event -> 
                    event.getUserId().equals(userId) &&
                    event.getIpAddress().equals(ipAddress) &&
                    event.getType() == AuditEventType.SUSPICIOUS_ACTIVITY &&
                    event.getDetails().equals(details) &&
                    !event.isSuccess() && // 可疑活动标记为不成功
                    event.getMetadata().containsKey("activityType") &&
                    event.getMetadata().get("activityType").equals(activity)
                )
                .verifyComplete();
    }
    
    @Test
    void testGenerateSecurityReport() {
        // Given - 先记录一些审计事件
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now();
        
        Mono<Void> setupEvents = auditService.auditTokenIssued("user1", "token1", "192.168.1.1", "Mozilla")
                .then(auditService.auditTokenRefreshed("user1", "token1", "token2", "192.168.1.1"))
                .then(auditService.auditApiKeyCreated("key1", "admin", "192.168.1.2"))
                .then(auditService.auditApiKeyUsed("key1", "/api/test", "192.168.1.3", true))
                .then(auditService.auditSuspiciousActivity("Multiple logins", "user2", "192.168.1.4", "Details"));
        
        // When & Then
        StepVerifier.create(
                setupEvents.then(auditService.generateSecurityReport(startTime, endTime))
        )
        .expectNextMatches(report -> 
            report.getTotalJwtOperations() >= 2 &&
            report.getTotalApiKeyOperations() >= 2 &&
            report.getSuspiciousActivities() >= 1 &&
            report.getOperationsByType().size() > 0 &&
            report.getOperationsByUser().size() > 0
        )
        .verifyComplete();
    }
    
    @Test
    void testFindAuditEventsWithQuery() {
        // Given - 先记录一些审计事件
        String userId = "testUser";
        String ipAddress = "192.168.1.100";
        
        Mono<Void> setupEvents = auditService.auditTokenIssued(userId, "token1", ipAddress, "Mozilla")
                .then(auditService.auditTokenValidated(userId, "token1", true, ipAddress))
                .then(auditService.auditApiKeyCreated("key1", userId, ipAddress));
        
        // When
        AuditEventQuery query = new AuditEventQuery();
        query.setUserId(userId);
        query.setIpAddress(ipAddress);
        query.setStartTime(LocalDateTime.now().minusMinutes(5));
        query.setEndTime(LocalDateTime.now().plusMinutes(5));
        query.setSize(10);
        
        // Then
        StepVerifier.create(
                setupEvents.thenMany(auditService.findAuditEvents(query))
        )
        .expectNextCount(3) // 应该有3个事件
        .verifyComplete();
    }
    
    @Test
    void testGetUserAuditEvents() {
        // Given
        String userId = "testUser";
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);
        
        Mono<Void> setupEvents = auditService.auditTokenIssued(userId, "token1", "192.168.1.1", "Mozilla")
                .then(auditService.auditTokenValidated(userId, "token1", true, "192.168.1.1"));
        
        // When & Then
        StepVerifier.create(
                setupEvents.thenMany(auditService.getUserAuditEvents(userId, startTime, endTime, 10))
        )
        .expectNextCount(2) // 应该有2个事件
        .verifyComplete();
    }
    
    @Test
    void testGetIpAuditEvents() {
        // Given
        String ipAddress = "192.168.1.200";
        LocalDateTime startTime = LocalDateTime.now().minusHours(1);
        LocalDateTime endTime = LocalDateTime.now().plusHours(1);
        
        Mono<Void> setupEvents = auditService.auditTokenIssued("user1", "token1", ipAddress, "Mozilla")
                .then(auditService.auditApiKeyUsed("key1", "/api/test", ipAddress, true));
        
        // When & Then
        StepVerifier.create(
                setupEvents.thenMany(auditService.getIpAuditEvents(ipAddress, startTime, endTime, 10))
        )
        .expectNextCount(2) // 应该有2个事件
        .verifyComplete();
    }
    
    @Test
    void testBatchRecordAuditEvents() {
        // Given
        AuditEvent event1 = new AuditEvent();
        event1.setType(AuditEventType.JWT_TOKEN_ISSUED);
        event1.setUserId("user1");
        event1.setResourceId("token1");
        event1.setAction("TOKEN_ISSUED");
        event1.setSuccess(true);
        
        AuditEvent event2 = new AuditEvent();
        event2.setType(AuditEventType.API_KEY_CREATED);
        event2.setUserId("admin");
        event2.setResourceId("key1");
        event2.setAction("API_KEY_CREATED");
        event2.setSuccess(true);
        
        List<AuditEvent> events = List.of(event1, event2);
        
        // When & Then
        StepVerifier.create(auditService.batchRecordAuditEvents(events))
                .verifyComplete();
        
        // Verify events were recorded
        AuditEventQuery query = new AuditEventQuery();
        query.setStartTime(LocalDateTime.now().minusMinutes(1));
        query.setEndTime(LocalDateTime.now().plusMinutes(1));
        
        StepVerifier.create(auditService.findAuditEvents(query))
                .expectNextCount(2) // 应该有2个事件
                .verifyComplete();
    }
}