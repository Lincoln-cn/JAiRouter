package org.unreal.modelrouter.persistence.jpa.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistStatus;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.BlacklistSource;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityBlacklistEntity.RiskLevel;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SecurityBlacklistEntity 单元测试
 */
@DisplayName("SecurityBlacklistEntity 测试")
class SecurityBlacklistEntityTest {

    @Test
    @DisplayName("测试默认构造函数")
    void testDefaultConstructor() {
        SecurityBlacklistEntity entity = new SecurityBlacklistEntity();

        assertNull(entity.getId());
        assertNull(entity.getBlacklistType());
        assertNull(entity.getTargetValue());
        assertNull(entity.getReason());
    }

    @Test
    @DisplayName("测试Builder模式")
    void testBuilder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusHours(24);

        SecurityBlacklistEntity entity = SecurityBlacklistEntity.builder()
                .id(1L)
                .blacklistType(BlacklistType.TOKEN)
                .targetValue("abc123def456")
                .targetHash("sha256-hash-value")
                .userId("user-001")
                .reason("Token revoked by user")
                .riskLevel(RiskLevel.MEDIUM)
                .addedBy("admin")
                .addedAt(now)
                .expiresAt(expiry)
                .status(BlacklistStatus.ACTIVE)
                .source(BlacklistSource.MANUAL)
                .build();

        assertEquals(1L, entity.getId());
        assertEquals(BlacklistType.TOKEN, entity.getBlacklistType());
        assertEquals("abc123def456", entity.getTargetValue());
        assertEquals("sha256-hash-value", entity.getTargetHash());
        assertEquals("user-001", entity.getUserId());
        assertEquals("Token revoked by user", entity.getReason());
        assertEquals(RiskLevel.MEDIUM, entity.getRiskLevel());
        assertEquals("admin", entity.getAddedBy());
        assertEquals(now, entity.getAddedAt());
        assertEquals(expiry, entity.getExpiresAt());
        assertEquals(BlacklistStatus.ACTIVE, entity.getStatus());
        assertEquals(BlacklistSource.MANUAL, entity.getSource());
    }

    @Test
    @DisplayName("测试不同黑名单类型")
    void testDifferentBlacklistTypes() {
        SecurityBlacklistEntity tokenBlacklist = SecurityBlacklistEntity.builder()
                .blacklistType(BlacklistType.TOKEN)
                .targetValue("jwt-token-hash")
                .status(BlacklistStatus.ACTIVE)
                .build();

        SecurityBlacklistEntity ipBlacklist = SecurityBlacklistEntity.builder()
                .blacklistType(BlacklistType.IP)
                .targetValue("192.168.1.100")
                .status(BlacklistStatus.ACTIVE)
                .build();

        SecurityBlacklistEntity deviceBlacklist = SecurityBlacklistEntity.builder()
                .blacklistType(BlacklistType.DEVICE)
                .targetValue("device-fingerprint-123")
                .status(BlacklistStatus.ACTIVE)
                .build();

        assertEquals(BlacklistType.TOKEN, tokenBlacklist.getBlacklistType());
        assertEquals(BlacklistType.IP, ipBlacklist.getBlacklistType());
        assertEquals(BlacklistType.DEVICE, deviceBlacklist.getBlacklistType());
    }

    @Test
    @DisplayName("测试过期时间")
    void testExpiryTime() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiry = now.plusHours(1);

        SecurityBlacklistEntity entity = SecurityBlacklistEntity.builder()
                .addedAt(now)
                .expiresAt(expiry)
                .status(BlacklistStatus.ACTIVE)
                .build();

        assertTrue(entity.getExpiresAt().isAfter(entity.getAddedAt()));
    }

    @Test
    @DisplayName("测试isExpired方法 - 未过期")
    void testIsExpiredNotExpired() {
        LocalDateTime future = LocalDateTime.now().plusHours(1);
        SecurityBlacklistEntity entity = SecurityBlacklistEntity.builder()
                .expiresAt(future)
                .build();

        assertFalse(entity.isExpired());
    }

    @Test
    @DisplayName("测试isExpired方法 - 已过期")
    void testIsExpiredAlreadyExpired() {
        LocalDateTime past = LocalDateTime.now().minusHours(1);
        SecurityBlacklistEntity entity = SecurityBlacklistEntity.builder()
                .expiresAt(past)
                .build();

        assertTrue(entity.isExpired());
    }

    @Test
    @DisplayName("测试isExpired方法 - 永久有效")
    void testIsExpiredPermanent() {
        SecurityBlacklistEntity entity = SecurityBlacklistEntity.builder()
                .expiresAt(null)
                .build();

        assertFalse(entity.isExpired());
    }

    @Test
    @DisplayName("测试isActive方法")
    void testIsActive() {
        // 活跃状态
        SecurityBlacklistEntity activeEntity = SecurityBlacklistEntity.builder()
                .status(BlacklistStatus.ACTIVE)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        // 已过期状态
        SecurityBlacklistEntity expiredEntity = SecurityBlacklistEntity.builder()
                .status(BlacklistStatus.EXPIRED)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();

        // 已移除状态
        SecurityBlacklistEntity removedEntity = SecurityBlacklistEntity.builder()
                .status(BlacklistStatus.REMOVED)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();

        assertTrue(activeEntity.isActive());
        assertFalse(expiredEntity.isActive());
        assertFalse(removedEntity.isActive());
    }

    @Test
    @DisplayName("测试不同来源")
    void testDifferentSources() {
        SecurityBlacklistEntity manualEntity = SecurityBlacklistEntity.builder()
                .source(BlacklistSource.MANUAL)
                .build();

        SecurityBlacklistEntity autoEntity = SecurityBlacklistEntity.builder()
                .source(BlacklistSource.AUTO)
                .build();

        SecurityBlacklistEntity systemEntity = SecurityBlacklistEntity.builder()
                .source(BlacklistSource.SYSTEM)
                .build();

        assertEquals(BlacklistSource.MANUAL, manualEntity.getSource());
        assertEquals(BlacklistSource.AUTO, autoEntity.getSource());
        assertEquals(BlacklistSource.SYSTEM, systemEntity.getSource());
    }
}
