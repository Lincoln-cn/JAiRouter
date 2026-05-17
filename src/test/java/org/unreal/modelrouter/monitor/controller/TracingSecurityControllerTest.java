package org.unreal.modelrouter.monitor.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.tracing.encryption.TracingEncryptionService;
import org.unreal.modelrouter.monitor.tracing.sanitization.TracingSanitizationService;
import org.unreal.modelrouter.monitor.tracing.security.TracingSecurityManager;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TracingSecurityController 单元测试
 */
@ExtendWith(MockitoExtension.class)
class TracingSecurityControllerTest {

    @Mock
    private TracingSanitizationService tracingSanitizationService;

    @Mock
    private TracingSecurityManager tracingSecurityManager;

    @Mock
    private TracingEncryptionService tracingEncryptionService;

    @InjectMocks
    private TracingSecurityController controller;

    // ========================================
    // 脱敏规则管理测试
    // ========================================

    @Nested
    @DisplayName("获取追踪敏感字段列表测试")
    class GetTracingSensitiveFieldsTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            Set<String> fields = new HashSet<>(List.of("password", "token", "apiKey"));
            when(tracingSanitizationService.getTracingSensitiveFields()).thenReturn(fields);

            ResponseEntity<RouterResponse<Set<String>>> response = controller.getTracingSensitiveFields();

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(3, response.getBody().getData().size());
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            when(tracingSanitizationService.getTracingSensitiveFields())
                    .thenThrow(new RuntimeException("Test error"));

            ResponseEntity<RouterResponse<Set<String>>> response = controller.getTracingSensitiveFields();

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }

    @Nested
    @DisplayName("添加追踪敏感字段测试")
    class AddTracingSensitiveFieldTests {

        @Test
        @DisplayName("添加成功")
        void addSuccess() {
            doNothing().when(tracingSanitizationService).addTracingSensitiveField("newField");

            ResponseEntity<RouterResponse<Void>> response = controller.addTracingSensitiveField("newField");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(tracingSanitizationService).addTracingSensitiveField("newField");
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            doThrow(new RuntimeException("Test error"))
                    .when(tracingSanitizationService).addTracingSensitiveField(any());

            ResponseEntity<RouterResponse<Void>> response = controller.addTracingSensitiveField("newField");

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }

    @Nested
    @DisplayName("移除追踪敏感字段测试")
    class RemoveTracingSensitiveFieldTests {

        @Test
        @DisplayName("移除成功")
        void removeSuccess() {
            doNothing().when(tracingSanitizationService).removeTracingSensitiveField("field");

            ResponseEntity<RouterResponse<Void>> response = controller.removeTracingSensitiveField("field");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(tracingSanitizationService).removeTracingSensitiveField("field");
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            doThrow(new RuntimeException("Test error"))
                    .when(tracingSanitizationService).removeTracingSensitiveField(any());

            ResponseEntity<RouterResponse<Void>> response = controller.removeTracingSensitiveField("field");

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }

    // ========================================
    // 访问权限管理测试
    // ========================================

    @Nested
    @DisplayName("获取用户追踪访问历史测试")
    class GetUserTraceAccessHistoryTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            List<TracingSecurityManager.TraceAccessRecord> history = List.of();
            when(tracingSecurityManager.getUserTraceAccessHistory("user1")).thenReturn(history);

            ResponseEntity<RouterResponse<List<TracingSecurityManager.TraceAccessRecord>>> response =
                    controller.getUserTraceAccessHistory("user1");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            when(tracingSecurityManager.getUserTraceAccessHistory(any()))
                    .thenThrow(new RuntimeException("Test error"));

            ResponseEntity<RouterResponse<List<TracingSecurityManager.TraceAccessRecord>>> response =
                    controller.getUserTraceAccessHistory("user1");

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }

    @Nested
    @DisplayName("清理用户权限缓存测试")
    class ClearUserPermissionCacheTests {

        @Test
        @DisplayName("清理成功")
        void clearSuccess() {
            doNothing().when(tracingSecurityManager).clearUserPermissionCache("user1");

            ResponseEntity<RouterResponse<Void>> response = controller.clearUserPermissionCache("user1");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(tracingSecurityManager).clearUserPermissionCache("user1");
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            doThrow(new RuntimeException("Test error"))
                    .when(tracingSecurityManager).clearUserPermissionCache(any());

            ResponseEntity<RouterResponse<Void>> response = controller.clearUserPermissionCache("user1");

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }

    @Nested
    @DisplayName("清理所有权限缓存测试")
    class ClearAllPermissionCacheTests {

        @Test
        @DisplayName("清理成功")
        void clearSuccess() {
            doNothing().when(tracingSecurityManager).clearAllPermissionCache();

            ResponseEntity<RouterResponse<Void>> response = controller.clearAllPermissionCache();

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            verify(tracingSecurityManager).clearAllPermissionCache();
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            doThrow(new RuntimeException("Test error"))
                    .when(tracingSecurityManager).clearAllPermissionCache();

            ResponseEntity<RouterResponse<Void>> response = controller.clearAllPermissionCache();

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }

    // ========================================
    // 加密密钥管理测试
    // ========================================

    @Nested
    @DisplayName("轮换加密密钥测试")
    class RotateEncryptionKeyTests {

        @Test
        @DisplayName("轮换成功")
        void rotateSuccess() {
            when(tracingEncryptionService.rotateEncryptionKey("trace-1")).thenReturn(Mono.just(true));

            ResponseEntity<RouterResponse<Boolean>> response = controller.rotateEncryptionKey("trace-1");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertTrue(response.getBody().getData());
        }

        @Test
        @DisplayName("轮换失败")
        void rotateFailed() {
            when(tracingEncryptionService.rotateEncryptionKey("trace-1")).thenReturn(Mono.just(false));

            ResponseEntity<RouterResponse<Boolean>> response = controller.rotateEncryptionKey("trace-1");

            assertNotNull(response.getBody());
            assertFalse(response.getBody().isSuccess());
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            when(tracingEncryptionService.rotateEncryptionKey(any()))
                    .thenThrow(new RuntimeException("Test error"));

            ResponseEntity<RouterResponse<Boolean>> response = controller.rotateEncryptionKey("trace-1");

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }

    @Nested
    @DisplayName("清理过期数据测试")
    class CleanupExpiredDataTests {

        @Test
        @DisplayName("清理成功")
        void cleanupSuccess() {
            when(tracingEncryptionService.cleanupExpiredData()).thenReturn(Mono.just(10));

            ResponseEntity<RouterResponse<Integer>> response = controller.cleanupExpiredData();

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertEquals(10, response.getBody().getData());
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            when(tracingEncryptionService.cleanupExpiredData())
                    .thenThrow(new RuntimeException("Test error"));

            ResponseEntity<RouterResponse<Integer>> response = controller.cleanupExpiredData();

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }

    @Nested
    @DisplayName("安全清理追踪数据测试")
    class SecureCleanupTraceDataTests {

        @Test
        @DisplayName("清理成功")
        void cleanupSuccess() {
            when(tracingEncryptionService.secureCleanupTraceData("trace-1")).thenReturn(Mono.empty());

            ResponseEntity<RouterResponse<Void>> response = controller.secureCleanupTraceData("trace-1");

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            when(tracingEncryptionService.secureCleanupTraceData(any()))
                    .thenThrow(new RuntimeException("Test error"));

            ResponseEntity<RouterResponse<Void>> response = controller.secureCleanupTraceData("trace-1");

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }

    // ========================================
    // 安全状态概览测试
    // ========================================

    @Nested
    @DisplayName("获取追踪安全状态概览测试")
    class GetSecurityOverviewTests {

        @Test
        @DisplayName("获取成功")
        void getSuccess() {
            when(tracingSanitizationService.getTracingSensitiveFields())
                    .thenReturn(new HashSet<>(List.of("password", "token")));

            ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.getSecurityOverview();

            assertNotNull(response.getBody());
            assertTrue(response.getBody().isSuccess());
            assertNotNull(response.getBody().getData());

            Map<String, Object> overview = response.getBody().getData();
            assertTrue(overview.containsKey("sanitization"));
            assertTrue(overview.containsKey("accessControl"));
            assertTrue(overview.containsKey("encryption"));
            assertTrue(overview.containsKey("audit"));
        }

        @Test
        @DisplayName("异常处理")
        void handleException() {
            when(tracingSanitizationService.getTracingSensitiveFields())
                    .thenThrow(new RuntimeException("Test error"));

            ResponseEntity<RouterResponse<Map<String, Object>>> response = controller.getSecurityOverview();

            assertEquals(500, response.getStatusCode().value());
            assertFalse(response.getBody().isSuccess());
        }
    }
}
