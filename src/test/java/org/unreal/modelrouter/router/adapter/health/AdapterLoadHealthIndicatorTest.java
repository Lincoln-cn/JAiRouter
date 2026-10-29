package org.unreal.modelrouter.router.adapter.health;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.mock.env.MockEnvironment;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #778: 适配器加载失败要能从 /actuator/health 读到。
 */
class AdapterLoadHealthIndicatorTest {

    private static MockEnvironment environment(final String failHealth) {
        final MockEnvironment environment = new MockEnvironment();
        if (failHealth != null) {
            environment.setProperty("jairouter.router.adapter.health.fail-health", failHealth);
        }
        return environment;
    }

    @Test
    @DisplayName("没有加载失败时 UP，且失败数为 0")
    void upWhenNoFailures() {
        final AdapterRegistry registry = Mockito.mock(AdapterRegistry.class);
        Mockito.when(registry.getLoadFailures()).thenReturn(Map.of());
        final Health health = new AdapterLoadHealthIndicator(registry, environment(null)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("failedAdapterCount", 0);
    }

    @Test
    @DisplayName("有加载失败时默认仍为 UP，但详情里列出适配器与原因")
    void upWithDetailsWhenFailuresExist() {
        final AdapterRegistry registry = Mockito.mock(AdapterRegistry.class);
        Mockito.when(registry.getLoadFailures())
                .thenReturn(Map.of("broken", "YAML: NullPointerException"));
        final Health health = new AdapterLoadHealthIndicator(registry, environment(null)).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("failedAdapterCount", 1);
        assertThat(health.getDetails()).containsKey("failedAdapters");
    }

    @Test
    @DisplayName("显式设置 fail-health=true 时降级为 DOWN")
    void downWhenFailHealthEnabled() {
        final AdapterRegistry registry = Mockito.mock(AdapterRegistry.class);
        Mockito.when(registry.getLoadFailures())
                .thenReturn(Map.of("broken", "YAML: NullPointerException"));
        final Health health = new AdapterLoadHealthIndicator(registry, environment("true")).health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    }
}
