package org.unreal.modelrouter.monitor.callhistory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 调用历史配置管理控制器
 * 提供记录治理（Record Governance）配置的查询和修改接口
 *
 * @author JAiRouter Team
 * @since 2.9.2
 */
@Slf4j
@RestController
@RequestMapping("/api/config/call-history")
@RequiredArgsConstructor
@Tag(name = "调用历史配置", description = "调用历史记录治理配置管理接口")
@PreAuthorize("hasRole('ADMIN')")
public class CallHistoryConfigController {

    private final CallHistoryProperties callHistoryProperties;
    private final SecurityAuditService securityAuditService;

    /**
     * 获取调用历史配置
     */
    @GetMapping
    @Operation(summary = "获取调用历史配置", description = "获取当前调用历史记录治理配置")
    public ResponseEntity<RouterResponse<Map<String, Object>>> getConfig() {
        Map<String, Object> config = buildConfigMap();
        return ResponseEntity.ok(RouterResponse.success(config));
    }

    /**
     * 更新调用历史配置
     * 当前支持修改 recordLevel 字段
     */
    @PutMapping
    @Operation(summary = "更新调用历史配置", description = "更新调用历史记录治理配置（如记录级别）")
    public ResponseEntity<RouterResponse<Map<String, Object>>> updateConfig(
            @RequestBody final Map<String, String> body) {

        String newLevelStr = body.get("recordLevel");
        if (newLevelStr == null || newLevelStr.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error("缺少必填字段: recordLevel", "INVALID_REQUEST"));
        }

        // 校验枚举值
        RecordLevel newLevel;
        try {
            newLevel = RecordLevel.valueOf(newLevelStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(RouterResponse.error(
                            "无效的 recordLevel 值: " + newLevelStr
                                    + "，有效值: METADATA_ONLY, SUMMARY, FULL",
                            "INVALID_RECORD_LEVEL"));
        }

        // 记录旧值并更新
        RecordLevel oldLevel = callHistoryProperties.getRecordLevel();
        callHistoryProperties.setRecordLevel(newLevel);

        log.info("调用历史记录级别已更新: {} -> {}", oldLevel, newLevel);

        // 发送审计事件
        emitRecordLevelChangeAudit(oldLevel, newLevel);

        Map<String, Object> config = buildConfigMap();
        return ResponseEntity.ok(RouterResponse.success(config, "配置已更新"));
    }

    /**
     * 构建配置响应 Map
     */
    private Map<String, Object> buildConfigMap() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("recordLevel", callHistoryProperties.getRecordLevel().name());
        config.put("maxContentLength", callHistoryProperties.getMaxContentLength());
        config.put("retentionDays", callHistoryProperties.getRetentionDays());
        config.put("enabled", callHistoryProperties.isEnabled());
        config.put("requestBodySummaryEnabled", callHistoryProperties.isRequestBodySummaryEnabled());
        config.put("responseBodySummaryEnabled", callHistoryProperties.isResponseBodySummaryEnabled());
        config.put("slowCallThresholdMs", callHistoryProperties.getSlowCallThresholdMs());
        config.put("encryptionKeySource", callHistoryProperties.getEncryptionKeySource());
        return config;
    }

    /**
     * 发送 RECORD_LEVEL_CHANGE 审计事件
     *
     * @param oldLevel 旧记录级别
     * @param newLevel 新记录级别
     */
    private void emitRecordLevelChangeAudit(final RecordLevel oldLevel, final RecordLevel newLevel) {
        try {
            Map<String, Object> additionalData = new LinkedHashMap<>();
            additionalData.put("oldLevel", oldLevel.name());
            additionalData.put("newLevel", newLevel.name());

            SecurityAuditEvent event = SecurityAuditEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("RECORD_LEVEL_CHANGE")
                    .resource("call-history/config")
                    .action("UPDATE")
                    .success(true)
                    .additionalData(additionalData)
                    .build();

            securityAuditService.recordEvent(event).subscribe(
                    v -> log.debug("RECORD_LEVEL_CHANGE 审计事件已记录: {} -> {}", oldLevel, newLevel),
                    e -> log.warn("记录 RECORD_LEVEL_CHANGE 审计事件失败: {}", e.getMessage())
            );
        } catch (Exception e) {
            log.warn("发送 RECORD_LEVEL_CHANGE 审计事件异常: {}", e.getMessage());
        }
    }
}
