package org.unreal.modelrouter.monitor.callhistory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryQueryDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ApiCallHistoryController RBAC 和详情端点单元测试
 *
 * 验证：
 * 1. @PreAuthorize("hasRole('ADMIN')") 注解存在于类级别
 * 2. 详情端点（detail）正常返回解密数据
 * 3. FULL 级别记录触发 FULL_CONTENT_ACCESS 审计事件
 *
 * @author JAiRouter Team
 * @since 2.9.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiCallHistoryController RBAC 与详情端点测试")
class ApiCallHistoryControllerRbacTest {

    @Mock
    private ApiCallHistoryService callHistoryService;

    @Mock
    private ApiCallHistoryRecorder callHistoryRecorder;

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private ApiCallHistoryController controller;

    // ==================== RBAC 检查 ====================

    @Nested
    @DisplayName("RBAC 检查")
    class RbacAnnotationTests {

        @Test
        @DisplayName("controller 无 @PreAuthorize（RBAC 由 SecurityConfiguration URL 规则保护）")
        void noClassLevelPreAuthorize() {
            // v2.9.4-fix: 同步返回类型的 controller 不能用类级 @PreAuthorize
            // （@EnableReactiveMethodSecurity 要求 Publisher 返回类型，真实请求 500），
            // RBAC 已移至 SecurityConfiguration 的 URL 规则：/api/call-history/** -> hasRole('ADMIN')
            PreAuthorize annotation = ApiCallHistoryController.class.getAnnotation(PreAuthorize.class);
            assertNull(annotation,
                    "ApiCallHistoryController 不应有 @PreAuthorize（RBAC 由 URL 规则保护）");
        }
    }

    // ==================== 详情端点测试 ====================

    @Nested
    @DisplayName("GET /{id}/detail - 查询记录详情")
    class GetDetailTests {

        @Test
        @DisplayName("记录存在时返回解密后的实体")
        void detailFound() {
            ApiCallHistoryEntity entity = ApiCallHistoryEntity.builder()
                    .id(1L)
                    .recordLevel(RecordLevel.METADATA_ONLY.name())
                    .traceId("trace-1")
                    .requestId("req-1")
                    .requestMethod("POST")
                    .requestPath("/v1/chat/completions")
                    .serviceType("chat")
                    .modelName("gpt-4")
                    .build();

            when(callHistoryService.findById(1L)).thenReturn(Optional.of(entity));
            when(callHistoryService.decryptBodies(entity)).thenReturn(entity);

            ResponseEntity<RouterResponse<ApiCallHistoryEntity>> response = controller.getDetail(1L);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(entity, response.getBody().getData());
            verify(callHistoryService).decryptBodies(entity);
            // METADATA_ONLY 不应触发审计
            verifyNoInteractions(securityAuditService);
        }

        @Test
        @DisplayName("记录不存在时返回错误")
        void detailNotFound() {
            when(callHistoryService.findById(999L)).thenReturn(Optional.empty());

            ResponseEntity<RouterResponse<ApiCallHistoryEntity>> response = controller.getDetail(999L);

            assertNotNull(response.getBody());
            assertFalse(response.getBody().isSuccess());
            assertTrue(response.getBody().getMessage().contains("记录不存在"));
            // issue #413：状态码已按 NOT_FOUND 解析，响应体也必须带同一个码（此前为 null）
            assertEquals(ErrorCode.NOT_FOUND.code(), response.getBody().getErrorCode());
        }

        @Test
        @DisplayName("FULL 级别记录触发 FULL_CONTENT_ACCESS 审计事件")
        void fullLevelTriggersAudit() {
            ApiCallHistoryEntity entity = ApiCallHistoryEntity.builder()
                    .id(2L)
                    .recordLevel(RecordLevel.FULL.name())
                    .traceId("trace-2")
                    .requestId("req-2")
                    .requestMethod("POST")
                    .requestPath("/v1/chat/completions")
                    .serviceType("chat")
                    .modelName("gpt-4")
                    .requestBodyEncrypted("encrypted-data")
                    .build();

            when(callHistoryService.findById(2L)).thenReturn(Optional.of(entity));
            when(callHistoryService.decryptBodies(entity)).thenReturn(entity);
            when(securityAuditService.recordEvent(any())).thenReturn(reactor.core.publisher.Mono.empty());

            ResponseEntity<RouterResponse<ApiCallHistoryEntity>> response = controller.getDetail(2L);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(securityAuditService).recordEvent(argThat(event ->
                    AuditEventType.FULL_CONTENT_ACCESS == AuditEventType.valueOf(event.getEventType())
                            && "call-history".equals(event.getResource())
                            && "DETAIL".equals(event.getAction())
            ));
        }

        @Test
        @DisplayName("SUMMARY 级别记录不触发审计事件")
        void summaryLevelNoAudit() {
            ApiCallHistoryEntity entity = ApiCallHistoryEntity.builder()
                    .id(3L)
                    .recordLevel(RecordLevel.SUMMARY.name())
                    .traceId("trace-3")
                    .requestId("req-3")
                    .requestMethod("POST")
                    .requestPath("/v1/chat/completions")
                    .serviceType("chat")
                    .modelName("gpt-4")
                    .build();

            when(callHistoryService.findById(3L)).thenReturn(Optional.of(entity));
            when(callHistoryService.decryptBodies(entity)).thenReturn(entity);

            ResponseEntity<RouterResponse<ApiCallHistoryEntity>> response = controller.getDetail(3L);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verifyNoInteractions(securityAuditService);
        }
    }

    // ==================== 属主校验测试（issue #698） ====================

    @Nested
    @DisplayName("POST /record - 写入属主校验（issue #698）")
    class OwnershipTests {

        private org.springframework.security.core.Authentication apiKeyOf(final String keyId) {
            return new org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication(
                    keyId, "sk-test", java.util.List.of("chat"));
        }

        private org.springframework.security.core.Authentication jwtPrincipal() {
            return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "admin", null, java.util.List.of(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        }

        private org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO recordOf(final String apiKeyId) {
            return org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO.builder()
                    .traceId("trace-698")
                    .requestId("req-698")
                    .requestMethod("POST")
                    .requestPath("/v1/chat/completions")
                    .serviceType("chat")
                    .modelName("gpt-4")
                    .apiKeyId(apiKeyId)
                    .build();
        }

        @Test
        @DisplayName("CALLH-698-1: API Key 主体写他人 apiKeyId ⇒ 403 且不落库")
        void apiKeyClaimingOtherKeyId_isRejected() {
            final org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO record =
                    recordOf("key-victim");

            final ResponseEntity<RouterResponse<Void>> result = controller.record(record, apiKeyOf("key-attacker"));

            assertEquals(org.springframework.http.HttpStatus.FORBIDDEN, result.getStatusCode());
            assertNotNull(result.getBody());
            assertFalse(result.getBody().isSuccess());
            assertEquals(ErrorCode.FORBIDDEN.code(), result.getBody().getErrorCode());
            assertTrue(result.getBody().getMessage().contains("只能写入自身"),
                    "拒绝原因必须说明属主约束，实际: " + result.getBody().getMessage());
            verify(callHistoryService, never()).record(any());
        }

        @Test
        @DisplayName("CALLH-698-2: API Key 主体写自身 apiKeyId ⇒ 放行")
        void apiKeyClaimingOwnKeyId_isAccepted() {
            final org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO record =
                    recordOf("key-self");

            final ResponseEntity<RouterResponse<Void>> result = controller.record(record, apiKeyOf("key-self"));

            assertEquals(org.springframework.http.HttpStatus.OK, result.getStatusCode());
            verify(callHistoryService).record(record);
        }

        @Test
        @DisplayName("CALLH-698-3: API Key 主体未声明 apiKeyId ⇒ 放行（无从矛盾）")
        void apiKeyWithoutClaimedKeyId_isAccepted() {
            final org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO record = recordOf(null);

            final ResponseEntity<RouterResponse<Void>> result = controller.record(record, apiKeyOf("key-self"));

            assertEquals(org.springframework.http.HttpStatus.OK, result.getStatusCode());
            verify(callHistoryService).record(record);
        }

        @Test
        @DisplayName("CALLH-698-4: JWT 管理面主体写他人 apiKeyId ⇒ 保持既有行为（放行）")
        void jwtPrincipalClaimingOtherKeyId_isAcceptedForNow() {
            final org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO record =
                    recordOf("key-victim");

            final ResponseEntity<RouterResponse<Void>> result = controller.record(record, jwtPrincipal());

            assertEquals(org.springframework.http.HttpStatus.OK, result.getStatusCode(),
                    "管理面代报属未定义的调用形态，本次刻意不收紧");
            verify(callHistoryService).record(record);
        }

        @Test
        @DisplayName("CALLH-698-5: 未注入认证主体（null）⇒ 放行，安全链已拦未认证请求")
        void nullAuthentication_isAccepted() {
            final org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO record =
                    recordOf("key-victim");

            final ResponseEntity<RouterResponse<Void>> result = controller.record(record, null);

            assertEquals(org.springframework.http.HttpStatus.OK, result.getStatusCode());
            verify(callHistoryService).record(record);
        }
    }

    // ==================== 基础端点功能测试 ====================

    @Nested
    @DisplayName("基础端点功能测试")
    class BasicEndpointTests {

        @Test
        @DisplayName("查询调用历史 - 正常返回")
        void querySuccess() {
            CallHistoryQueryDTO query = new CallHistoryQueryDTO();
            org.springframework.data.domain.Page<ApiCallHistoryEntity> mockPage =
                    mock(org.springframework.data.domain.Page.class);

            when(callHistoryService.query(any(CallHistoryQueryDTO.class))).thenReturn(mockPage);

            ResponseEntity<RouterResponse<org.springframework.data.domain.Page<ApiCallHistoryEntity>>> response =
                    controller.query(query);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(callHistoryService).query(query);
        }

        @Test
        @DisplayName("获取统计信息 - 正常返回")
        void statisticsSuccess() {
            CallHistoryStatisticsDTO stats = CallHistoryStatisticsDTO.builder().build();
            when(callHistoryService.getStatistics(any(), any())).thenReturn(stats);

            ResponseEntity<RouterResponse<CallHistoryStatisticsDTO>> response =
                    controller.getStatistics(null, null);

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
        }

        @Test
        @DisplayName("获取总记录数 - 正常返回")
        void countSuccess() {
            when(callHistoryService.countAll()).thenReturn(42L);

            ResponseEntity<RouterResponse<java.util.Map<String, Object>>> response = controller.count();

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(42L, response.getBody().getData().get("count"));
        }
    }
}
