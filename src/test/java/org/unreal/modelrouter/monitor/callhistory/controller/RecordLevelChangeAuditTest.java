package org.unreal.modelrouter.monitor.callhistory.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * CallHistoryConfigController 单元测试
 * 覆盖配置查询、配置更新、审计事件触发
 *
 * @author JAiRouter Team
 * @since 2.9.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CallHistoryConfigController 配置管理与审计测试")
class RecordLevelChangeAuditTest {

    @Mock
    private CallHistoryProperties callHistoryProperties;

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private CallHistoryConfigController controller;

    // ==================== RBAC 注解验证 ====================

    @Nested
    @DisplayName("RBAC 注解检查")
    class RbacAnnotationTests {

        @Test
        @DisplayName("类级别 @PreAuthorize 注解存在且要求 ADMIN 角色")
        void classLevelPreAuthorizeExists() {
            PreAuthorize annotation = CallHistoryConfigController.class.getAnnotation(PreAuthorize.class);
            assertNotNull(annotation, "CallHistoryConfigController 应该有 @PreAuthorize 注解");
            assertEquals("hasRole('ADMIN')", annotation.value(),
                    "@PreAuthorize 应该要求 ADMIN 角色");
        }
    }

    // ==================== GET 配置查询 ====================

    @Nested
    @DisplayName("GET /api/config/call-history - 查询配置")
    class GetConfigTests {

        @Test
        @DisplayName("正常返回配置信息")
        void getConfigSuccess() {
            when(callHistoryProperties.getRecordLevel()).thenReturn(RecordLevel.METADATA_ONLY);
            when(callHistoryProperties.getMaxContentLength()).thenReturn(65536);
            when(callHistoryProperties.getRetentionDays()).thenReturn(30);
            when(callHistoryProperties.isEnabled()).thenReturn(true);
            when(callHistoryProperties.isRequestBodySummaryEnabled()).thenReturn(true);
            when(callHistoryProperties.isResponseBodySummaryEnabled()).thenReturn(true);
            when(callHistoryProperties.getSlowCallThresholdMs()).thenReturn(1000L);
            when(callHistoryProperties.getEncryptionKeySource()).thenReturn("auto");

            ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.getConfig();

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            Map<String, Object> data = response.getBody().getData();
            assertEquals("METADATA_ONLY", data.get("recordLevel"));
            assertEquals(65536, data.get("maxContentLength"));
            assertEquals(30, data.get("retentionDays"));
            assertEquals(true, data.get("enabled"));
        }
    }

    // ==================== PUT 配置更新 ====================

    @Nested
    @DisplayName("PUT /api/config/call-history - 更新配置")
    class UpdateConfigTests {

        @Test
        @DisplayName("更新 recordLevel 为 SUMMARY - 成功并触发审计")
        void updateRecordLevelSuccess() {
            when(callHistoryProperties.getRecordLevel()).thenReturn(RecordLevel.METADATA_ONLY);
            when(securityAuditService.recordEvent(any())).thenReturn(reactor.core.publisher.Mono.empty());
            // stub config getter calls for buildConfigMap after setRecordLevel
            when(callHistoryProperties.getMaxContentLength()).thenReturn(65536);
            when(callHistoryProperties.getRetentionDays()).thenReturn(30);
            when(callHistoryProperties.isEnabled()).thenReturn(true);
            when(callHistoryProperties.isRequestBodySummaryEnabled()).thenReturn(true);
            when(callHistoryProperties.isResponseBodySummaryEnabled()).thenReturn(true);
            when(callHistoryProperties.getSlowCallThresholdMs()).thenReturn(1000L);
            when(callHistoryProperties.getEncryptionKeySource()).thenReturn("auto");

            ResponseEntity<RouterResponse<Map<String, Object>>> response =
                    controller.updateConfig(Map.of("recordLevel", "SUMMARY"));

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(callHistoryProperties).setRecordLevel(RecordLevel.SUMMARY);

            // 验证审计事件
            ArgumentCaptor<SecurityAuditEvent> eventCaptor = ArgumentCaptor.forClass(SecurityAuditEvent.class);
            verify(securityAuditService).recordEvent(eventCaptor.capture());

            SecurityAuditEvent capturedEvent = eventCaptor.getValue();
            assertEquals("RECORD_LEVEL_CHANGE", capturedEvent.getEventType());
            assertEquals("call-history/config", capturedEvent.getResource());
            assertEquals("UPDATE", capturedEvent.getAction());
            assertTrue(capturedEvent.isSuccess());
            assertNotNull(capturedEvent.getAdditionalData());
            assertEquals("METADATA_ONLY", capturedEvent.getAdditionalData().get("oldLevel"));
            assertEquals("SUMMARY", capturedEvent.getAdditionalData().get("newLevel"));
        }

        @Test
        @DisplayName("更新 recordLevel 为 FULL - 小写输入自动转大写")
        void updateRecordLevelCaseInsensitive() {
            when(callHistoryProperties.getRecordLevel()).thenReturn(RecordLevel.SUMMARY);
            when(securityAuditService.recordEvent(any())).thenReturn(reactor.core.publisher.Mono.empty());
            when(callHistoryProperties.getMaxContentLength()).thenReturn(65536);
            when(callHistoryProperties.getRetentionDays()).thenReturn(30);
            when(callHistoryProperties.isEnabled()).thenReturn(true);
            when(callHistoryProperties.isRequestBodySummaryEnabled()).thenReturn(true);
            when(callHistoryProperties.isResponseBodySummaryEnabled()).thenReturn(true);
            when(callHistoryProperties.getSlowCallThresholdMs()).thenReturn(1000L);
            when(callHistoryProperties.getEncryptionKeySource()).thenReturn("auto");

            ResponseEntity<RouterResponse<Map<String, Object>>> response =
                    controller.updateConfig(Map.of("recordLevel", "full"));

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(callHistoryProperties).setRecordLevel(RecordLevel.FULL);
        }

        @Test
        @DisplayName("无效的 recordLevel 值 - 返回 400")
        void updateRecordLevelInvalid() {
            ResponseEntity<RouterResponse<Map<String, Object>>> response =
                    controller.updateConfig(Map.of("recordLevel", "INVALID_LEVEL"));

            assertNotNull(response.getBody());
            assertFalse(response.getBody().isSuccess());
            assertEquals("INVALID_RECORD_LEVEL", response.getBody().getErrorCode());
            verify(callHistoryProperties, never()).setRecordLevel(any());
            verifyNoInteractions(securityAuditService);
        }

        @Test
        @DisplayName("缺少 recordLevel 字段 - 返回 400")
        void updateRecordLevelMissing() {
            ResponseEntity<RouterResponse<Map<String, Object>>> response =
                    controller.updateConfig(Map.of("otherField", "value"));

            assertNotNull(response.getBody());
            assertFalse(response.getBody().isSuccess());
            assertEquals("INVALID_REQUEST", response.getBody().getErrorCode());
            verifyNoInteractions(securityAuditService);
        }

        @Test
        @DisplayName("空字符串 recordLevel - 返回 400")
        void updateRecordLevelEmpty() {
            ResponseEntity<RouterResponse<Map<String, Object>>> response =
                    controller.updateConfig(Map.of("recordLevel", ""));

            assertNotNull(response.getBody());
            assertFalse(response.getBody().isSuccess());
            verifyNoInteractions(securityAuditService);
        }

        @Test
        @DisplayName("审计事件发送异常不影响配置更新结果")
        void auditExceptionDoesNotBreakUpdate() {
            when(callHistoryProperties.getRecordLevel()).thenReturn(RecordLevel.METADATA_ONLY);
            when(securityAuditService.recordEvent(any())).thenReturn(reactor.core.publisher.Mono.empty());
            when(callHistoryProperties.getMaxContentLength()).thenReturn(65536);
            when(callHistoryProperties.getRetentionDays()).thenReturn(30);
            when(callHistoryProperties.isEnabled()).thenReturn(true);
            when(callHistoryProperties.isRequestBodySummaryEnabled()).thenReturn(true);
            when(callHistoryProperties.isResponseBodySummaryEnabled()).thenReturn(true);
            when(callHistoryProperties.getSlowCallThresholdMs()).thenReturn(1000L);
            when(callHistoryProperties.getEncryptionKeySource()).thenReturn("auto");

            ResponseEntity<RouterResponse<Map<String, Object>>> response =
                    controller.updateConfig(Map.of("recordLevel", "FULL"));

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(callHistoryProperties).setRecordLevel(RecordLevel.FULL);
        }
    }
}
