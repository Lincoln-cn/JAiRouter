package org.unreal.modelrouter.router.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.factory.ComponentFactory;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RateLimitManager 实例级限流器 key 生成回退逻辑测试
 *
 * <p>v2.9.7: instanceId 缺失(如 YAML 实例未配置 instance-id)时,
 * generateInstanceKey 依次回退 name -> baseUrl,避免 key 冲突与监控 identifier 显示 "null"。
 *
 * @author JAiRouter Team
 * @since 2.9.7
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitManager generateInstanceKey 回退逻辑")
class RateLimitManagerInstanceKeyTest {

    @Mock
    private ComponentFactory componentFactory;

    @Mock
    private ServiceTypeResolver serviceTypeResolver;

    @Mock
    private ConfigConverterHelper configConverterHelper;

    private ModelRouterProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ModelRouterProperties();
        when(serviceTypeResolver.parseServiceType("chat")).thenReturn(ModelServiceRegistry.ServiceType.chat);
        RateLimiter mockLimiter = mock(RateLimiter.class);
        // getAllRateLimiterStatus()/getRateLimiterMetrics() 会调用 limiter.getConfig().getAlgorithm() 等,
        // 裸 mock 的 getConfig() 默认返回 null 会触发 NPE,须先配置非 null 的 RateLimitConfig
        when(mockLimiter.getConfig()).thenReturn(rateLimiterConfig());
        when(componentFactory.createScopedRateLimiter(any(RateLimitConfig.class)))
                .thenReturn(mockLimiter);
    }

    /** 构造与 RateLimitManager.convert() 输出一致的非 null 限流器配置 */
    private RateLimitConfig rateLimiterConfig() {
        RateLimitConfig cfg = new RateLimitConfig();
        cfg.setEnabled(true);
        cfg.setAlgorithm("token-bucket");
        cfg.setCapacity(50L);
        cfg.setRate(5L);
        cfg.setScope("instance");
        return cfg;
    }

    /** 构造一个带实例级限流(启用)的 chat 服务并构建 RateLimitManager */
    private RateLimitManager buildManager(final ModelRouterProperties.ModelInstance instance) {
        ModelRouterProperties.ServiceConfig svc = new ModelRouterProperties.ServiceConfig();
        svc.setInstances(List.of(instance));
        properties.setServices(Map.of("chat", svc));
        return new RateLimitManager(componentFactory, serviceTypeResolver, configConverterHelper, properties);
    }

    private ModelRouterProperties.RateLimitConfig enabledInstanceRateLimit() {
        ModelRouterProperties.RateLimitConfig cfg = new ModelRouterProperties.RateLimitConfig();
        cfg.setEnabled(true);
        cfg.setAlgorithm("token-bucket");
        cfg.setCapacity(50L);
        cfg.setRate(5L);
        cfg.setScope("instance");
        return cfg;
    }

    /** 取实例级限流器指标中的 identifier */
    private String instanceIdentifier(final RateLimitManager manager) {
        return manager.getRateLimiterMetrics().stream()
                .filter(m -> "instance".equals(m.getScope()))
                .map(RateLimitManager.RateLimiterMetrics::getIdentifier)
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到实例级限流器指标"));
    }

    @Test
    @DisplayName("instanceId 为 null 时回退到 name")
    void fallbackToNameWhenInstanceIdNull() {
        ModelRouterProperties.ModelInstance inst = new ModelRouterProperties.ModelInstance();
        inst.setName("qwen3:4b");
        inst.setBaseUrl("http://172.16.30.6:9090");
        inst.setRateLimit(enabledInstanceRateLimit());

        RateLimitManager manager = buildManager(inst);

        assertEquals("chat:qwen3:4b", instanceKey(manager));
        assertEquals("qwen3:4b", instanceIdentifier(manager));
    }

    @Test
    @DisplayName("instanceId 与 name 均为 null 时回退到 baseUrl")
    void fallbackToBaseUrlWhenNameNull() {
        ModelRouterProperties.ModelInstance inst = new ModelRouterProperties.ModelInstance();
        inst.setName(null);
        inst.setBaseUrl("http://172.16.30.6:9090");
        inst.setRateLimit(enabledInstanceRateLimit());

        RateLimitManager manager = buildManager(inst);

        assertEquals("chat:http://172.16.30.6:9090", instanceKey(manager));
        assertEquals("http://172.16.30.6:9090", instanceIdentifier(manager));
    }

    @Test
    @DisplayName("instanceId 存在时优先使用 instanceId")
    void preferInstanceId() {
        ModelRouterProperties.ModelInstance inst = new ModelRouterProperties.ModelInstance();
        inst.setInstanceId("c09eb43b-5084-48bb-8fc8-3e5ab2505c31");
        inst.setName("qwen3:4b");
        inst.setBaseUrl("http://172.16.30.6:9090");
        inst.setRateLimit(enabledInstanceRateLimit());

        RateLimitManager manager = buildManager(inst);

        assertEquals("c09eb43b-5084-48bb-8fc8-3e5ab2505c31", instanceIdentifier(manager));
    }

    @SuppressWarnings("unchecked")
    private String instanceKey(final RateLimitManager manager) {
        Map<String, String> instanceMap = (Map<String, String>) manager.getAllRateLimiterStatus().get("instance");
        assertEquals(1, instanceMap.size(), "应恰好创建一个实例级限流器");
        return instanceMap.keySet().iterator().next();
    }
}
