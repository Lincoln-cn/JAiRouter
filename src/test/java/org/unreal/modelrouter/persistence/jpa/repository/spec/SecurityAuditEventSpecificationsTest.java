package org.unreal.modelrouter.persistence.jpa.repository.spec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.unreal.modelrouter.ModelRouterApplication;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.repository.SecurityAuditEventRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 审计事件动态条件的真实数据库行为（#1134）。
 *
 * <p>这里验证的是「条件为 null 时不产生限制」「集合为空时不产生限制」等语义，
 * 与原 JPQL 的 {@code (:param IS NULL OR ...)} 行为等价。</p>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@ContextConfiguration(classes = ModelRouterApplication.class)
@DisplayName("审计事件动态条件（#1134）")
class SecurityAuditEventSpecificationsTest {

    @Autowired
    private SecurityAuditEventRepository repository;

    private final LocalDateTime base = LocalDateTime.of(2026, 5, 1, 10, 0);

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.saveAll(List.of(
                event("e1", AuditEventType.API_KEY_CREATED, "u1", true, base),
                event("e2", AuditEventType.SYSTEM_MAINTENANCE, "u2", false, base.plusHours(1)),
                event("e3", AuditEventType.API_KEY_CREATED, "u2", true, base.plusHours(2))));
    }

    @Test
    @DisplayName("全部条件为 null 时不加限制")
    void allNullMeansNoFilter() {
        long count = repository.count(SecurityAuditEventSpecifications.of(
                null, null, null, null, null, null, null, null, null));

        assertEquals(3, count);
    }

    @Test
    @DisplayName("按用户过滤，其余条件为 null")
    void filtersByUserOnly() {
        long count = repository.count(SecurityAuditEventSpecifications.of(
                null, null, null, "u2", null, null, null, null, null));

        assertEquals(2, count);
    }

    @Test
    @DisplayName("空事件类型集合不产生 IN 限制")
    void emptyEventTypesMeansNoFilter() {
        long count = repository.count(SecurityAuditEventSpecifications.of(
                null, null, List.of(), null, null, null, null, null, null));

        assertEquals(3, count);
    }

    @Test
    @DisplayName("时间范围与成功标志组合生效")
    void combinesTimeRangeAndSuccess() {
        long count = repository.count(SecurityAuditEventSpecifications.of(
                base.plusMinutes(30), base.plusHours(3), null, null, null, null, true, null, null));

        assertEquals(1, count);
    }

    private static SecurityAuditEventEntity event(
            final String eventId,
            final AuditEventType type,
            final String userId,
            final boolean success,
            final LocalDateTime timestamp) {
        return SecurityAuditEventEntity.builder()
                .eventId(eventId)
                .eventType(type)
                .eventCategory("TEST")
                .userId(userId)
                .clientIp("10.0.0.1")
                .timestamp(timestamp)
                .success(success)
                .build();
    }
}
