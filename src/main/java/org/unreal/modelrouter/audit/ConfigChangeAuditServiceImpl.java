/**
 * 配置变更审计服务实现类（简化版）
 */
package org.unreal.modelrouter.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ConfigChangeAuditServiceImpl implements ConfigChangeAuditService {

    private final R2dbcEntityTemplate r2dbcTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConfigChangeAuditServiceImpl(R2dbcEntityTemplate r2dbcTemplate, ObjectMapper objectMapper) {
        this.r2dbcTemplate = r2dbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> recordConfigCreation(
        String configKey,
        String configType,
        Map<String, Object> configValue,
        String operator,
        String operatorIp,
        String reason
    ) {
        return recordChange("CREATE", configKey, configType, null, configValue, operator, operatorIp, reason);
    }

    @Override
    public Mono<Void> recordConfigUpdate(
        String configKey,
        String configType,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String operator,
        String operatorIp,
        String reason
    ) {
        return recordChange("UPDATE", configKey, configType, oldValue, newValue, operator, operatorIp, reason);
    }

    @Override
    public Mono<Void> recordConfigDeletion(
        String configKey,
        String configType,
        Map<String, Object> oldValue,
        String operator,
        String operatorIp,
        String reason
    ) {
        return recordChange("DELETE", configKey, configType, oldValue, null, operator, operatorIp, reason);
    }

    /**
     * 记录配置变更
     */
    private Mono<Void> recordChange(
        String operation,
        String configKey,
        String configType,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String operator,
        String operatorIp,
        String reason
    ) {
        ConfigChangeAuditEntity entity = new ConfigChangeAuditEntity();
        entity.setEventId(UUID.randomUUID().toString());
        entity.setConfigKey(configKey);
        entity.setConfigType(configType);
        entity.setOperation(operation);
        entity.setOperator(operator != null ? operator : "system");
        entity.setOperatorIp(operatorIp);
        entity.setOldValueJson(toJson(oldValue));
        entity.setNewValueJson(toJson(newValue));
        entity.setChangeSummary(generateChangeSummary(operation, configType, configKey));
        entity.setReason(reason);
        entity.setTimestamp(LocalDateTime.now());

        return r2dbcTemplate.insert(entity)
            .then()
            .doOnSuccess(unused -> log.debug("记录配置变更审计事件：{} {} {}", operation, configType, configKey))
            .doOnError(error -> log.error("记录配置变更审计事件失败：{} {} {}", operation, configType, configKey, error));
    }

    /**
     * 生成变更摘要
     */
    private String generateChangeSummary(String operation, String configType, String configKey) {
        return String.format("%s %s 配置：%s", operation, configType, configKey);
    }

    /**
     * 对象转 JSON
     */
    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("序列化对象失败", e);
            throw new RuntimeException("序列化对象失败", e);
        }
    }
}
