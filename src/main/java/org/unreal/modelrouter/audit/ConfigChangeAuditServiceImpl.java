/**
 * 配置变更审计服务实现类（JPA 版本）
 * v1.5.1: 从 R2DBC 迁移到 JPA
 */
package org.unreal.modelrouter.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ConfigChangeAuditServiceImpl implements ConfigChangeAuditService {

    private final ConfigChangeAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConfigChangeAuditServiceImpl(final ConfigChangeAuditRepository auditRepository,final ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void recordConfigCreation(
        final String configKey,
        final String configType,
        final Map<String, Object> configValue,
        final String operator,
        final String operatorIp,
        final String reason
    ) {
        recordChange("CREATE", configKey, configType, null, configValue, operator, operatorIp, reason);
    }

    @Override
    @Transactional
    public void recordConfigUpdate(
        final String configKey,
        final String configType,
        final Map<String, Object> oldValue,
        final Map<String, Object> newValue,
        final String operator,
        final String operatorIp,
        final String reason
    ) {
        recordChange("UPDATE", configKey, configType, oldValue, newValue, operator, operatorIp, reason);
    }

    @Override
    @Transactional
    public void recordConfigDeletion(
        final String configKey,
        final String configType,
        final Map<String, Object> oldValue,
        final String operator,
        final String operatorIp,
        final String reason
    ) {
        recordChange("DELETE", configKey, configType, oldValue, null, operator, operatorIp, reason);
    }

    private void recordChange(
        final String operation,
        final String configKey,
        final String configType,
        final Map<String, Object> oldValue,
        final Map<String, Object> newValue,
        final String operator,
        final String operatorIp,
        final String reason
    ) {
        try {
            ConfigChangeAuditEntity audit = ConfigChangeAuditEntity.builder()
                    .eventId(UUID.randomUUID().toString())
                    .configKey(configKey)
                    .configType(configType)
                    .operation(operation)
                    .operator(operator)
                    .operatorIp(operatorIp)
                    .oldValueJson(oldValue != null ? objectMapper.writeValueAsString(oldValue) : null)
                    .newValueJson(newValue != null ? objectMapper.writeValueAsString(newValue) : null)
                    .changeSummary(generateChangeSummary(oldValue, newValue))
                    .reason(reason)
                    .timestamp(LocalDateTime.now())
                    .build();

            auditRepository.save(audit);
            log.debug("Recorded config change audit: {} - {}", operation, configKey);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize config value for audit", e);
        }
    }

    private String generateChangeSummary(final Map<String, Object> oldValue,final Map<String, Object> newValue) {
        if (oldValue == null && newValue != null) {
            return "Created new configuration";
        } else if (oldValue != null && newValue == null) {
            return "Deleted configuration";
        } else if (oldValue != null && newValue != null) {
            return "Updated configuration";
        }
        return "Unknown change";
    }
}
