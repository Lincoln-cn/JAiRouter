package org.unreal.modelrouter.persistence.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.unreal.modelrouter.auth.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.common.controller.response.RouterResponse;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;
import org.unreal.modelrouter.monitor.dto.TokenUsageStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.TokenUsageEntity;
import org.unreal.modelrouter.monitor.service.TokenUsageService;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * TokenUsageController 单元测试
 * 
 * <p>测试 Token 使用量统计功能</p>
 *
 * @version v2.7.6
 * @since 2025-01-17
 */
@DisplayName("TokenUsageController RESTful 接口测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenUsageControllerTest {

    @Mock
    private TokenUsageService tokenUsageService;

    @InjectMocks
    private TokenUsageController controller;

    private TokenUsageRecordDTO testRecord;
    private TokenUsageEntity testEntity;
    private TokenUsageStatisticsDTO testStats;

    @BeforeEach
    void setUp() {
        testRecord = new TokenUsageRecordDTO();
        testRecord.setModelName("gpt-4");
        testRecord.setServiceType("chat");
        testRecord.setPromptTokens(100L);
        testRecord.setCompletionTokens(200L);
        testRecord.setTotalTokens(300L);

        testEntity = new TokenUsageEntity();
        testEntity.setId(1L);
        testEntity.setModelName("gpt-4");
        testEntity.setServiceType("chat");

        testStats = new TokenUsageStatisticsDTO();
        testStats.setTotalRequests(10L);
        testStats.setTotalPromptTokens(1000L);
        testStats.setTotalCompletionTokens(2000L);
        testStats.setTotalTokens(3000L);
        testStats.setStartTime(LocalDateTime.now().minusDays(7));
        testStats.setEndTime(LocalDateTime.now());
    }

    // ==================== 记录使用量测试 ====================

    @Nested
    @DisplayName("POST /api/token-usage/record - 记录使用量测试")
    class RecordTokenUsageTests {

        @Test
        @DisplayName("TOKEN-001: 成功记录Token使用量")
        void testRecordTokenUsage_success() {
            // Given
            doNothing().when(tokenUsageService).recordTokenUsage(any());

            // When
            ResponseEntity<RouterResponse<Void>> result = controller.recordTokenUsage(testRecord, null);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            verify(tokenUsageService).recordTokenUsage(testRecord);
        }
    }

    // ==================== 批量记录测试 ====================

    @Nested
    @DisplayName("POST /api/token-usage/record/batch - 批量记录测试")
    class RecordTokenUsageBatchTests {

        @Test
        @DisplayName("TOKEN-002: 成功批量记录")
        void testRecordTokenUsageBatch_success() {
            // Given
            doNothing().when(tokenUsageService).recordTokenUsageBatch(anyList());

            // When
            ResponseEntity<RouterResponse<Void>> result = controller.recordTokenUsageBatch(List.of(testRecord), null);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
        }
    }

    // ==================== 写入属主校验测试（issue #254） ====================

    @Nested
    @DisplayName("POST /api/token-usage/record[/batch] - 属主校验（issue #254）")
    class OwnershipTests {

        private Authentication apiKeyOf(final String keyId) {
            return new ApiKeyAuthentication(keyId, "sk-test", List.of("chat"));
        }

        private Authentication jwtPrincipal() {
            return new UsernamePasswordAuthenticationToken(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        }

        @Test
        @DisplayName("API Key 主体上报他人 apiKeyId：403 且不落库")
        void apiKeyClaimingOtherKeyId_isRejected() {
            testRecord.setApiKeyId("other-key");

            final ResponseEntity<RouterResponse<Void>> result =
                    controller.recordTokenUsage(testRecord, apiKeyOf("my-key"));

            assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
            assertFalse(result.getBody().isSuccess());
            verify(tokenUsageService, never()).recordTokenUsage(any());
        }

        @Test
        @DisplayName("API Key 主体上报自身 apiKeyId：放行")
        void apiKeyClaimingOwnKeyId_isAccepted() {
            testRecord.setApiKeyId("my-key");

            final ResponseEntity<RouterResponse<Void>> result =
                    controller.recordTokenUsage(testRecord, apiKeyOf("my-key"));

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(tokenUsageService).recordTokenUsage(testRecord);
        }

        @Test
        @DisplayName("API Key 主体未声明 apiKeyId：放行（无从矛盾）")
        void apiKeyWithoutClaimedKeyId_isAccepted() {
            testRecord.setApiKeyId(null);

            final ResponseEntity<RouterResponse<Void>> result =
                    controller.recordTokenUsage(testRecord, apiKeyOf("my-key"));

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(tokenUsageService).recordTokenUsage(testRecord);
        }

        @Test
        @DisplayName("非 API Key 主体（JWT）声明他人 apiKeyId：保持既有行为（放行）")
        void jwtPrincipalClaimingOtherKeyId_isAcceptedForNow() {
            testRecord.setApiKeyId("other-key");

            final ResponseEntity<RouterResponse<Void>> result =
                    controller.recordTokenUsage(testRecord, jwtPrincipal());

            assertEquals(HttpStatus.OK, result.getStatusCode(),
                    "管理面代报属未定义的调用形态，本次刻意不收紧");
            verify(tokenUsageService).recordTokenUsage(testRecord);
        }

        @Test
        @DisplayName("批量写入中任一条声明他人 apiKeyId：整批 403 且不落库")
        void batchWithAnyForeignKeyId_isRejectedEntirely() {
            final TokenUsageRecordDTO own = new TokenUsageRecordDTO();
            own.setApiKeyId("my-key");
            final TokenUsageRecordDTO foreign = new TokenUsageRecordDTO();
            foreign.setApiKeyId("other-key");

            final ResponseEntity<RouterResponse<Void>> result =
                    controller.recordTokenUsageBatch(List.of(own, foreign), apiKeyOf("my-key"));

            assertEquals(HttpStatus.FORBIDDEN, result.getStatusCode());
            verify(tokenUsageService, never()).recordTokenUsageBatch(anyList());
        }

        @Test
        @DisplayName("批量写入全部为自身 apiKeyId：放行")
        void batchWithOwnKeyIds_isAccepted() {
            final TokenUsageRecordDTO own = new TokenUsageRecordDTO();
            own.setApiKeyId("my-key");

            final ResponseEntity<RouterResponse<Void>> result =
                    controller.recordTokenUsageBatch(List.of(own), apiKeyOf("my-key"));

            assertEquals(HttpStatus.OK, result.getStatusCode());
            verify(tokenUsageService).recordTokenUsageBatch(anyList());
        }
    }

    // ==================== 获取统计信息测试 ====================

    @Nested
    @DisplayName("GET /api/token-usage/statistics - 获取统计信息测试")
    class GetTokenUsageStatisticsTests {

        @Test
        @DisplayName("TOKEN-003: 成功获取统计信息")
        void testGetTokenUsageStatistics_success() {
            // Given
            when(tokenUsageService.getTokenUsageStatistics(any(), any())).thenReturn(testStats);

            // When
            ResponseEntity<RouterResponse<TokenUsageStatisticsDTO>> result = controller.getTokenUsageStatistics(null, null);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertTrue(result.getBody().isSuccess());
            assertEquals(10L, result.getBody().getData().getTotalRequests());
        }
    }

    // ==================== 获取最近使用记录测试 ====================

    @Nested
    @DisplayName("GET /api/token-usage/recent - 获取最近使用记录测试")
    class GetRecentUsageTests {

        @Test
        @DisplayName("TOKEN-004: 成功获取最近使用记录")
        void testGetRecentUsage_success() {
            // Given
            when(tokenUsageService.getRecentUsage(20)).thenReturn(List.of(testEntity));

            // When
            ResponseEntity<RouterResponse<List<TokenUsageEntity>>> result = controller.getRecentUsage(20);

            // Then
            assertEquals(HttpStatus.OK, result.getStatusCode());
            assertEquals(1, result.getBody().getData().size());
        }
    }
}
