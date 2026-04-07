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
    public ConfigChangeAuditServiceImpl(ConfigChangeAuditRepository auditRepository, ObjectMapper objectMapper) {
        this.auditRepository = auditRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void recordConfigCreation(
        String configKey,
        String configType,
        Map<String, Object> configValue,
        String operator,
        String operatorIp,
        String reason
    ) {
        recordChange("CREATE", configKey, configType, null, configValue, operator, operatorIp, reason);
    }

    @Override
    @Transactional
    public void recordConfigUpdate(
        String configKey,
        String configType,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String operator,
        String operatorIp,
        String reason
    ) {
        recordChange("UPDATE", configKey, configType, oldValue, newValue, operator, operatorIp, reason);
    }

    @Override
    @Transactional
    public void recordConfigDeletion(
        String configKey,
        String configType,
        Map<String, Object> oldValue,
        String operator,
        String operatorIp,
        String reason
    ) {
        recordChange("DELETE", configKey, configType, oldValue, null, operator, operatorIp, reason);
    }

    private void recordChange(
        String operation,
        String configKey,
        String configType,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String operator,
        String operatorIp,
        String reason
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

    private String generateChangeSummary(Map<String, Object> oldValue, Map<String, Object> newValue) {
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
