package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ModelRouterProperties 单元测试
 */
@DisplayName("ModelRouterProperties 测试")
class ModelRouterPropertiesTest {

    @Nested
    @DisplayName("LoadBalanceConfig 测试")
    class LoadBalanceConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            LoadBalanceConfig config = new LoadBalanceConfig();

            assertEquals("random", config.getType());
            assertEquals("md5", config.getHashAlgorithm());
            assertEquals(150, config.getVirtualNodes());
        }

        @Test
        @DisplayName("测试Setter和Getter")
        void testSetterGetter() {
            LoadBalanceConfig config = new LoadBalanceConfig();

            config.setType("round-robin");
            config.setHashAlgorithm("sha256");
            config.setVirtualNodes(200);

            assertEquals("round-robin", config.getType());
            assertEquals("sha256", config.getHashAlgorithm());
            assertEquals(200, config.getVirtualNodes());
        }

        @Test
        @DisplayName("测试一致性哈希配置")
        void testConsistentHashConfig() {
            LoadBalanceConfig config = new LoadBalanceConfig();
            config.setType("consistent-hash");
            config.setVirtualNodes(300);

            assertEquals("consistent-hash", config.getType());
            assertEquals(300, config.getVirtualNodes());
        }
    }

    @Nested
    @DisplayName("RateLimitConfig 测试")
    class RateLimitConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            RateLimitConfig config = new RateLimitConfig();

            assertFalse(config.getEnabled());
            assertEquals("token-bucket", config.getAlgorithm());
            assertEquals(100L, config.getCapacity());
            assertEquals(10L, config.getRate());
            assertEquals("service", config.getScope());
        }

        @Test
        @DisplayName("测试启用限流配置")
        void testEnabledConfig() {
            RateLimitConfig config = new RateLimitConfig();
            config.setEnabled(true);
            config.setAlgorithm("sliding-window");
            config.setCapacity(200L);
            config.setRate(50L);
            config.setScope("instance");
            config.setClientIpEnable(true);

            assertTrue(config.getEnabled());
            assertEquals("sliding-window", config.getAlgorithm());
            assertEquals(200L, config.getCapacity());
            assertEquals(50L, config.getRate());
            assertEquals("instance", config.getScope());
            assertTrue(config.getClientIpEnable());
        }

        @Test
        @DisplayName("测试covertTo方法 - 禁用状态")
        void testCovertToDisabled() {
            RateLimitConfig config = new RateLimitConfig();
            config.setEnabled(false);

            var result = config.covertTo();
            assertNotNull(result);
        }

        @Test
        @DisplayName("测试covertTo方法 - 启用状态")
        void testCovertToEnabled() {
            RateLimitConfig config = new RateLimitConfig();
            config.setEnabled(true);
            config.setAlgorithm("token-bucket");
            config.setCapacity(100L);
            config.setRate(10L);
            config.setScope("service");
            config.setKey("test-key");

            var result = config.covertTo();
            assertNotNull(result);
            assertEquals("token-bucket", result.getAlgorithm());
            assertEquals(100L, result.getCapacity());
            assertEquals(10L, result.getRate());
        }
    }

    @Nested
    @DisplayName("CircuitBreakerConfig 测试")
    class CircuitBreakerConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            CircuitBreakerConfig config = new CircuitBreakerConfig();

            assertFalse(config.getEnabled());
            assertEquals(5, config.getFailureThreshold());
            assertEquals(60000L, config.getTimeout());
            assertEquals(2, config.getSuccessThreshold());
        }

        @Test
        @DisplayName("测试自定义配置")
        void testCustomConfig() {
            CircuitBreakerConfig config = new CircuitBreakerConfig();
            config.setEnabled(true);
            config.setFailureThreshold(10);
            config.setTimeout(30000L);
            config.setSuccessThreshold(5);

            assertTrue(config.getEnabled());
            assertEquals(10, config.getFailureThreshold());
            assertEquals(30000L, config.getTimeout());
            assertEquals(5, config.getSuccessThreshold());
        }
    }

    @Nested
    @DisplayName("FallbackConfig 测试")
    class FallbackConfigTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            FallbackConfig config = new FallbackConfig();

            assertFalse(config.getEnabled());
            assertEquals("default", config.getStrategy());
            assertEquals(100, config.getCacheSize());
            assertEquals(300000L, config.getCacheTtl());
        }

        @Test
        @DisplayName("测试缓存策略配置")
        void testCacheStrategy() {
            FallbackConfig config = new FallbackConfig();
            config.setEnabled(true);
            config.setStrategy("cache");
            config.setCacheSize(500);
            config.setCacheTtl(600000L);

            assertTrue(config.getEnabled());
            assertEquals("cache", config.getStrategy());
            assertEquals(500, config.getCacheSize());
            assertEquals(600000L, config.getCacheTtl());
        }
    }

    @Nested
    @DisplayName("ModelInstance 测试")
    class ModelInstanceTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            ModelInstance instance = new ModelInstance();

            assertEquals(1, instance.getWeight());
            assertEquals("active", instance.getStatus());
            assertTrue(instance.isHealthy());
        }

        @Test
        @DisplayName("测试Setter和Getter")
        void testSetterGetter() {
            ModelInstance instance = new ModelInstance();

            instance.setId("inst-123");
            instance.setName("test-instance");
            instance.setBaseUrl("http://localhost:8080");
            instance.setPath("/v1/chat");
            instance.setWeight(5);
            instance.setStatus("inactive");
            instance.setInstanceId("uuid-456");
            instance.setAdapter("ollama");
            instance.setHealthy(false);
            instance.setHeaders(Map.of("X-Custom", "value"));

            assertEquals("inst-123", instance.getId());
            assertEquals("test-instance", instance.getName());
            assertEquals("http://localhost:8080", instance.getBaseUrl());
            assertEquals("/v1/chat", instance.getPath());
            assertEquals(5, instance.getWeight());
            assertEquals("inactive", instance.getStatus());
            assertEquals("uuid-456", instance.getInstanceId());
            assertEquals("ollama", instance.getAdapter());
            assertFalse(instance.isHealthy());
            assertEquals("value", instance.getHeaders().get("X-Custom"));
        }

        @Test
        @DisplayName("测试getInstanceId自动生成")
        void testGetInstanceIdAutoGenerate() {
            ModelInstance instance = new ModelInstance();
            instance.setInstanceId(null);

            String instanceId = instance.getInstanceId();

            assertNotNull(instanceId);
            assertTrue(instanceId.length() > 0);
        }
    }

    @Nested
    @DisplayName("ServiceConfig 测试")
    class ServiceConfigTest {

        @Test
        @DisplayName("测试默认构造")
        void testDefaultConstructor() {
            ServiceConfig config = new ServiceConfig();

            assertNull(config.getLoadBalance());
            assertNull(config.getInstances());
            assertNull(config.getAdapter());
        }

        @Test
        @DisplayName("测试完整配置")
        void testFullConfig() {
            ServiceConfig config = new ServiceConfig();
            LoadBalanceConfig lbConfig = new LoadBalanceConfig();
            lbConfig.setType("round-robin");

            ModelInstance instance1 = new ModelInstance();
            instance1.setName("instance1");
            instance1.setBaseUrl("http://inst1:8080");

            ModelInstance instance2 = new ModelInstance();
            instance2.setName("instance2");
            instance2.setBaseUrl("http://inst2:8080");

            config.setLoadBalance(lbConfig);
            config.setInstances(List.of(instance1, instance2));
            config.setAdapter("vllm");
            config.setRateLimit(new RateLimitConfig());
            config.setCircuitBreaker(new CircuitBreakerConfig());
            config.setFallback(new FallbackConfig());

            assertEquals("round-robin", config.getLoadBalance().getType());
            assertEquals(2, config.getInstances().size());
            assertEquals("vllm", config.getAdapter());
            assertNotNull(config.getRateLimit());
            assertNotNull(config.getCircuitBreaker());
            assertNotNull(config.getFallback());
        }
    }

    @Nested
    @DisplayName("ModelRouterProperties 测试")
    class MainPropertiesTest {

        @Test
        @DisplayName("测试默认值")
        void testDefaultValues() {
            ModelRouterProperties properties = new ModelRouterProperties();

            assertEquals("normal", properties.getAdapter());
            assertNotNull(properties.getLoadBalance());
            assertNotNull(properties.getRateLimit());
            assertNotNull(properties.getCircuitBreaker());
            assertNotNull(properties.getFallback());
        }

        @Test
        @DisplayName("测试完整配置")
        void testFullConfig() {
            ModelRouterProperties properties = new ModelRouterProperties();

            LoadBalanceConfig lbConfig = new LoadBalanceConfig();
            lbConfig.setType("weighted");

            ServiceConfig chatService = new ServiceConfig();
            chatService.setAdapter("openai");

            ServiceConfig embeddingService = new ServiceConfig();
            embeddingService.setAdapter("ollama");

            properties.setLoadBalance(lbConfig);
            properties.setAdapter("advanced");
            properties.setServices(Map.of(
                    "chat", chatService,
                    "embedding", embeddingService
            ));

            assertEquals("weighted", properties.getLoadBalance().getType());
            assertEquals("advanced", properties.getAdapter());
            assertEquals(2, properties.getServices().size());
            assertEquals("openai", properties.getServices().get("chat").getAdapter());
            assertEquals("ollama", properties.getServices().get("embedding").getAdapter());
        }
    }
}
