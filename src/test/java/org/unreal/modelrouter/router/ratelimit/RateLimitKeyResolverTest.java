package org.unreal.modelrouter.router.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.ratelimit.RateLimitKeyResolver.Outcome;
import org.unreal.modelrouter.router.ratelimit.RateLimitKeyResolver.Resolution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RateLimitKeyResolver} 单元测试（#161）。
 *
 * <p>穷举「维度 × 取值是否可用」的全部组合。这些组合是限流最容易出错的地方：错误地判定为
 * 可用会让不同客户端共用配额，错误地判定为不可用会让限流静默失效。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.4
 */
class RateLimitKeyResolverTest {

    private static final String IP = "203.0.113.7";
    private static final String KEY = "key-abc123";

    // ---------- CLIENT_IP 维度 ----------

    @Test
    @DisplayName("IP 维度：有 IP 时直接使用")
    void clientIpDimensionResolvesIp() {
        Resolution r = RateLimitKeyResolver.resolve(RateLimitKeyDimension.CLIENT_IP, IP, KEY);
        assertEquals(Outcome.RESOLVED, r.outcome());
        assertEquals(IP, r.key());
        assertTrue(r.available());
        assertFalse(r.usedFallback());
    }

    @Test
    @DisplayName("IP 维度：即使有 API Key，缺 IP 仍判不可用（不跨维度取值）")
    void clientIpDimensionDoesNotBorrowApiKey() {
        Resolution r = RateLimitKeyResolver.resolve(RateLimitKeyDimension.CLIENT_IP, null, KEY);
        assertEquals(Outcome.UNAVAILABLE, r.outcome());
        assertNull(r.key());
        assertFalse(r.available());
    }

    // ---------- API_KEY 维度 ----------

    @Test
    @DisplayName("API Key 维度：有 Key 时直接使用")
    void apiKeyDimensionResolvesKey() {
        Resolution r = RateLimitKeyResolver.resolve(RateLimitKeyDimension.API_KEY, IP, KEY);
        assertEquals(Outcome.RESOLVED, r.outcome());
        assertEquals(KEY, r.key());
        assertFalse(r.usedFallback());
    }

    @Test
    @DisplayName("API Key 维度：缺 Key 时回退 IP，且标记为需要告警的回退")
    void apiKeyDimensionFallsBackToIpAndWarns() {
        Resolution r = RateLimitKeyResolver.resolve(RateLimitKeyDimension.API_KEY, IP, null);
        assertEquals(Outcome.FALLBACK_TO_CLIENT_IP, r.outcome());
        assertEquals(IP, r.key(), "回退后限流仍须生效，不能放行");
        assertTrue(r.available());
        assertTrue(r.unexpectedFallback(), "API Key 维度缺 Key 属于配置与流量不匹配，应告警");
    }

    @Test
    @DisplayName("API Key 维度：Key 与 IP 都缺时判不可用")
    void apiKeyDimensionUnavailableWithoutEither() {
        Resolution r = RateLimitKeyResolver.resolve(RateLimitKeyDimension.API_KEY, null, null);
        assertEquals(Outcome.UNAVAILABLE, r.outcome());
        assertFalse(r.available());
    }

    // ---------- TENANT 维度 ----------

    @Test
    @DisplayName("租户维度：有 Key 时按 Key 归组")
    void tenantDimensionPrefersApiKey() {
        Resolution r = RateLimitKeyResolver.resolve(RateLimitKeyDimension.TENANT, IP, KEY);
        assertEquals(Outcome.RESOLVED, r.outcome());
        assertEquals(KEY, r.key());
    }

    @Test
    @DisplayName("租户维度：缺 Key 时回退 IP，但属于设计行为不告警")
    void tenantDimensionFallbackIsExpected() {
        Resolution r = RateLimitKeyResolver.resolve(RateLimitKeyDimension.TENANT, IP, null);
        assertEquals(Outcome.FALLBACK_TO_CLIENT_IP, r.outcome());
        assertEquals(IP, r.key());
        assertTrue(r.available());
        assertFalse(r.unexpectedFallback(), "租户维度的回退是预期行为");
    }

    @Test
    @DisplayName("租户维度：Key 与 IP 都缺时判不可用")
    void tenantDimensionUnavailableWithoutEither() {
        Resolution r = RateLimitKeyResolver.resolve(RateLimitKeyDimension.TENANT, null, null);
        assertEquals(Outcome.UNAVAILABLE, r.outcome());
        assertFalse(r.available());
    }

    // ---------- 边界 ----------

    @Test
    @DisplayName("维度为 null 时按客户端 IP 处理")
    void nullDimensionBehavesAsClientIp() {
        Resolution r = RateLimitKeyResolver.resolve(null, IP, KEY);
        assertEquals(RateLimitKeyDimension.CLIENT_IP, r.dimension());
        assertEquals(Outcome.RESOLVED, r.outcome());
        assertEquals(IP, r.key());
    }

    @Test
    @DisplayName("纯空白取值视为缺失，不得当作有效键值")
    void blankValuesAreTreatedAsMissing() {
        Resolution r = RateLimitKeyResolver.resolve(RateLimitKeyDimension.API_KEY, "   ", "  ");
        assertEquals(Outcome.UNAVAILABLE, r.outcome());
        assertNull(r.key());
    }

    @Test
    @DisplayName("解析结果始终携带生效维度，便于调用方按维度定级日志")
    void resolutionCarriesEffectiveDimension() {
        assertEquals(RateLimitKeyDimension.API_KEY,
                RateLimitKeyResolver.resolve(RateLimitKeyDimension.API_KEY, IP, KEY).dimension());
        assertEquals(RateLimitKeyDimension.TENANT,
                RateLimitKeyResolver.resolve(RateLimitKeyDimension.TENANT, IP, null).dimension());
    }
}
