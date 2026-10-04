package org.unreal.modelrouter.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.unreal.modelrouter.auth.security.config.RedisJwtCacheConfiguration;
import org.unreal.modelrouter.persistence.jpa.JpaStoreManager;
import org.unreal.modelrouter.persistence.store.StoreManager;
import org.unreal.modelrouter.persistence.store.StoreManagerConfiguration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 多副本必需开关打开后的类型消歧（issue #196）。
 *
 * <p>背景：开启 {@code jairouter.security.jwt.persistence.redis.enabled=true} 后，JWT 侧原先会
 * 自建一套 Redis（专用连接工厂 + 专用模板），与应用默认那套同类型并存，于是全仓十余处未限定的
 * {@code ReactiveRedisConnectionFactory} / {@code ReactiveRedisTemplate<String, String>} 注入点
 * 变成"候选不唯一"，按文档开启全套开关后应用直接启动失败。整改方向（用户裁决）：**取消 JWT 独立
 * Redis**，JWT 复用应用默认 Redis。
 *
 * <p>另同批暴露：{@code StoreManager} 也有两个同类型 bean（{@code jpaStoreManager} 组件与
 * {@code storeManager} 别名，实为同一实例），此前靠**参数名恰好等于 bean 名**才能解析，
 * 参数名不匹配的注入点（如该开关打开后装配的 {@code RedisJwtTokenPersistenceServiceImpl}）直接失败。
 */
@DisplayName("多副本必需开关打开后的类型消歧（issue #196）")
class MultiReplicaSwitchesWiringTest {

    private final ApplicationContextRunner jwtRedisRunner = new ApplicationContextRunner()
            .withUserConfiguration(RedisJwtCacheConfiguration.class)
            .withPropertyValues("jairouter.security.jwt.persistence.redis.enabled=true")
            .withBean("reactiveStringRedisTemplate", ReactiveRedisTemplate.class,
                    () -> Mockito.mock(ReactiveRedisTemplate.class));

    private final ApplicationContextRunner storeManagerRunner = new ApplicationContextRunner()
            .withUserConfiguration(StoreManagerConfiguration.class)
            .withBean(JpaStoreManager.class, () -> Mockito.mock(JpaStoreManager.class));

    @Test
    @DisplayName("JWT Redis 开关打开后，不再自建连接工厂与专用模板")
    void jwtRedisConfigurationCreatesNoOwnConnection() {
        jwtRedisRunner.run(context -> {
            assertNotNull(context.getBean(RedisJwtCacheConfiguration.RedisJwtHealthChecker.class),
                    "健康检查器应仍然提供（消费者 JwtRedisHealthMonitor 依赖它）");

            assertEquals(0, context.getBeanNamesForType(ReactiveRedisConnectionFactory.class).length,
                    "不应再有 JWT 专用连接工厂（那是与应用默认 Redis 并存的第二个候选）");
            assertFalse(context.containsBean("jwtRedisConnectionFactory"),
                    "不应再注册 jwtRedisConnectionFactory");
            assertFalse(context.containsBean("jwtReactiveRedisTemplate"),
                    "不应再注册 jwtReactiveRedisTemplate");
        });
    }

    @Test
    @DisplayName("StoreManager 按类型解析唯一（别名 bean 已标 @Primary）")
    void storeManagerResolvesUniquelyByType() {
        storeManagerRunner.run(context -> assertNotNull(context.getBean(StoreManager.class),
                "StoreManager 应能按类型解析出唯一 bean；否则按类型注入的地方会报候选不唯一"));
    }
}
