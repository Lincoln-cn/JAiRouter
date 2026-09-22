package org.unreal.modelrouter.auth.controller;

import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.dto.ApiKeyUpdateRequest;
import org.unreal.modelrouter.auth.security.dto.ApiKeyVO;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.auth.security.service.ApiKeyQuotaService;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.auth.security.service.TokenBucketRateLimiter;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 配额管理接口在 WebFlux 链路上调用 block() 导致 100% 失败。
 *
 * <p>现象：{@code PUT /api/auth/api-keys/{keyId}/quota} 恒定返回
 * {@code HTTP 200 + success=false + errorCode=INTERNAL_ERROR}，消息为
 * “block()/blockFirst()/blockLast() are blocking, which is not supported in
 * thread parallel-N”。配额抽屉的「保存配额 / 重置配额」因此完全不可用。</p>
 *
 * <p>根因：{@link ApiKeyQuotaService#updateQuotaLimits} 内部对
 * {@code apiKeyService.updateApiKey(...)} 调用了 {@code block()}。虽然它给上游加了
 * {@code subscribeOn(boundedElastic())}，但 {@code block()} 阻塞的是**调用方线程**；
 * 控制器侧 {@code Mono.fromCallable(...)} 没有 subscribeOn，于是该 callable 直接在
 * 订阅线程上执行——实测为 Reactor 的 {@code parallel-*} 非阻塞线程，Reactor 禁止在此
 * block，直接抛 {@link IllegalStateException}。</p>
 *
 * <p>本用例把控制器的 Mono 显式订阅在 {@code Schedulers.parallel()} 上以稳定复现；
 * 修复后（把阻塞工作放到 boundedElastic）应当返回 success=true。</p>
 */
class ApiKeyManagementControllerQuotaBlockingTest {

    private static final String KEY_ID = "key-1";
    private static final String KEY_HASH = "hash-1";

    @Test
    void updateQuotaShouldSucceedWhenSubscribedOnReactorNonBlockingThread() {
        final ApiKeyService apiKeyService = mock(ApiKeyService.class);
        final TokenBucketRateLimiter rateLimiter = mock(TokenBucketRateLimiter.class);
        final ApiKeyQuotaService quotaService = new ApiKeyQuotaService(apiKeyService, rateLimiter);
        final ApiKeyManagementController controller =
                new ApiKeyManagementController(apiKeyService, quotaService);

        final ApiKey apiKey = ApiKey.builder()
                .keyId(KEY_ID)
                .keyHash(KEY_HASH)
                .keyPrefix("sk-")
                .enabled(true)
                .dailyRequestLimit(100L)
                .dailyTokenLimit(0L)
                .rateLimitPerMinute(0)
                .quotaAlertThreshold(0.8)
                .usage(UsageStatistics.builder()
                        .totalRequests(0L)
                        .successfulRequests(0L)
                        .failedRequests(0L)
                        .dailyUsage(new HashMap<>())
                        .dailyTokenUsage(new HashMap<>())
                        .build())
                .build();

        final Map<String, ApiKey> cache = new HashMap<>();
        cache.put(KEY_HASH, apiKey);
        final Map<String, String> index = new HashMap<>();
        index.put(KEY_ID, KEY_HASH);

        when(apiKeyService.getApiKeyCache()).thenReturn(cache);
        when(apiKeyService.getKeyIdIndex()).thenReturn(index);
        when(apiKeyService.updateApiKey(eq(KEY_ID), any())).thenReturn(Mono.just(new ApiKeyVO()));
        when(rateLimiter.getCurrentCount(KEY_ID)).thenReturn(0);

        final ApiKeyUpdateRequest request = new ApiKeyUpdateRequest();
        request.setDailyRequestLimit(500L);

        StepVerifier.create(controller.updateApiKeyQuota(KEY_ID, request)
                        .subscribeOn(Schedulers.parallel()))
                .assertNext(response -> assertTrue(response.isSuccess(),
                        "配额更新不应因在 Reactor 非阻塞线程上调用 block() 而失败: "
                                + response.getErrorCode() + " / " + response.getMessage()))
                .verifyComplete();
    }
}
