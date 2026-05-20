package org.unreal.modelrouter.auth.security.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.common.dto.AuditEvent;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity;
import org.unreal.modelrouter.persistence.jpa.entity.SecurityAuditEventEntity.RiskLevel;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 审计实体转换器
 * 负责Entity和DTO之间的转换
 *
 * @since v2.7.19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEntityMapper {

    private final ObjectMapper objectMapper;

    /**
     * Entity转AuditEvent DTO
     */
    public AuditEvent entityToDto(final SecurityAuditEventEntity entity) {
        AuditEvent dto = new AuditEvent();
        dto.setId(entity.getEventId());
        dto.setType(entity.getEventType());
        dto.setUserId(entity.getUserId());
        dto.setResourceId(entity.getResourceId());
        dto.setAction(entity.getAction());
        dto.setDetails(entity.getDetails());
        dto.setIpAddress(entity.getClientIp());
        dto.setUserAgent(entity.getUserAgent());
        dto.setSuccess(entity.getSuccess());
        dto.setTimestamp(entity.getTimestamp());
        dto.setMetadata(parseJson(entity.getMetadata()));
        return dto;
    }

    /**
     * AuditEvent DTO转Entity
     */
    public SecurityAuditEventEntity dtoToEntity(final AuditEvent dto) {
        return SecurityAuditEventEntity.builder()
                .eventId(dto.getId() != null ? dto.getId() : UUID.randomUUID().toString())
                .eventType(dto.getType())
                .userId(dto.getUserId())
                .resourceId(dto.getResourceId())
                .clientIp(dto.getIpAddress())
                .userAgent(dto.getUserAgent())
                .action(dto.getAction())
                .details(dto.getDetails())
                .success(dto.isSuccess())
                .metadata(toJson(dto.getMetadata()))
                .timestamp(dto.getTimestamp() != null ? dto.getTimestamp() : LocalDateTime.now())
                .build();
    }

    /**
     * Entity转SecurityAuditEvent
     */
    public SecurityAuditEvent entityToSecurityEvent(final SecurityAuditEventEntity entity) {
        return SecurityAuditEvent.builder()
                .eventId(entity.getEventId())
                .eventType(entity.getEventType().name())
                .userId(entity.getUserId())
                .clientIp(entity.getClientIp())
                .userAgent(entity.getUserAgent())
                .timestamp(entity.getTimestamp())
                .resource(entity.getResource())
                .action(entity.getAction())
                .success(entity.getSuccess())
                .failureReason(entity.getFailureReason())
                .additionalData(parseJson(entity.getMetadata()))
                .requestId(entity.getRequestId())
                .sessionId(entity.getSessionId())
                .build();
    }

    /**
     * SecurityAuditEvent转Entity
     */
    public SecurityAuditEventEntity securityEventToEntity(final SecurityAuditEvent event) {
        return SecurityAuditEventEntity.builder()
                .eventId(event.getEventId() != null ? event.getEventId() : UUID.randomUUID().toString())
                .eventType(parseEventType(event.getEventType()))
                .userId(event.getUserId())
                .clientIp(event.getClientIp())
                .userAgent(event.getUserAgent())
                .timestamp(event.getTimestamp() != null ? event.getTimestamp() : LocalDateTime.now())
                .resource(event.getResource())
                .action(event.getAction())
                .success(event.isSuccess())
                .failureReason(event.getFailureReason())
                .metadata(toJson(event.getAdditionalData()))
                .requestId(event.getRequestId())
                .sessionId(event.getSessionId())
                .build();
    }

    /**
     * 解析事件类型
     */
    public AuditEventType parseEventType(final String eventType) {
        try {
            return AuditEventType.valueOf(eventType);
        } catch (IllegalArgumentException e) {
            log.warn("无法解析事件类型: {}, 使用默认值", eventType);
            return AuditEventType.SYSTEM_MAINTENANCE;
        }
    }

    /**
     * 确定风险级别
     */
    public RiskLevel determineRiskLevel(final AuditEventType type, final Boolean success) {
        if (type == null) return RiskLevel.LOW;
        if (success != null && !success) {
            if (type == AuditEventType.SECURITY_ALERT) return RiskLevel.CRITICAL;
            if (type == AuditEventType.SUSPICIOUS_ACTIVITY) return RiskLevel.HIGH;
            if (type == AuditEventType.AUTHORIZATION_FAILED) return RiskLevel.MEDIUM;
            return RiskLevel.LOW;
        }
        if (type == AuditEventType.JWT_TOKEN_REVOKED || type == AuditEventType.API_KEY_REVOKED) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    /**
     * Map转JSON字符串
     */
    public String toJson(final Map<String, Object> map) {
        if (map == null || map.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("JSON序列化失败", e);
            return null;
        }
    }

    /**
     * JSON字符串转Map
     */
    public Map<String, Object> parseJson(final String json) {
        if (json == null || json.isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            log.error("JSON解析失败", e);
            return new HashMap<>();
        }
    }
}
