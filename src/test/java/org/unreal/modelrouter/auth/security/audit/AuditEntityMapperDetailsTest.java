package org.unreal.modelrouter.auth.security.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 审计事件的 {@code details} 列必须在 recordEvent 通道上往返（issue #1186）。
 *
 * <p>缺陷形态：实体与 DTO 都有 {@code details}，但事件对象没有这个字段、映射器也不搬运它，
 * 于是经 {@code ExtendedSecurityAuditServiceImpl.persist()}（唯一落库漏斗，见该类的
 * {@code entityMapper.securityEventToEntity(event)} 调用）写入的行，details 永远是 NULL。
 * 这类缺陷不会让任何用例变红 —— 它只是让审计页少一列内容，所以需要专门的往返断言守着。</p>
 */
@DisplayName("审计事件 details 往返（#1186）")
class AuditEntityMapperDetailsTest {

    private final AuditEntityMapper mapper = new AuditEntityMapper(new ObjectMapper());

    @Test
    @DisplayName("事件 → 实体：details 必须落到 details 列")
    void securityEventToEntityKeepsDetails() {
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType(AuditEventType.SLA_BOOTSTRAP_ISSUED.name())
                .userId("admin")
                .resource("/api/security/sla/accounts/1/tokens")
                .action("ISSUE_BOOTSTRAP")
                .success(true)
                .details("角色 AUDITOR")
                .build();

        SecurityAuditEventEntity entity = mapper.securityEventToEntity(event);

        assertEquals("角色 AUDITOR", entity.getDetails(),
                "details 未映射会让 recordEvent 落库的行该列恒为空，审计页永远看不到这段说明");
    }

    @Test
    @DisplayName("实体 → 事件：details 必须能读回")
    void entityToSecurityEventKeepsDetails() {
        SecurityAuditEventEntity entity = SecurityAuditEventEntity.builder()
                .eventId("evt-1")
                .eventType(AuditEventType.SECURITY_ALERT)
                .userId("admin")
                .resource("/api/security/audit")
                .action("EXPORT")
                .success(true)
                .details("导出 500 行")
                .build();

        assertEquals("导出 500 行", mapper.entityToSecurityEvent(entity).getDetails());
    }

    @Test
    @DisplayName("details 与 additionalData 互不干扰：前者纯文本，后者进 metadata JSON")
    void detailsAndAdditionalDataStaySeparate() {
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType(AuditEventType.SECURITY_ALERT.name())
                .success(true)
                .details("运维手工触发")
                .additionalData(Map.of("rows", 12, "scope", "all"))
                .build();

        SecurityAuditEventEntity entity = mapper.securityEventToEntity(event);

        assertEquals("运维手工触发", entity.getDetails());
        assertFalse(entity.getMetadata() == null || entity.getMetadata().isEmpty(),
                "结构化数据应进 metadata 列");
        assertEquals("all", mapper.entityToSecurityEvent(entity).getAdditionalData().get("scope"));
    }

    @Test
    @DisplayName("未设置 details 时保持为空，不写空串")
    void nullDetailsStaysNull() {
        SecurityAuditEvent event = SecurityAuditEvent.builder()
                .eventType(AuditEventType.SYSTEM_MAINTENANCE.name())
                .success(true)
                .build();

        assertNull(mapper.securityEventToEntity(event).getDetails());
    }
}
