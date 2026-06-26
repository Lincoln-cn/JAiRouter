package org.unreal.modelrouter.router.fallback;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.unreal.modelrouter.router.factory.ComponentFactory;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * FallbackManager 单元测试
 *
 * 测试内容：
 * 1. 初始化测试
 * 2. 获取降级策略测试
 * 3. 执行降级测试
 * 4. 清除策略缓存测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FallbackManager 测试")
class FallbackManagerTest {

    @Mock
    private ComponentFactory componentFactory;

    @Mock
    private FallbackStrategy<ResponseEntity<?>> fallbackStrategy;

    private FallbackManager fallbackManager;
    private ModelRouterProperties properties;

    @BeforeEach
    void setUp() {
        fallbackManager = new FallbackManager(componentFactory);
        properties = new ModelRouterProperties();
    }

    @Test
    @DisplayName("测试 1: 初始化 FallbackManager")
    void testInitialize() {
        ModelRouterProperties.FallbackConfig fallbackConfig = new ModelRouterProperties.FallbackConfig();
        fallbackConfig.setEnabled(true);
        properties.setFallback(fallbackConfig);

        fallbackManager.initialize(properties);

        assertNotNull(fallbackManager.getDefaultFallbackConfig());
        assertTrue(fallbackManager.getDefaultFallbackConfig().getEnabled());
    }

    @Test
    @DisplayName("测试 2: 初始化时无配置使用默认值")
    void testInitializeWithNullConfig() {
        fallbackManager.initialize(properties);

        assertNotNull(fallbackManager.getDefaultFallbackConfig());
    }

    @Test
    @DisplayName("测试 3: 获取降级策略")
    void testGetFallbackStrategy() {
        // 准备
        when(componentFactory.createFallbackStrategy(any(), eq("chat")))
                .thenReturn(fallbackStrategy);

        // 执行
        FallbackStrategy<ResponseEntity<?>> strategy = fallbackManager.getFallbackStrategy("chat", null);

        // 验证
        assertNotNull(strategy);
        verify(componentFactory, times(1)).createFallbackStrategy(any(), eq("chat"));
    }

    @Test
    @DisplayName("测试 4: 获取降级策略使用缓存")
    void testGetFallbackStrategyUsesCache() {
        // 准备
        when(componentFactory.createFallbackStrategy(any(), eq("chat")))
                .thenReturn(fallbackStrategy);

        // 第一次获取
        fallbackManager.getFallbackStrategy("chat", null);

        // 第二次获取应该使用缓存
        fallbackManager.getFallbackStrategy("chat", null);

        // 只调用一次组件工厂
        verify(componentFactory, times(1)).createFallbackStrategy(any(), eq("chat"));
    }

    @Test
    @DisplayName("测试 5: 执行降级处理")
    @SuppressWarnings("unchecked")
    void testFallback() {
        // 准备
        when(componentFactory.createFallbackStrategy(any(), eq("chat")))
                .thenReturn(fallbackStrategy);
        ResponseEntity<ResponseEntity<?>> expectedResponse = (ResponseEntity<ResponseEntity<?>>) (ResponseEntity<?>) ResponseEntity.status(503).body("Service degraded");
        doReturn(expectedResponse).when(fallbackStrategy).fallback(any(Exception.class));

        // 执行
        ResponseEntity<?> response = fallbackManager.fallback("chat", null, new RuntimeException("Test error"));

        // 验证
        assertNotNull(response);
        assertEquals(503, response.getStatusCodeValue());
        verify(fallbackStrategy, times(1)).fallback(any(Exception.class));
    }

    @Test
    @DisplayName("测试 6: 无降级策略时返回 null")
    void testFallbackWithNoStrategy() {
        when(componentFactory.createFallbackStrategy(any(), eq("chat")))
                .thenReturn(null);

        ResponseEntity<?> response = fallbackManager.fallback("chat", null, new RuntimeException("Test error"));

        assertNull(response);
    }

    @Test
    @DisplayName("测试 7: 清除指定服务的降级策略")
    void testClearFallbackStrategy() {
        // 准备
        when(componentFactory.createFallbackStrategy(any(), eq("chat")))
                .thenReturn(fallbackStrategy);

        // 获取策略
        fallbackManager.getFallbackStrategy("chat", null);
        assertEquals(1, fallbackManager.getFallbackStrategyCount());

        // 清除策略
        fallbackManager.clearFallbackStrategy("chat");

        assertEquals(0, fallbackManager.getFallbackStrategyCount());
    }

    @Test
    @DisplayName("测试 8: 清除所有降级策略")
    void testClearAllFallbackStrategies() {
        // 准备
        when(componentFactory.createFallbackStrategy(any(), any()))
                .thenReturn(fallbackStrategy);

        // 获取多个策略
        fallbackManager.getFallbackStrategy("chat", null);
        fallbackManager.getFallbackStrategy("embedding", null);

        assertEquals(2, fallbackManager.getFallbackStrategyCount());

        // 清除所有
        fallbackManager.clearAllFallbackStrategies();

        assertEquals(0, fallbackManager.getFallbackStrategyCount());
    }

    @Test
    @DisplayName("测试 9: 获取默认配置")
    void testGetDefaultFallbackConfig() {
        ModelRouterProperties.FallbackConfig config = new ModelRouterProperties.FallbackConfig();
        config.setEnabled(true);
        config.setCacheSize(100);
        properties.setFallback(config);

        fallbackManager.initialize(properties);

        ModelRouterProperties.FallbackConfig defaultConfig = fallbackManager.getDefaultFallbackConfig();
        assertNotNull(defaultConfig);
        assertTrue(defaultConfig.getEnabled());
    }

    @Test
    @DisplayName("测试 10: 无配置时返回新默认配置")
    void testGetDefaultFallbackConfigWhenNull() {
        ModelRouterProperties.FallbackConfig defaultConfig = fallbackManager.getDefaultFallbackConfig();
        assertNotNull(defaultConfig);
        assertFalse(defaultConfig.getEnabled());
    }

    @Test
    @DisplayName("测试 11: 服务级别配置覆盖全局配置")
    void testServiceLevelConfigOverride() {
        // 设置全局配置
        ModelRouterProperties.FallbackConfig globalConfig = new ModelRouterProperties.FallbackConfig();
        globalConfig.setEnabled(false);
        properties.setFallback(globalConfig);
        fallbackManager.initialize(properties);

        // 设置服务级别配置
        ModelRouterProperties.ServiceConfig serviceConfig = new ModelRouterProperties.ServiceConfig();
        ModelRouterProperties.FallbackConfig serviceFallbackConfig = new ModelRouterProperties.FallbackConfig();
        serviceFallbackConfig.setEnabled(true);
        serviceConfig.setFallback(serviceFallbackConfig);

        // 准备 mock
        when(componentFactory.createFallbackStrategy(any(), eq("chat")))
                .thenReturn(fallbackStrategy);

        // 执行
        fallbackManager.getFallbackStrategy("chat", serviceConfig);

        // 验证使用了服务级别配置
        verify(componentFactory).createFallbackStrategy(eq(serviceFallbackConfig), eq("chat"));
    }

    @Test
    @DisplayName("测试 12: 降级策略执行异常返回 null")
    void testFallbackExecutionException() {
        when(componentFactory.createFallbackStrategy(any(), eq("chat")))
                .thenReturn(fallbackStrategy);
        when(fallbackStrategy.fallback(any())).thenThrow(new RuntimeException("Strategy error"));

        ResponseEntity<?> response = fallbackManager.fallback("chat", null, new RuntimeException("Test error"));

        assertNull(response);
    }
}
