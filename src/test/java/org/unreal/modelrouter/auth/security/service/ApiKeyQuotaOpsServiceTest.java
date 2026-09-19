package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.dto.ApiKeyUpdateRequest;
import org.unreal.modelrouter.auth.security.dto.ApiKeyVO;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * v3.2.1: API Key 配额便捷操作 — 专用更新 / 批量重置 / 剩余量
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeyQuotaOpsServiceTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Mock
    private TokenBucketRateLimiter rateLimiter;

    private ApiKeyQuotaService quotaService;

    @BeforeEach
    void setUp() {
        quotaService = new ApiKeyQuotaService(apiKeyService, rateLimiter);
        final ApiKey key = ApiKey.builder()
                .keyId("key-1")
                .keyHash("hash-1")
                .description("test")
                .dailyRequestLimit(100)
                .dailyTokenLimit(1000)
                .rateLimitPerMinute(60)
                .quotaAlertThreshold(0.8)
                .usage(new org.unreal.modelrouter.auth.security.model.UsageStatistics())
                .build();
        if (key.getUsage().getDailyUsage() == null) {
            key.getUsage().setDailyUsage(new java.util.HashMap<>());
        }
        if (key.getUsage().getDailyTokenUsage() == null) {
            key.getUsage().setDailyTokenUsage(new java.util.HashMap<>());
        }
        key.getUsage().getDailyUsage().put(
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE), 30L);
        key.getUsage().getDailyTokenUsage().put(
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE), 200L);

        when(apiKeyService.getApiKeyCache()).thenReturn(Map.of("hash-1", key));
        when(apiKeyService.getKeyIdIndex()).thenReturn(Map.of("key-1", "hash-1"));
        when(apiKeyService.updateApiKey(anyString(), any())).thenAnswer(inv -> {
            final ApiKeyUpdateRequest req = inv.getArgument(1);
            if (req.getDailyRequestLimit() != null) {
                key.setDailyRequestLimit(req.getDailyRequestLimit());
            }
            if (req.getDailyTokenLimit() != null) {
                key.setDailyTokenLimit(req.getDailyTokenLimit());
            }
            if (req.getRateLimitPerMinute() != null) {
                key.setRateLimitPerMinute(req.getRateLimitPerMinute());
            }
            if (req.getQuotaAlertThreshold() != null) {
                key.setQuotaAlertThreshold(req.getQuotaAlertThreshold());
            }
            return Mono.just(ApiKeyVO.builder().keyId("key-1").build());
        });
        when(apiKeyService.resetDailyQuota(anyString())).thenReturn(Mono.empty());
        when(rateLimiter.getCurrentCount(anyString())).thenReturn(0);
    }

    @Test
    @DisplayName("updateQuotaLimits: 仅更新配额字段并返回刷新后的详情")
    void updateQuotaLimits_shouldPatchQuotaFields() {
        final ApiKeyUpdateRequest req = ApiKeyUpdateRequest.builder()
                .dailyRequestLimit(500L)
                .dailyTokenLimit(50000L)
                .rateLimitPerMinute(120)
                .quotaAlertThreshold(0.9)
                .build();

        final ApiKeyQuotaService.QuotaUsageDetail detail = quotaService.updateQuotaLimits("key-1", req);

        assertEquals(500L, detail.getDailyRequestLimit());
        assertEquals(50000L, detail.getDailyTokenLimit());
        assertEquals(120, detail.getRateLimitPerMinute());
        assertEquals(0.9, detail.getQuotaAlertThreshold());
        assertEquals(30L, detail.getTodayRequestCount());
        verify(apiKeyService).updateApiKey(eq("key-1"), any(ApiKeyUpdateRequest.class));
    }

    @Test
    @DisplayName("updateQuotaLimits: 未知 Key 抛出异常")
    void updateQuotaLimits_unknownKey_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> quotaService.updateQuotaLimits("missing", ApiKeyUpdateRequest.builder().dailyRequestLimit(1L).build()));
    }

    @Test
    @DisplayName("getQuotaUsage: 计算剩余量（0=不限时 -1）")
    void getQuotaUsage_calculatesRemaining() {
        final var detail = quotaService.getQuotaUsage("key-1").orElseThrow();
        assertEquals(70L, detail.getRemainingRequests());
        assertEquals(800L, detail.getRemainingTokens());

        // 不限制
        final ApiKey unlimited = ApiKey.builder().keyId("k0").keyHash("h0").build();
        when(apiKeyService.getKeyIdIndex()).thenReturn(Map.of("key-1", "hash-1", "k0", "h0"));
        when(apiKeyService.getApiKeyCache()).thenReturn(Map.of("hash-1", unlimited, "h0", unlimited));
        // rebuild map properly
        final ApiKey key1 = ApiKey.builder().keyId("key-1").keyHash("hash-1")
                .dailyRequestLimit(0).dailyTokenLimit(0).build();
        when(apiKeyService.getApiKeyCache()).thenReturn(Map.of("hash-1", key1));
        when(apiKeyService.getKeyIdIndex()).thenReturn(Map.of("key-1", "hash-1"));
        final var unlimitedDetail = quotaService.getQuotaUsage("key-1").orElseThrow();
        assertEquals(-1L, unlimitedDetail.getRemainingRequests());
        assertEquals(-1L, unlimitedDetail.getRemainingTokens());
    }

    @Test
    @DisplayName("resetDailyQuotas: 批量重置并返回成功数")
    void resetDailyQuotas_batch() {
        final int n = quotaService.resetDailyQuotas(List.of("key-1", "missing"));
        assertEquals(1, n);
        verify(apiKeyService, times(1)).resetDailyQuota("key-1");
        verify(apiKeyService, never()).resetDailyQuota("missing");
        verify(rateLimiter, atLeastOnce()).reset("key-1");
    }

    @Test
    @DisplayName("resetAllDailyQuotas: 调用既有全量重置")
    void resetAllDailyQuotas_delegates() {
        doNothing().when(rateLimiter).resetAll();
        quotaService.resetAllDailyQuotas();
        verify(apiKeyService, atLeastOnce()).resetDailyQuota("key-1");
        verify(rateLimiter).resetAll();
    }
}
