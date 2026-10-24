package org.unreal.modelrouter.router.ratelimit.enduser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.dto.ChatDTO;
import org.unreal.modelrouter.monitor.monitoring.collector.MetricsCollector;
import org.unreal.modelrouter.router.model.ModelServiceRegistry.ServiceType;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link EndUserRateLimitService} 的门面语义：什么时候不判定、什么时候拒绝、指标怎么打。
 *
 * <p>本层默认关闭，且**只对 API Key 调用方生效**：控制台 JWT 用户走的是管理面限流，
 * 不参与终端用户配额。</p>
 */
@DisplayName("终端用户限流门面：关闭即零副作用 / JWT 跳过 / 超限拒绝")
class EndUserRateLimitServiceTest {

    private static final String API_KEY_ID = "key-abc";

    @Test
    @DisplayName("未启用时返回空（不解析身份、不计数、不记指标）")
    void disabledYieldsEmpty() {
        final MetricsCollector metrics = mock(MetricsCollector.class);
        final EndUserRateLimitService service = new EndUserRateLimitService(
                properties(false, 5), new LocalEndUserRateLimitCounter(10), metrics);

        assertTrue(service.evaluate(API_KEY_ID, chat("alice"), "alice", ServiceType.chat, false).isEmpty());
        verifyNoInteractions(metrics);
    }

    @Test
    @DisplayName("控制台 JWT 调用方跳过本层")
    void jwtCallerSkipped() {
        final EndUserRateLimitService service = service(properties(true, 5));

        assertTrue(service.evaluate(API_KEY_ID, chat("alice"), "alice", ServiceType.chat, true).isEmpty());
    }

    @Test
    @DisplayName("没有 API Key ID（无法定位桶）时跳过")
    void blankApiKeySkipped() {
        final EndUserRateLimitService service = service(properties(true, 5));

        assertTrue(service.evaluate("  ", chat("alice"), "alice", ServiceType.chat, false).isEmpty());
        assertTrue(service.evaluate(null, chat("alice"), "alice", ServiceType.chat, false).isEmpty());
    }

    @Test
    @DisplayName("未覆盖的服务类型（tts / stt / rerank）跳过")
    void unsupportedServiceSkipped() {
        final EndUserRateLimitService service = service(properties(true, 5));

        assertTrue(service.evaluate(API_KEY_ID, chat("alice"), "alice", ServiceType.tts, false).isEmpty());
        assertTrue(service.evaluate(API_KEY_ID, chat("alice"), "alice", ServiceType.stt, false).isEmpty());
        assertTrue(service.evaluate(API_KEY_ID, chat("alice"), "alice", ServiceType.rerank, false).isEmpty());
    }

    @Test
    @DisplayName("标识缺失且 missing-identity=skip 时放行")
    void missingIdentitySkipped() {
        final EndUserRateLimitService service = service(properties(true, 5));

        assertTrue(service.evaluate(API_KEY_ID, chat(null), null, ServiceType.chat, false).isEmpty(),
                "缺失即跳过：不该产生判定结果，也不该计数");
    }

    @Test
    @DisplayName("标识缺失且 missing-identity=reject 时拒绝")
    void missingIdentityRejected() {
        final EndUserRateLimitProperties properties = properties(true, 5);
        properties.setMissingIdentity(EndUserRateLimitProperties.MissingIdentity.REJECT);
        final EndUserRateLimitService service = service(properties);

        final Optional<EndUserRateLimitCounter.Decision> decision =
                service.evaluate(API_KEY_ID, chat(null), null, ServiceType.chat, false);

        assertTrue(decision.isPresent(), "reject 策略下缺失标识应当产生拒绝判定");
        assertFalse(decision.get().allowed());
        assertFalse(decision.get().degraded(), "这是策略拒绝，不是降级");
    }

    @Test
    @DisplayName("同一用户达到上限后被拒，不同用户互不影响")
    void rejectsAfterLimitPerUser() {
        final EndUserRateLimitService service = service(properties(true, 1));

        assertTrue(service.evaluate(API_KEY_ID, chat("alice"), null, ServiceType.chat, false)
                .orElseThrow().allowed(), "第 1 次放行");
        assertFalse(service.evaluate(API_KEY_ID, chat("alice"), null, ServiceType.chat, false)
                .orElseThrow().allowed(), "第 2 次应当被拒");
        assertTrue(service.evaluate(API_KEY_ID, chat("bob"), null, ServiceType.chat, false)
                .orElseThrow().allowed(), "另一个用户仍有自己的配额");
    }

    @Test
    @DisplayName("放行时按 service 与算法名记录指标")
    void recordsMetricsOnAllow() {
        final MetricsCollector metrics = mock(MetricsCollector.class);
        final EndUserRateLimitService service = new EndUserRateLimitService(
                properties(true, 5), new LocalEndUserRateLimitCounter(10), metrics);

        service.evaluate(API_KEY_ID, chat("alice"), null, ServiceType.chat, false);

        verify(metrics).recordRateLimit("chat", "end-user-fixed-window", true);
    }

    @Test
    @DisplayName("降级放行时算法名带 -degraded 后缀（便于告警区分）")
    void degradedMetricsCarrySuffix() {
        final MetricsCollector metrics = mock(MetricsCollector.class);
        final EndUserRateLimitCounter degraded = mock(EndUserRateLimitCounter.class);
        when(degraded.record(anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new EndUserRateLimitCounter.Decision(true, 0L, 5L, 30L, true));
        final EndUserRateLimitService service =
                new EndUserRateLimitService(properties(true, 5), degraded, metrics);

        service.evaluate(API_KEY_ID, chat("alice"), null, ServiceType.chat, false);

        verify(metrics).recordRateLimit("chat", "end-user-fixed-window-degraded", true);
    }

    private static EndUserRateLimitService service(final EndUserRateLimitProperties properties) {
        return new EndUserRateLimitService(properties, new LocalEndUserRateLimitCounter(100));
    }

    private static EndUserRateLimitProperties properties(final boolean enabled, final int limit) {
        final EndUserRateLimitProperties properties = new EndUserRateLimitProperties();
        properties.setEnabled(enabled);
        properties.setRequestsPerMinute(limit);
        return properties;
    }

    private static ChatDTO.Request chat(final String user) {
        return new ChatDTO.Request("gpt-test", List.of(), false, null, null, null, null,
                null, null, null, user, null);
    }
}
