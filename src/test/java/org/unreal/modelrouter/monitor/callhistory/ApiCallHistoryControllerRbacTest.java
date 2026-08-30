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
import org.unreal.modelrouter.common.controller.response.RouterResponse;
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
                    "FULL_CONTENT_ACCESS".equals(event.getEventType())
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
