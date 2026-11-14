package org.unreal.modelrouter.common.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthContributors;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.unreal.modelrouter.config.health.HealthProbeProperties;

/**
 * {@code readinessDependencies} 聚合器的行为边界。
 *
 * <p>三条刻意的设计必须被守住：① 默认关闭时恒定 UP（不改变 3.8.6 之前的行为）；
 * ② 未装配的依赖跳过而不是判 DOWN（否则没配 Redis 的部署会被误判）；
 * ③ 贡献者抛异常时吞掉（探针不能成为故障源）。</p>
 */
@DisplayName("就绪探针依赖聚合器")
class ReadinessDependencyIndicatorTest {

    private static HealthIndicator indicator(String status) {
        HealthIndicator indicator = mock(HealthIndicator.class);
        when(indicator.health()).thenReturn(Health.status(status).build());
        return indicator;
    }

    private static HealthIndicator failingIndicator() {
        HealthIndicator indicator = mock(HealthIndicator.class);
        when(indicator.health()).thenThrow(new IllegalStateException("boom"));
        return indicator;
    }

    @SuppressWarnings("unchecked")
    private static HealthContributors.Entry entry(
            String name, HealthContributor contributor) {
        HealthContributors.Entry entry = mock(HealthContributors.Entry.class);
        when(entry.name()).thenReturn(name);
        when(entry.contributor()).thenReturn(contributor);
        return entry;
    }

    @SuppressWarnings("unchecked")
    private static ReadinessDependencyIndicator indicatorWith(
            boolean includeDependencies, HealthContributors.Entry... entries) {
        HealthContributorRegistry registry = mock(HealthContributorRegistry.class);
        when(registry.stream()).thenReturn(Stream.of(entries));

        ObjectProvider<HealthContributorRegistry> provider =
                mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(registry);

        HealthProbeProperties properties = new HealthProbeProperties();
        properties.getReadiness().setIncludeDependencies(includeDependencies);
        return new ReadinessDependencyIndicator(provider, properties);
    }

    @Test
    @DisplayName("默认关闭：恒定 UP 并标注 enabled=false")
    void disabled_isAlwaysUp() {
        ReadinessDependencyIndicator indicator =
                indicatorWith(false, entry("db", indicator(Status.DOWN.getCode())));

        Health health = indicator.health();

        assertEquals(Status.UP.getCode(), health.getStatus().getCode());
        assertEquals(false, health.getDetails().get("enabled"));
    }

    @Test
    @DisplayName("启用且 db/redis 都健康：UP，evaluated 列出两个依赖")
    void enabledWithAllHealthy_isUp() {
        ReadinessDependencyIndicator indicator = indicatorWith(true,
                entry("db", indicator(Status.UP.getCode())),
                entry("redis", indicator(Status.UP.getCode())));

        Health health = indicator.health();

        assertEquals(Status.UP.getCode(), health.getStatus().getCode());
        assertEquals(List.of("db", "redis"), health.getDetails().get("evaluated"));
        assertEquals(List.of(), health.getDetails().get("unhealthy"));
    }

    @Test
    @DisplayName("启用且 redis 不可用：DOWN，unhealthy 指出 redis")
    void enabledWithRedisDown_isDown() {
        ReadinessDependencyIndicator indicator = indicatorWith(true,
                entry("db", indicator(Status.UP.getCode())),
                entry("redis", indicator(Status.DOWN.getCode())));

        Health health = indicator.health();

        assertEquals(Status.DOWN.getCode(), health.getStatus().getCode());
        assertEquals(List.of("redis=DOWN"), health.getDetails().get("unhealthy"));
    }

    @Test
    @DisplayName("未装配的依赖跳过，不判 DOWN")
    void missingDependency_isSkipped() {
        ReadinessDependencyIndicator indicator = indicatorWith(true,
                entry("redis", indicator(Status.UP.getCode())));

        Health health = indicator.health();

        assertEquals(Status.UP.getCode(), health.getStatus().getCode());
        assertEquals(List.of("redis"), health.getDetails().get("evaluated"));
    }

    @Test
    @DisplayName("贡献者抛异常时吞掉，探针不成为故障源")
    void throwingContributor_isIgnored() {
        ReadinessDependencyIndicator indicator = indicatorWith(true,
                entry("db", failingIndicator()));

        Health health = indicator.health();

        assertEquals(Status.UP.getCode(), health.getStatus().getCode());
        assertEquals(List.of("db"), health.getDetails().get("evaluated"));
        assertTrue(((List<?>) health.getDetails().get("unhealthy")).isEmpty());
        assertFalse(health.getDetails().containsKey("error"));
    }
}
