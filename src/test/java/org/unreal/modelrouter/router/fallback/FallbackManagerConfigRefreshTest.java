package org.unreal.modelrouter.router.fallback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.fallback.impl.CacheFallbackStrategy;
import org.unreal.modelrouter.router.fallback.impl.DefaultFallbackStrategy;
import org.unreal.modelrouter.router.factory.ComponentFactory;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #873：降级策略要跟着配置变。
 *
 * <p>修改前策略按 serviceType 缓存在 {@code FallbackManager} 里，配置热更新后不重建，
 * 控制台把 {@code fallback.strategy} 从 default 改成 cache（或改 cache-size / cache-ttl）后，
 * 运行中的实例仍按旧策略处理，直到进程重启。</p>
 */
@DisplayName("#873 降级策略按配置指纹重建")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FallbackManagerConfigRefreshTest {

    @Mock
    private ComponentFactory componentFactory;

    private FallbackManager manager;
    private ModelRouterProperties.ServiceConfig serviceConfig;
    private ModelRouterProperties.FallbackConfig fallbackConfig;

    @BeforeEach
    void setUp() {
        manager = new FallbackManager(componentFactory);

        fallbackConfig = new ModelRouterProperties.FallbackConfig();
        fallbackConfig.setEnabled(true);
        fallbackConfig.setStrategy("default");
        fallbackConfig.setCacheSize(10);
        fallbackConfig.setCacheTtl(1000L);

        serviceConfig = new ModelRouterProperties.ServiceConfig();
        serviceConfig.setFallback(fallbackConfig);

        when(componentFactory.createFallbackStrategy(any(), eq("chat"))).thenAnswer(invocation -> {
            final ModelRouterProperties.FallbackConfig config = invocation.getArgument(0);
            if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
                return null;
            }
            return "cache".equalsIgnoreCase(config.getStrategy())
                    ? new CacheFallbackStrategy("chat", config.getCacheSize(), config.getCacheTtl())
                    : new DefaultFallbackStrategy("chat");
        });
    }

    @Test
    @DisplayName("873-001 配置没变时复用同一个策略实例")
    void unchangedConfigReusesStrategy() {
        final FallbackStrategy<ResponseEntity<?>> first = manager.getFallbackStrategy("chat", serviceConfig);
        final FallbackStrategy<ResponseEntity<?>> second = manager.getFallbackStrategy("chat", serviceConfig);

        assertNotNull(first, "enabled=true 时应创建策略");
        assertSame(first, second, "配置没变时应复用缓存实例");
        verify(componentFactory, times(1)).createFallbackStrategy(any(), eq("chat"));
    }

    @Test
    @DisplayName("873-002 strategy 改了要重建（default → cache）")
    void strategyChangeRebuilds() {
        final FallbackStrategy<ResponseEntity<?>> before = manager.getFallbackStrategy("chat", serviceConfig);
        assertInstanceOf(DefaultFallbackStrategy.class, before, "初始策略应为 default");

        fallbackConfig.setStrategy("cache");
        final FallbackStrategy<ResponseEntity<?>> after = manager.getFallbackStrategy("chat", serviceConfig);

        assertInstanceOf(CacheFallbackStrategy.class, after,
                "配置改成 cache 之后必须换策略实例（修改前会一直沿用它缓存里的旧实例）");
        verify(componentFactory, times(2)).createFallbackStrategy(any(), eq("chat"));
    }

    @Test
    @DisplayName("873-003 cache-ttl 改了也要重建（影响缓存行为）")
    void cacheTtlChangeRebuilds() {
        final FallbackStrategy<ResponseEntity<?>> before = manager.getFallbackStrategy("chat", serviceConfig);
        fallbackConfig.setStrategy("cache");
        final FallbackStrategy<ResponseEntity<?>> cachedStrategy = manager.getFallbackStrategy("chat", serviceConfig);
        assertInstanceOf(CacheFallbackStrategy.class, cachedStrategy, "先切到 cache 策略");
        assertEquals(1000L, ((CacheFallbackStrategy) cachedStrategy).getTtlMillis(), "初始 TTL 来自配置");

        fallbackConfig.setCacheTtl(600_000L);
        final FallbackStrategy<ResponseEntity<?>> rebuilt = manager.getFallbackStrategy("chat", serviceConfig);
        assertInstanceOf(CacheFallbackStrategy.class, rebuilt, "TTL 变化后仍是 cache 策略，但应是新实例");
        assertEquals(600_000L, ((CacheFallbackStrategy) rebuilt).getTtlMillis(),
                "重建后的策略必须带上新的 cache-ttl");
    }

    @Test
    @DisplayName("873-004 关掉降级后不再返回策略，缓存也被清掉")
    void disablingFallbackDropsCachedStrategy() {
        assertNotNull(manager.getFallbackStrategy("chat", serviceConfig), "初始应创建策略");
        assertEquals(1, manager.getFallbackStrategyCount(), "缓存里应有一条");

        fallbackConfig.setEnabled(false);
        assertNull(manager.getFallbackStrategy("chat", serviceConfig), "关掉降级后不应返回策略");
        assertEquals(0, manager.getFallbackStrategyCount(), "关掉后缓存条目要被清掉");
    }
}
