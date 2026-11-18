package org.unreal.modelrouter.monitor.callhistory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unreal.modelrouter.auth.security.audit.SecurityAuditService;
import org.unreal.modelrouter.auth.security.permission.PermissionCodes;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.auth.security.permission.DataScope;
import org.unreal.modelrouter.auth.security.permission.DataScopeResolver;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import reactor.core.publisher.Mono;
import org.unreal.modelrouter.common.dto.AuditEventType;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryQueryDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ApiCallHistoryController RBAC 与正文可见性单元测试。
 *
 * <p>验证：
 * <ol>
 *   <li>controller 无类级 {@code @PreAuthorize}（RBAC 由 URL 规则保护）</li>
 *   <li>调用历史双码：{@code callhistory:read} 看元数据与摘要，
 *   {@code callhistory:full-content} 才解密正文</li>
 *   <li>只有真正解密 FULL 正文时才记 {@code FULL_CONTENT_ACCESS} 审计 ——
 *   仅持 read 的运维不该产生「解密查看」审计噪音</li>
 *   <li>无 full-content 时加密字段一律从响应里清空（列表与详情同一口径）</li>
 * </ol>
 *
 * @author JAiRouter Team
 * @since 2.9.2
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiCallHistoryController RBAC 与正文可见性测试")
class ApiCallHistoryControllerRbacTest {

    @Mock
    private ApiCallHistoryService callHistoryService;

    @Mock
    private ApiCallHistoryRecorder callHistoryRecorder;

    @Mock
    private SecurityAuditService securityAuditService;

    @Mock
    private DataScopeResolver dataScopeResolver;

    @InjectMocks
    private ApiCallHistoryController controller;

    @org.junit.jupiter.api.BeforeEach
    void stubUnrestrictedScope() {
        org.mockito.Mockito.lenient().when(dataScopeResolver.resolveWithKeys(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.just(DataScope.unrestricted()));
    }

    // ==================== 认证主体工具 ====================

    /**
     * 持若干权限码的运维主体（不含 ROLE_ADMIN）。
     */
    private static Authentication withCodes(final String... codes) {
        return new UsernamePasswordAuthenticationToken(
                "operator", null,
                Arrays.stream(codes).map(SimpleGrantedAuthority::new).toList());
    }

    private static Authentication admin() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static ApiCallHistoryEntity record(final long id, final RecordLevel level) {
        return ApiCallHistoryEntity.builder()
                .id(id)
                .recordLevel(level.name())
                .traceId("trace-" + id)
                .requestId("req-" + id)
                .requestMethod("POST")
                .requestPath("/v1/chat/completions")
                .serviceType("chat")
                .modelName("gpt-4")
                .requestBodySummary("request-summary")
                .responseBodySummary("response-summary")
                .requestBodyEncrypted("encrypted-request")
                .responseBodyEncrypted("encrypted-response")
                .build();
    }

    // ==================== RBAC 检查 ====================

    @Nested
    @DisplayName("RBAC 检查")
    class RbacAnnotationTests {

        @Test
        @DisplayName("controller 无 @PreAuthorize（RBAC 由 URL 规则保护）")
        void noClassLevelPreAuthorize() {
            // v2.9.4-fix: 同步返回类型的 controller 不能用类级 @PreAuthorize
            // （@EnableReactiveMethodSecurity 要求 Publisher 返回类型，真实请求 500），
            // RBAC 已移至 URL 规则：GET /api/call-history/** -> callhistory:read，
            // 正文解密再由 callhistory:full-content 在本类内细粒度把关。
            PreAuthorize annotation = ApiCallHistoryController.class.getAnnotation(PreAuthorize.class);
            assertNull(annotation,
                    "ApiCallHistoryController 不应有 @PreAuthorize（RBAC 由 URL 规则保护）");
        }
    }

    // ==================== 详情端点：双码 ====================

    @Nested
    @DisplayName("GET /{id}/detail - 双码正文可见性")
    class GetDetailTests {

        @Test
        @DisplayName("仅持 callhistory:read ⇒ 不解密、不发审计、加密字段被清空")
        void readOnlyDoesNotDecrypt() {
            ApiCallHistoryEntity entity = record(1L, RecordLevel.FULL);
            when(callHistoryService.findById(1L)).thenReturn(Optional.of(entity));

            ResponseEntity<RouterResponse<ApiCallHistoryEntity>> response =
                    controller.getDetail(1L, withCodes(PermissionCodes.CALLHISTORY_READ)).block();

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            // 核心断言：正文不可见，但摘要仍可读（运维面需要的正是摘要与元数据）
            assertNull(entity.getRequestBodyEncrypted(), "无 full-content 时不得返回请求正文密文");
            assertNull(entity.getResponseBodyEncrypted(), "无 full-content 时不得返回响应正文密文");
            assertEquals("request-summary", entity.getRequestBodySummary());
            verify(callHistoryService, never()).decryptBodies(any());
            verifyNoInteractions(securityAuditService);
        }

        @Test
        @DisplayName("持 callhistory:full-content ⇒ 解密 FULL 正文并发 FULL_CONTENT_ACCESS 审计")
        void fullContentDecryptsAndAudits() {
            ApiCallHistoryEntity entity = record(2L, RecordLevel.FULL);
            when(callHistoryService.findById(2L)).thenReturn(Optional.of(entity));
            when(securityAuditService.recordEvent(any())).thenReturn(reactor.core.publisher.Mono.empty());

            ResponseEntity<RouterResponse<ApiCallHistoryEntity>> response =
                    controller.getDetail(2L, withCodes(
                            PermissionCodes.CALLHISTORY_READ, PermissionCodes.CALLHISTORY_FULL_CONTENT)).block();

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(callHistoryService).decryptBodies(entity);
            verify(securityAuditService).recordEvent(argThat(event ->
                    AuditEventType.FULL_CONTENT_ACCESS == AuditEventType.valueOf(event.getEventType())
                            && "call-history".equals(event.getResource())
                            && "DETAIL".equals(event.getAction())
                            // 审计必须能回答「谁看了正文」：运行态核对曾发现 userId 为空
                            && "operator".equals(event.getUserId())
                            && event.getAdditionalData() != null
                            && Long.valueOf(2L).equals(event.getAdditionalData().get("recordId"))
            ));
        }

        @Test
        @DisplayName("ADMIN 直通（ROLE_ADMIN 即隐含 full-content）")
        void adminImpliesFullContent() {
            ApiCallHistoryEntity entity = record(3L, RecordLevel.FULL);
            when(callHistoryService.findById(3L)).thenReturn(Optional.of(entity));
            when(securityAuditService.recordEvent(any())).thenReturn(reactor.core.publisher.Mono.empty());

            controller.getDetail(3L, admin()).block();

            verify(callHistoryService).decryptBodies(entity);
            verify(securityAuditService).recordEvent(argThat(event -> "admin".equals(event.getUserId())));
        }

        @Test
        @DisplayName("FULL 之外（SUMMARY）即使有 full-content 也不发审计")
        void summaryLevelNoAudit() {
            ApiCallHistoryEntity entity = record(4L, RecordLevel.SUMMARY);
            when(callHistoryService.findById(4L)).thenReturn(Optional.of(entity));

            controller.getDetail(4L, withCodes(PermissionCodes.CALLHISTORY_FULL_CONTENT)).block();

            verify(callHistoryService).decryptBodies(entity);
            verifyNoInteractions(securityAuditService);
        }

        @Test
        @DisplayName("记录不存在时返回 NOT_FOUND 错误")
        void detailNotFound() {
            when(callHistoryService.findById(999L)).thenReturn(Optional.empty());

            ResponseEntity<RouterResponse<ApiCallHistoryEntity>> response =
                    controller.getDetail(999L, withCodes(PermissionCodes.CALLHISTORY_READ)).block();

            assertNotNull(response.getBody());
            assertFalse(response.getBody().isSuccess());
            assertTrue(response.getBody().getMessage().contains("记录不存在"));
            // issue #413：状态码已按 NOT_FOUND 解析，响应体也必须带同一个码（此前为 null）
            assertEquals(ErrorCode.NOT_FOUND.code(), response.getBody().getErrorCode());
        }
    }

    // ==================== 列表端点：同一裁剪口径 ====================

    @Nested
    @DisplayName("列表端点 - 无 full-content 时清空密文")
    class ListRedactionTests {

        @Test
        @DisplayName("GET /api/call-history 分页结果不返回密文")
        void queryRedactsBodies() {
            ApiCallHistoryEntity entity = record(10L, RecordLevel.FULL);
            when(callHistoryService.query(any(CallHistoryQueryDTO.class), any(DataScope.class)))
                    .thenReturn(new PageImpl<>(List.of(entity)));

            ResponseEntity<RouterResponse<org.springframework.data.domain.Page<ApiCallHistoryEntity>>> response =
                    controller.query(new CallHistoryQueryDTO(), withCodes(PermissionCodes.CALLHISTORY_READ)).block();

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertNull(entity.getRequestBodyEncrypted());
            assertNull(entity.getResponseBodyEncrypted());
        }

        @Test
        @DisplayName("GET /recent 对无 full-content 主体清空密文")
        void recentRedactsBodies() {
            ApiCallHistoryEntity entity = record(11L, RecordLevel.FULL);
            when(callHistoryService.findRecent(eq(20), any(DataScope.class))).thenReturn(List.of(entity));

            controller.findRecent(20, withCodes(PermissionCodes.CALLHISTORY_READ)).block();

            assertNull(entity.getRequestBodyEncrypted());
            assertNull(entity.getResponseBodyEncrypted());
        }

        @Test
        @DisplayName("持 full-content 的列表不做裁剪")
        void listKeepsCiphertextForFullContent() {
            ApiCallHistoryEntity entity = record(12L, RecordLevel.FULL);
            when(callHistoryService.findRecent(eq(20), any(DataScope.class))).thenReturn(List.of(entity));

            controller.findRecent(20, admin()).block();

            assertEquals("encrypted-request", entity.getRequestBodyEncrypted());
            assertEquals("encrypted-response", entity.getResponseBodyEncrypted());
        }
    }

    // ==================== 属主校验测试（issue #698） ====================

    @Nested
    @DisplayName("POST /record - 写入属主校验（issue #698）")
    class OwnershipTests {

        private Authentication apiKeyOf(final String keyId) {
            return new org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication(
                    keyId, "sk-test", List.of("chat"));
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

            final ResponseEntity<RouterResponse<Void>> result = controller.record(record, admin());

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
        @DisplayName("获取统计信息 - 正常返回")
        void statisticsSuccess() {
            CallHistoryStatisticsDTO stats = CallHistoryStatisticsDTO.builder().build();
            when(callHistoryService.getStatistics(any(), any(), any())).thenReturn(stats);

            ResponseEntity<RouterResponse<CallHistoryStatisticsDTO>> response =
                    controller.getStatistics(null, null, admin()).block();

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
