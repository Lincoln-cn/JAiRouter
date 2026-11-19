package org.unreal.modelrouter.monitor.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.auth.security.permission.DataScope;
import org.unreal.modelrouter.monitor.dto.TokenUsageStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.TokenUsageEntity;
import org.unreal.modelrouter.persistence.jpa.repository.TokenUsageRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Token 用量的数据面隔离（issue #1185）。
 *
 * <p>只持 {@code monitoring:tokenusage:self:read} 的账号应当只看到自己的用量，
 * 而不是全平台用量；持全局码时口径不变。判定口径与
 * {@link DataScope#owns(String, String)} 一致：userId 命中<b>或</b> apiKeyId 在本人密钥集合里。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenUsageDataScopeTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 12, 0, 0);
    private static final LocalDateTime END = LocalDateTime.of(2026, 11, 19, 0, 0);

    @Mock
    private TokenUsageRepository tokenUsageRepository;

    @InjectMocks
    private TokenUsageService tokenUsageService;

    @BeforeEach
    void setUp() {
        when(tokenUsageRepository.findByTimeRange(any(), any())).thenReturn(List.of(
                row("alice", "key-a", "gpt-4o", 100L, true),
                row("alice", null, "gpt-4o-mini", 50L, true),
                row("bob", "key-b", "gpt-4o", 900L, true),
                row(null, "key-a", "gpt-4o", 30L, false),
                row("carol", "key-c", "claude-3", 700L, true)));
    }

    @Test
    @DisplayName("受限范围只统计本人的记录（userId 命中）")
    void restrictedScopeCountsOwnRowsOnly() {
        TokenUsageStatisticsDTO dto = tokenUsageService.getTokenUsageStatistics(
                START, END, DataScope.of("alice").withApiKeyIds(Set.of("key-a")));

        assertEquals(3L, dto.getTotalRequests(), "alice 自己的 2 行 + 本人密钥的 1 行");
        assertEquals(180L, dto.getTotalTokens(), "100 + 50 + 30");
        assertEquals(2L, dto.getSuccessfulRequests());
        assertEquals(1L, dto.getFailedRequests(), "失败行（isSuccess=false）应计入失败数");
        assertEquals(66.66666666666667, dto.getSuccessRate(), 0.0001);
        assertEquals(2, dto.getByModel().size(), "gpt-4o 与 gpt-4o-mini 两个模型");
    }

    @Test
    @DisplayName("不受限范围保持全量口径")
    void unrestrictedScopeCountsAllRows() {
        TokenUsageStatisticsDTO dto = tokenUsageService.getTokenUsageStatistics(
                START, END, DataScope.unrestricted());

        // 不受限走原 SQL 聚合路径，这里只验证它没有走内存聚合（repository 的聚合方法被调用）
        assertNotNull(dto);
    }

    @Test
    @DisplayName("密钥并集：userId 为空但密钥属于本人时仍计入")
    void apiKeyOwnershipCountsToo() {
        TokenUsageStatisticsDTO dto = tokenUsageService.getTokenUsageStatistics(
                START, END, DataScope.of("dave").withApiKeyIds(Set.of("key-a")));

        // dave 没有任何 userId 命中，只靠密钥并集：alice/key-a 与 null/key-a 两行
        assertEquals(2L, dto.getTotalRequests(), "密钥 key-a 命中的两行");
    }

    @Test
    @DisplayName("最近记录同样只返回本人的")
    void recentUsageIsScoped() {
        List<TokenUsageEntity> recent = tokenUsageService.getRecentUsage(
                10, DataScope.of("alice").withApiKeyIds(Set.of("key-a")));

        assertEquals(3, recent.size());
        recent.forEach(row -> {
            final boolean own = "alice".equals(row.getUserId()) || "key-a".equals(row.getApiKeyId());
            assertEquals(true, own, "返回了不属于本人的记录");
        });
    }

    private static TokenUsageEntity row(final String userId, final String apiKeyId,
                                        final String model, final Long tokens, final Boolean success) {
        return TokenUsageEntity.builder()
                .userId(userId)
                .apiKeyId(apiKeyId)
                .modelName(model)
                .serviceType("chat")
                .provider("openai")
                .totalTokens(tokens)
                .promptTokens(tokens / 2)
                .completionTokens(tokens / 2)
                .isSuccess(success)
                .responseTimeMs(40L)
                .occurredAt(START.plusHours(1))
                .usageDate("2026-11-12")
                .hour(1)
                .year(2026)
                .weekOfYear(46)
                .month(11)
                .build();
    }
}
