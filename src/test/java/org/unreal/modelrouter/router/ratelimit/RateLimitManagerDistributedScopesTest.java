package org.unreal.modelrouter.router.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.unreal.modelrouter.config.core.helper.ConfigConverterHelper;
import org.unreal.modelrouter.config.core.helper.ServiceTypeResolver;
import org.unreal.modelrouter.router.factory.ComponentFactory;
import org.unreal.modelrouter.router.model.ModelRouterProperties;
import org.unreal.modelrouter.router.model.ModelServiceRegistry;
import org.unreal.modelrouter.router.ratelimit.impl.RedisSlidingWindowRateLimiter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 分布式限流开关对四级限流器的覆盖（issue #1114）。
 *
 * <p>此前只有客户端维度与终端用户维度接 Redis：全局 / 服务 / 实例 / 规则在
 * {@code jairouter.ratelimit.distributed.enabled=true} 之后仍是每副本一份，
 * 三副本部署时服务级上限实际是配置值的 3 倍。本测试钉住两件事：</p>
 *
 * <ol>
 *   <li>开关打开且 Redis 可用时，四级都拿到 {@link RedisSlidingWindowRateLimiter}；</li>
 *   <li>注入发生在构造器之后 —— 所以 {@code @PostConstruct} 那一步确实必要，
 *       没调它时构造期创建的是本地实现。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("分布式限流开关对全局/服务/实例/规则的覆盖（#1114）")
class RateLimitManagerDistributedScopesTest {

    @Mock
    private ComponentFactory componentFactory;

    @Mock
    private ServiceTypeResolver serviceTypeResolver;

    @Mock
    private ConfigConverterHelper configConverterHelper;

    private ModelRouterProperties properties;

    private RateLimiter localLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        properties = new ModelRouterProperties();

        properties.setRateLimit(rateLimitConfig("global", 100L, 10L));
        ModelRouterProperties.ServiceConfig svc = new ModelRouterProperties.ServiceConfig();
        svc.setRateLimit(rateLimitConfig("service", 50L, 5L));
        ModelRouterProperties.ModelInstance inst = new ModelRouterProperties.ModelInstance();
        inst.setInstanceId("inst-1");
        inst.setName("qwen3:4b");
        inst.setBaseUrl("http://127.0.0.1:18080");
        inst.setRateLimit(rateLimitConfig("instance", 20L, 2L));
        svc.setInstances(List.of(inst));
        properties.setServices(Map.of("chat", svc));

        when(serviceTypeResolver.parseServiceType("chat")).thenReturn(ModelServiceRegistry.ServiceType.chat);
        when(configConverterHelper.convertRateLimitConfig(any()))
                .thenAnswer(inv -> toRouterConfig(inv.getArgument(0)));

        localLimiter = mock(RateLimiter.class);
        when(componentFactory.createScopedRateLimiter(any())).thenReturn(localLimiter);
    }

    private ModelRouterProperties.RateLimitConfig rateLimitConfig(final String scope,
                                                                  final long capacity,
                                                                  final long rate) {
        ModelRouterProperties.RateLimitConfig cfg = new ModelRouterProperties.RateLimitConfig();
        cfg.setEnabled(true);
        cfg.setAlgorithm("token-bucket");
        cfg.setCapacity(capacity);
        cfg.setRate(rate);
        cfg.setScope(scope);
        return cfg;
    }

    private RateLimitConfig toRouterConfig(final ModelRouterProperties.RateLimitConfig src) {
        RateLimitConfig dst = new RateLimitConfig();
        dst.setEnabled(Boolean.TRUE.equals(src.getEnabled()));
        dst.setAlgorithm(src.getAlgorithm());
        dst.setCapacity(src.getCapacity());
        dst.setRate(src.getRate());
        dst.setScope(src.getScope());
        return dst;
    }

    private RateLimitManager buildManager() {
        return new RateLimitManager(componentFactory, serviceTypeResolver, configConverterHelper, properties);
    }

    @SuppressWarnings("unchecked")
    private ReactiveRedisTemplate<String, String> enableDistributed(final RateLimitManager manager,
                                                                   final boolean withRedis) throws Exception {
        final ReactiveRedisTemplate<String, String> template = mock(ReactiveRedisTemplate.class);
        ObjectProvider<ReactiveRedisTemplate<String, String>> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(withRedis ? template : null);

        set(manager, "reactiveRedisTemplateProvider", provider);
        set(manager, "distributedRateLimitEnabled", true);
        set(manager, "distributedRateLimitTimeoutMs", 50L);
        return template;
    }

    private void set(final Object target, final String field, final Object value) throws Exception {
        Field f = RateLimitManager.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    private Object get(final Object target, final String field) throws Exception {
        Field f = RateLimitManager.class.getDeclaredField(field);
        f.setAccessible(true);
        return f.get(target);
    }

    private RateLimiter invokeCreate(final RateLimitManager manager,
                                     final RateLimitConfig cfg,
                                     final String cacheKey) throws Exception {
        Method m = RateLimitManager.class.getDeclaredMethod(
                "createScopedLimiter", RateLimitConfig.class, String.class);
        m.setAccessible(true);
        return (RateLimiter) m.invoke(manager, cfg, cacheKey);
    }

    @Test
    @DisplayName("构造期创建的是本地实现：注入还没发生")
    void constructedLimitersAreLocalBeforeInjection() throws Exception {
        RateLimitManager manager = buildManager();
        assertSame(localLimiter, get(manager, "globalLimiter"),
                "构造器里跑 initializeRateLimiters() 时 @Value 尚未注入，只能是本地实现");
    }

    @Test
    @DisplayName("开关打开且 Redis 可用：@PostConstruct 后四级都换成 Redis 实现")
    @SuppressWarnings("unchecked")
    void reinitializeSwitchesAllFourScopesToRedis() throws Exception {
        RateLimitManager manager = buildManager();
        enableDistributed(manager, true);

        manager.reinitializeDistributedLimiters();

        assertInstanceOf(RedisSlidingWindowRateLimiter.class, get(manager, "globalLimiter"),
                "全局限流器应改用 Redis 滑动窗口");

        Map<ModelServiceRegistry.ServiceType, RateLimiter> serviceLimiters =
                (Map<ModelServiceRegistry.ServiceType, RateLimiter>) get(manager, "serviceLimiters");
        assertInstanceOf(RedisSlidingWindowRateLimiter.class,
                serviceLimiters.get(ModelServiceRegistry.ServiceType.chat),
                "服务级限流器应改用 Redis 滑动窗口");

        Map<String, RateLimiter> instanceLimiters = (Map<String, RateLimiter>) get(manager, "instanceLimiters");
        assertInstanceOf(RedisSlidingWindowRateLimiter.class,
                instanceLimiters.values().iterator().next(),
                "实例级限流器应改用 Redis 滑动窗口");

        RateLimiter rule = invokeCreate(manager, toRouterConfig(rateLimitConfig("rule", 10L, 1L)), "rule:r1");
        assertInstanceOf(RedisSlidingWindowRateLimiter.class, rule,
                "规则级限流器应改用 Redis 滑动窗口");
    }

    @Test
    @DisplayName("开关打开但 Redis 不可用：保持本地实现，不抛异常")
    void keepsLocalLimiterWhenRedisMissing() throws Exception {
        RateLimitManager manager = buildManager();
        enableDistributed(manager, false);

        manager.reinitializeDistributedLimiters();

        assertSame(localLimiter, get(manager, "globalLimiter"),
                "没有 Redis 模板时应退回本地实现，不能因为开关打开就抛错");
    }

    @Test
    @DisplayName("开关关闭：即使有 Redis 模板也保持本地实现")
    void staysLocalWhenSwitchOff() throws Exception {
        RateLimitManager manager = buildManager();
        ObjectProvider<ReactiveRedisTemplate<String, String>> provider = mock(ObjectProvider.class);
        set(manager, "reactiveRedisTemplateProvider", provider);
        set(manager, "distributedRateLimitEnabled", false);

        manager.reinitializeDistributedLimiters();

        assertSame(localLimiter, get(manager, "globalLimiter"),
                "开关关闭时行为必须与引入前完全一致");
    }
}
