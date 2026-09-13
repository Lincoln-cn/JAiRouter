package org.unreal.modelrouter.auth.security.quota;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分布式计数配置测试（v3.1 PR-3）。
 *
 * <p>关闭开关时的默认值必须与 PR-1/PR-2 完全一致（零行为变更），因此逐个断言默认值与钳制规则。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("QuotaProperties 分布式计数配置测试")
class QuotaDistributedPropertiesTest {

    @Test
    @DisplayName("默认关闭：distributed.enabled=false / prefix=jairouter:quota / 50ms 超时 / 降级到本地")
    void defaults_shouldBeDisabledAndConservative() {
        final QuotaProperties properties = new QuotaProperties();

        assertFalse(properties.distributedEnabled(), "默认必须关闭，保证零行为变更");
        assertEquals(QuotaProperties.DEFAULT_DISTRIBUTED_KEY_PREFIX, properties.distributedKeyPrefix());
        assertEquals(Duration.ofMillis(50L), properties.distributedTimeout());
        assertTrue(properties.distributedDegradeToLocal());
        assertEquals("jairouter:quota", QuotaProperties.DEFAULT_DISTRIBUTED_KEY_PREFIX);
    }

    @Test
    @DisplayName("key 前缀：可配置，空白配置回落默认值")
    void keyPrefix_shouldFallBackToDefault() {
        final QuotaProperties properties = new QuotaProperties();
        properties.getDistributed().setKeyPrefix("custom:quota");
        assertEquals("custom:quota", properties.distributedKeyPrefix());

        properties.getDistributed().setKeyPrefix("");
        assertEquals(QuotaProperties.DEFAULT_DISTRIBUTED_KEY_PREFIX, properties.distributedKeyPrefix());
        properties.getDistributed().setKeyPrefix(null);
        assertEquals(QuotaProperties.DEFAULT_DISTRIBUTED_KEY_PREFIX, properties.distributedKeyPrefix());
        properties.setDistributed(null);
        assertEquals(QuotaProperties.DEFAULT_DISTRIBUTED_KEY_PREFIX, properties.distributedKeyPrefix());
        assertFalse(properties.distributedEnabled());
        assertTrue(properties.distributedDegradeToLocal());
    }

    @Test
    @DisplayName("超时：钳制在 1ms ~ 5s，非法值（0 / 负数 / null）回落默认 50ms")
    void timeout_shouldBeClamped() {
        final QuotaProperties properties = new QuotaProperties();

        properties.getDistributed().setTimeout(Duration.ofMillis(200L));
        assertEquals(Duration.ofMillis(200L), properties.distributedTimeout());

        properties.getDistributed().setTimeout(Duration.ofSeconds(30L));
        assertEquals(Duration.ofSeconds(5L), properties.distributedTimeout(), "上限 5s，避免拖死请求链路");

        properties.getDistributed().setTimeout(Duration.ofNanos(100L));
        assertEquals(Duration.ofMillis(1L), properties.distributedTimeout(), "下限 1ms，避免配置成必超时");

        properties.getDistributed().setTimeout(Duration.ZERO);
        assertEquals(QuotaProperties.DEFAULT_DISTRIBUTED_TIMEOUT, properties.distributedTimeout());
        properties.getDistributed().setTimeout(Duration.ofMillis(-5L));
        assertEquals(QuotaProperties.DEFAULT_DISTRIBUTED_TIMEOUT, properties.distributedTimeout());
        properties.getDistributed().setTimeout(null);
        assertEquals(QuotaProperties.DEFAULT_DISTRIBUTED_TIMEOUT, properties.distributedTimeout());
    }

    @Test
    @DisplayName("开关组合：distributed.enabled 与 degrade-to-local 互相独立")
    void switches_shouldBeIndependent() {
        final QuotaProperties properties = new QuotaProperties();

        properties.getDistributed().setEnabled(true);
        properties.getDistributed().setDegradeToLocal(false);

        assertTrue(properties.distributedEnabled());
        assertFalse(properties.distributedDegradeToLocal());
        assertFalse(properties.isEnabled(), "分布式开关不得隐式打开账本总开关");
    }
}
