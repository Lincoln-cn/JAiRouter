package org.unreal.modelrouter.router.ratelimit.enduser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EndUserRateLimitProperties} 的默认值与钳制边界。
 *
 * <p>钳制而不是 {@code @Validated} 校验：配置写坏了不该让网关起不来，
 * 而应退到一个安全值（与 {@code QuotaProperties} 的取向一致）。</p>
 */
@DisplayName("终端用户限流配置：默认关闭 + 取值钳制")
class EndUserRateLimitPropertiesTest {

    @Test
    @DisplayName("默认值：关闭、60/min、读请求头、缺失即跳过、50000 缓存、16 位摘要")
    void defaultsAreSafe() {
        final EndUserRateLimitProperties properties = new EndUserRateLimitProperties();

        assertFalse(properties.isEnabled(), "默认必须关闭，否则升级即改变行为");
        assertEquals(EndUserRateLimitProperties.DEFAULT_REQUESTS_PER_MINUTE,
                properties.getRequestsPerMinute(), "默认每分钟上限");
        assertEquals(60, properties.effectiveRequestsPerMinute(), "生效值应与默认值一致");
        assertTrue(properties.isHeaderEnabled(), "默认应读取 X-End-User-Id");
        assertEquals(EndUserRateLimitProperties.MissingIdentity.SKIP,
                properties.getMissingIdentity(), "默认缺失即跳过（灰度期不误伤）");
        assertEquals(EndUserRateLimitProperties.DEFAULT_LOCAL_CACHE_SIZE,
                properties.getLocalCacheSize(), "默认本地缓存条目上限");
        assertEquals(EndUserRateLimitProperties.DEFAULT_FINGERPRINT_LENGTH,
                properties.getFingerprintLength(), "默认摘要长度");
    }

    @Test
    @DisplayName("每分钟上限被钳制在 1 ~ 1000000（0/负数按 1，过大的数按上界）")
    void requestsPerMinuteIsClamped() {
        final EndUserRateLimitProperties properties = new EndUserRateLimitProperties();

        properties.setRequestsPerMinute(0);
        assertEquals(1, properties.effectiveRequestsPerMinute(), "0 会把用户永久拒绝，按 1 处理");

        properties.setRequestsPerMinute(-5);
        assertEquals(1, properties.effectiveRequestsPerMinute(), "负数同样按 1 处理");

        properties.setRequestsPerMinute(2_000_000);
        assertEquals(1_000_000, properties.effectiveRequestsPerMinute(), "上界");

        properties.setRequestsPerMinute(120);
        assertEquals(120, properties.effectiveRequestsPerMinute(), "区间内的值原样生效");
    }

    @Test
    @DisplayName("缓存条目上限与摘要长度也被钳制")
    void localCacheAndFingerprintAreClamped() {
        final EndUserRateLimitProperties properties = new EndUserRateLimitProperties();

        properties.setLocalCacheSize(0);
        assertEquals(1, properties.effectiveLocalCacheSize(), "缓存上限至少为 1");

        properties.setLocalCacheSize(1_500_000);
        assertEquals(1_000_000, properties.effectiveLocalCacheSize(), "缓存上限上界");

        properties.setFingerprintLength(1);
        assertEquals(8, properties.effectiveFingerprintLength(), "摘要长度下界（短摘要容易碰撞）");

        properties.setFingerprintLength(100);
        assertEquals(32, properties.effectiveFingerprintLength(), "摘要长度上界");

        properties.setFingerprintLength(24);
        assertEquals(24, properties.effectiveFingerprintLength(), "区间内的值原样生效");
    }
}
