package org.unreal.modelrouter.monitor.callhistory.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.crypto.RecordContentCipher;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code GET /api/config/call-history} 的只读观测字段测试。
 *
 * <p>这些字段存在的目的：把「配置请求的加密来源」与「实现实际生效的来源」同时暴露出来。
 * 3.5.1 之前 `encryption-key-source: env` 会被静默忽略，页面却显示 env —— 只因接口回显配置值、
 * 没有回显实际装配结果。这里锁住「回显实际结果」的契约。
 *
 * @author JAiRouter Team
 * @since 3.5.1
 */
@DisplayName("CallHistoryConfigController 只读观测字段测试")
class CallHistoryConfigControllerObservabilityTest {

    private final CallHistoryProperties properties = new CallHistoryProperties();
    private final SecurityAuditService auditService = mock(SecurityAuditService.class);
    private final RecordContentCipher cipher = mock(RecordContentCipher.class);

    private Map<String, Object> fetchConfig() {
        CallHistoryConfigController controller =
                new CallHistoryConfigController(properties, auditService, cipher);
        RouterResponse<Map<String, Object>> response = controller.getConfig().getBody();
        return response.getData();
    }

    @Test
    @DisplayName("env 请求但回退 auto 时：effective=auto、honored=false、env 未就绪")
    void reportsFallbackHonestly() {
        properties.setRecordLevel(RecordLevel.FULL);
        properties.setEncryptionKeySource("env");
        when(cipher.keySourceRequested()).thenReturn("env");
        when(cipher.keySourceEffective()).thenReturn("auto");
        when(cipher.environmentKeyPresent()).thenReturn(false);
        when(cipher.keyFingerprint()).thenReturn("a1b2c3d4");
        when(cipher.algorithm()).thenReturn("AES-256-GCM");

        Map<String, Object> config = fetchConfig();

        assertEquals("env", config.get("encryptionKeySource"), "配置值原样回显");
        assertEquals("env", config.get("encryptionKeySourceRequested"));
        assertEquals("auto", config.get("encryptionKeySourceEffective"), "必须回显实际生效值而不是配置值");
        assertEquals(false, config.get("encryptionKeySourceHonored"));
        assertEquals(false, config.get("environmentKeyPresent"));
        assertEquals("AES-256-GCM", config.get("encryptionAlgorithm"));
        assertEquals("a1b2c3d4", config.get("encryptionKeyFingerprint"));
        assertEquals(true, config.get("contentStoredEncrypted"));
    }

    @Test
    @DisplayName("env 生效时：effective=env、honored=true、env 就绪")
    void reportsEnvironmentKeyHonored() {
        properties.setRecordLevel(RecordLevel.SUMMARY);
        when(cipher.keySourceRequested()).thenReturn("env");
        when(cipher.keySourceEffective()).thenReturn("env");
        when(cipher.environmentKeyPresent()).thenReturn(true);

        Map<String, Object> config = fetchConfig();

        assertEquals("env", config.get("encryptionKeySourceEffective"));
        assertEquals(true, config.get("encryptionKeySourceHonored"));
        assertEquals(true, config.get("environmentKeyPresent"));
        assertEquals(false, config.get("contentStoredEncrypted"), "SUMMARY 不加密存储，contentStoredEncrypted 必须为 false");
    }

    @Test
    @DisplayName("只读字段包含存储上限与保留策略")
    void exposesStoragePolicyFields() {
        properties.setMaxContentLength(65536);
        properties.setRetentionDays(30);
        properties.setMaxRecords(1_000_000);
        when(cipher.keySourceEffective()).thenReturn("auto");

        Map<String, Object> config = fetchConfig();

        assertEquals(65536, config.get("maxContentLength"));
        assertEquals(30, config.get("retentionDays"));
        assertEquals(1_000_000L, config.get("maxRecords"));
        assertTrue(config.containsKey("slowCallThresholdMs"));
        assertTrue(config.containsKey("encryptionKeySourceEffective"));
        assertTrue(config.containsKey("encryptionAlgorithm"));
    }
}
