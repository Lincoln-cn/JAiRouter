package org.unreal.modelrouter.config.core.helper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.ratelimit.RateLimitConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ConfigValidatorHelper 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("ConfigValidatorHelper 测试")
class ConfigValidatorHelperTest {

    private ConfigValidatorHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ConfigValidatorHelper();
    }

    @Nested
    @DisplayName("限流算法验证测试")
    class RateLimitAlgorithmTests {

        @Test
        @DisplayName("VAL-157: 限流算法 - 有效算法token-bucket")
        void testValidAlgorithmTokenBucket() {
            assertTrue(helper.isValidRateLimitAlgorithm("token-bucket"));
        }

        @Test
        @DisplayName("VAL-158: 限流算法 - 有效算法leaky-bucket")
        void testValidAlgorithmLeakyBucket() {
            assertTrue(helper.isValidRateLimitAlgorithm("leaky-bucket"));
        }

        @Test
        @DisplayName("VAL-159: 限流算法 - 有效算法sliding-window")
        void testValidAlgorithmSlidingWindow() {
            assertTrue(helper.isValidRateLimitAlgorithm("sliding-window"));
        }

        @Test
        @DisplayName("VAL-160: 限流算法 - 无效算法")
        void testInvalidAlgorithm() {
            assertFalse(helper.isValidRateLimitAlgorithm("invalid"));
        }

        @Test
        @DisplayName("VAL-161: 限流算法 - null算法")
        void testNullAlgorithm() {
            assertFalse(helper.isValidRateLimitAlgorithm(null));
        }

        @Test
        @DisplayName("VAL-162: 限流算法 - 大小写不敏感")
        void testAlgorithmCaseInsensitive() {
            assertTrue(helper.isValidRateLimitAlgorithm("TOKEN-BUCKET"));
            assertTrue(helper.isValidRateLimitAlgorithm("Token-Bucket"));
        }
    }

    @Nested
    @DisplayName("限流作用域验证测试")
    class RateLimitScopeTests {

        @Test
        @DisplayName("VAL-163: 限流作用域 - 有效作用域service")
        void testValidScopeService() {
            assertTrue(helper.isValidRateLimitScope("service"));
        }

        @Test
        @DisplayName("VAL-164: 限流作用域 - 有效作用域model")
        void testValidScopeModel() {
            assertTrue(helper.isValidRateLimitScope("model"));
        }

        @Test
        @DisplayName("VAL-165: 限流作用域 - 有效作用域client-ip")
        void testValidScopeClientIp() {
            assertTrue(helper.isValidRateLimitScope("client-ip"));
        }

        @Test
        @DisplayName("VAL-166: 限流作用域 - 无效作用域")
        void testInvalidScope() {
            assertFalse(helper.isValidRateLimitScope("invalid"));
        }

        @Test
        @DisplayName("VAL-167: 限流作用域 - null作用域")
        void testNullScope() {
            assertFalse(helper.isValidRateLimitScope(null));
        }
    }

    @Nested
    @DisplayName("负载均衡类型验证测试")
    class LoadBalanceTypeTests {

        @Test
        @DisplayName("VAL-168: 负载均衡类型 - 有效类型round-robin")
        void testValidTypeRoundRobin() {
            assertTrue(helper.isValidLoadBalanceType("round-robin"));
        }

        @Test
        @DisplayName("VAL-169: 负载均衡类型 - 有效类型random")
        void testValidTypeRandom() {
            assertTrue(helper.isValidLoadBalanceType("random"));
        }

        @Test
        @DisplayName("VAL-170: 负载均衡类型 - 有效类型least-connections")
        void testValidTypeLeastConnections() {
            assertTrue(helper.isValidLoadBalanceType("least-connections"));
        }

        @Test
        @DisplayName("VAL-171: 负载均衡类型 - 有效类型ip-hash")
        void testValidTypeIpHash() {
            assertTrue(helper.isValidLoadBalanceType("ip-hash"));
        }

        @Test
        @DisplayName("VAL-172: 负载均衡类型 - 无效类型")
        void testInvalidType() {
            assertFalse(helper.isValidLoadBalanceType("invalid"));
        }

        @Test
        @DisplayName("VAL-173: 负载均衡类型 - null类型")
        void testNullType() {
            assertFalse(helper.isValidLoadBalanceType(null));
        }
    }

    @Nested
    @DisplayName("哈希算法验证测试")
    class HashAlgorithmTests {

        @Test
        @DisplayName("VAL-174: 哈希算法 - 有效算法md5")
        void testValidAlgorithmMd5() {
            assertTrue(helper.isValidHashAlgorithm("md5"));
        }

        @Test
        @DisplayName("VAL-175: 哈希算法 - 有效算法sha1")
        void testValidAlgorithmSha1() {
            assertTrue(helper.isValidHashAlgorithm("sha1"));
        }

        @Test
        @DisplayName("VAL-176: 哈希算法 - 有效算法sha256")
        void testValidAlgorithmSha256() {
            assertTrue(helper.isValidHashAlgorithm("sha256"));
        }

        @Test
        @DisplayName("VAL-177: 哈希算法 - 无效算法")
        void testInvalidAlgorithm() {
            assertFalse(helper.isValidHashAlgorithm("invalid"));
        }

        @Test
        @DisplayName("VAL-178: 哈希算法 - null算法")
        void testNullAlgorithm() {
            assertFalse(helper.isValidHashAlgorithm(null));
        }
    }

    @Nested
    @DisplayName("服务地址验证测试")
    class ServiceAddressTests {

        @Test
        @DisplayName("VAL-179: 服务地址 - 有效IP地址")
        void testValidIpAddress() {
            assertTrue(helper.validateServiceAddress("192.168.1.1:8080"));
        }

        @Test
        @DisplayName("VAL-180: 服务地址 - 有效域名")
        void testValidDomain() {
            assertTrue(helper.validateServiceAddress("example.com:8080"));
        }

        @Test
        @DisplayName("VAL-181: 服务地址 - 带http前缀")
        void testValidHttpPrefix() {
            assertTrue(helper.validateServiceAddress("http://example.com:8080"));
        }

        @Test
        @DisplayName("VAL-182: 服务地址 - 带https前缀")
        void testValidHttpsPrefix() {
            assertTrue(helper.validateServiceAddress("https://example.com:8080"));
        }

        @Test
        @DisplayName("VAL-183: 服务地址 - null地址")
        void testNullAddress() {
            assertFalse(helper.validateServiceAddress(null));
        }

        @Test
        @DisplayName("VAL-184: 服务地址 - 空地址")
        void testEmptyAddress() {
            assertFalse(helper.validateServiceAddress(""));
        }

        @Test
        @DisplayName("VAL-185: 服务地址 - 无效端口")
        void testInvalidPort() {
            assertFalse(helper.validateServiceAddress("example.com:99999"));
        }
    }

    @Nested
    @DisplayName("限流配置验证测试")
    class RateLimitConfigTests {

        @Test
        @DisplayName("VAL-186: 限流配置 - 有效配置")
        void testValidRateLimitConfig() {
            ModelRouterProperties.RateLimitConfig config = mock(ModelRouterProperties.RateLimitConfig.class);
            when(config.getEnabled()).thenReturn(true);
            when(config.getAlgorithm()).thenReturn("token-bucket");
            when(config.getCapacity()).thenReturn(100L);
            when(config.getRate()).thenReturn(10L);
            when(config.getScope()).thenReturn("service");
            when(config.getClientIpEnable()).thenReturn(false);

            assertTrue(helper.validateRateLimitConfig(config));
        }

        @Test
        @DisplayName("VAL-187: 限流配置 - null配置")
        void testNullRateLimitConfig() {
            assertFalse(helper.validateRateLimitConfig((ModelRouterProperties.RateLimitConfig) null));
        }

        @Test
        @DisplayName("VAL-188: 限流配置 - 未启用配置")
        void testDisabledRateLimitConfig() {
            ModelRouterProperties.RateLimitConfig config = mock(ModelRouterProperties.RateLimitConfig.class);
            when(config.getEnabled()).thenReturn(false);

            assertTrue(helper.validateRateLimitConfig(config));
        }

        @Test
        @DisplayName("VAL-189: 限流配置 - 无效算法")
        void testInvalidAlgorithmConfig() {
            ModelRouterProperties.RateLimitConfig config = mock(ModelRouterProperties.RateLimitConfig.class);
            when(config.getEnabled()).thenReturn(true);
            when(config.getAlgorithm()).thenReturn("invalid");

            assertFalse(helper.validateRateLimitConfig(config));
        }

        @Test
        @DisplayName("VAL-190: 限流配置 - 无效容量")
        void testInvalidCapacityConfig() {
            ModelRouterProperties.RateLimitConfig config = mock(ModelRouterProperties.RateLimitConfig.class);
            when(config.getEnabled()).thenReturn(true);
            when(config.getAlgorithm()).thenReturn("token-bucket");
            when(config.getCapacity()).thenReturn(0L);

            assertFalse(helper.validateRateLimitConfig(config));
        }
    }

    @Nested
    @DisplayName("负载均衡配置验证测试")
    class LoadBalanceConfigTests {

        @Test
        @DisplayName("VAL-191: 负载均衡配置 - 有效配置")
        void testValidLoadBalanceConfig() {
            ModelRouterProperties.LoadBalanceConfig config = mock(ModelRouterProperties.LoadBalanceConfig.class);
            when(config.getType()).thenReturn("round-robin");

            assertTrue(helper.validateLoadBalanceConfig(config));
        }

        @Test
        @DisplayName("VAL-192: 负载均衡配置 - null配置")
        void testNullLoadBalanceConfig() {
            assertFalse(helper.validateLoadBalanceConfig(null));
        }

        @Test
        @DisplayName("VAL-193: 负载均衡配置 - 无效类型")
        void testInvalidLoadBalanceConfig() {
            ModelRouterProperties.LoadBalanceConfig config = mock(ModelRouterProperties.LoadBalanceConfig.class);
            when(config.getType()).thenReturn("invalid");

            assertFalse(helper.validateLoadBalanceConfig(config));
        }

        @Test
        @DisplayName("VAL-194: 负载均衡配置 - IP哈希需要哈希算法")
        void testIpHashRequiresHashAlgorithm() {
            ModelRouterProperties.LoadBalanceConfig config = mock(ModelRouterProperties.LoadBalanceConfig.class);
            when(config.getType()).thenReturn("ip-hash");
            when(config.getHashAlgorithm()).thenReturn("md5");

            assertTrue(helper.validateLoadBalanceConfig(config));
        }

        @Test
        @DisplayName("VAL-195: 负载均衡配置 - IP哈希无哈希算法")
        void testIpHashWithoutHashAlgorithm() {
            ModelRouterProperties.LoadBalanceConfig config = mock(ModelRouterProperties.LoadBalanceConfig.class);
            when(config.getType()).thenReturn("ip-hash");
            when(config.getHashAlgorithm()).thenReturn(null);

            assertFalse(helper.validateLoadBalanceConfig(config));
        }
    }

    @Nested
    @DisplayName("限流配置转换测试")
    class ConvertRateLimitConfigTests {

        @Test
        @DisplayName("VAL-196: 限流配置转换 - null配置")
        void testConvertNullConfig() {
            assertNull(helper.convertRateLimitConfig(null));
        }

        @Test
        @DisplayName("VAL-197: 限流配置转换 - 有效配置")
        void testConvertValidConfig() {
            ModelRouterProperties.RateLimitConfig config = mock(ModelRouterProperties.RateLimitConfig.class);
            when(config.getAlgorithm()).thenReturn("token-bucket");
            when(config.getCapacity()).thenReturn(100L);
            when(config.getRate()).thenReturn(10L);
            when(config.getScope()).thenReturn("service");
            when(config.getKey()).thenReturn("test-key");

            RateLimitConfig result = helper.convertRateLimitConfig(config);

            assertNotNull(result);
            assertEquals("token-bucket", result.getAlgorithm());
            assertEquals(100L, result.getCapacity());
            assertEquals(10L, result.getRate());
            assertEquals("service", result.getScope());
            assertEquals("test-key", result.getKey());
        }
    }

    @Nested
    @DisplayName("Map格式限流配置验证测试")
    class MapRateLimitConfigTests {

        @Test
        @DisplayName("VAL-198: Map限流配置 - 非Map对象")
        void testNonMapRateLimitConfig() {
            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            helper.validateRateLimitConfig("not a map", "test", errors, warnings);

            assertTrue(errors.stream().anyMatch(e -> e.contains("格式错误")));
        }

        @Test
        @DisplayName("VAL-199: Map限流配置 - 未启用配置")
        void testDisabledMapRateLimitConfig() {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", false);

            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            helper.validateRateLimitConfig(config, "test", errors, warnings);

            assertTrue(errors.isEmpty());
        }

        @Test
        @DisplayName("VAL-200: Map限流配置 - 无效算法")
        void testInvalidAlgorithmMapConfig() {
            Map<String, Object> config = new HashMap<>();
            config.put("enabled", true);
            config.put("algorithm", "invalid");

            List<String> errors = new ArrayList<>();
            List<String> warnings = new ArrayList<>();

            helper.validateRateLimitConfig(config, "test", errors, warnings);

            assertTrue(errors.stream().anyMatch(e -> e.contains("算法无效")));
        }
    }
}
