/**
 * 配置变更审计服务实现类（JPA 版本）
 * v1.5.1: 从 R2DBC 迁移到 JPA
 */
package org.unreal.modelrouter.auth.audit;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.common.filter.RequestIdWebFilter;
import org.unreal.modelrouter.monitor.tracing.TracingContext;
import org.unreal.modelrouter.monitor.tracing.TracingContextHolder;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ConfigChangeAuditServiceImpl implements ConfigChangeAuditService {

    private final ConfigChangeAuditRepository auditRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public ConfigChangeAuditServiceImpl(
            final ConfigChangeAuditRepository auditRepository,
            final ObjectMapper objectMapper) {
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
                    .traceId(currentTraceId())
                    .timestamp(LocalDateTime.now())
                    .build();

            auditRepository.save(audit);
            log.debug("Recorded config change audit: {} - {}", operation, configKey);
        } catch (JacksonException e) {
            log.error("Failed to serialize config value for audit", e);
        }
    }

    /**
     * 取当前请求的追踪 ID，写入审计记录（#1154）。
     *
     * <p>优先读 MDC：{@code RequestIdWebFilter} 会在请求入口把 traceId 放进去，
     * 且用 {@code contextCapture()} 保证跨线程可见。若在非 Web 线程上写审计
     * （例如启动期迁移），MDC 为空，再回落 {@link TracingContextHolder}。</p>
     *
     * <p>取不到就返回 null：该列允许为空，「审计与链路对不上」比「审计写不进去」危害小。</p>
     *
     * @return 追踪 ID，或 null
     */
    private static String currentTraceId() {
        final String fromMdc = MDC.get(RequestIdWebFilter.MDC_TRACE_ID_KEY);
        if (fromMdc != null && !fromMdc.isEmpty()) {
            return fromMdc;
        }
        final TracingContext context = TracingContextHolder.getCurrentContext();
        if (context != null && context.isActive()) {
            return context.getTraceId();
        }
        return null;
    }

    private String generateChangeSummary(final Map<String, Object> oldValue, final Map<String, Object> newValue) {
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
