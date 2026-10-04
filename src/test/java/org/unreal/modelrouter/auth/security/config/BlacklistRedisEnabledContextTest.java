package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.reactive.ReactiveOAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveSecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.security.reactive.ReactiveManagementWebSecurityAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.auth.security.service.EnhancedJwtBlacklistService;
import org.unreal.modelrouter.auth.security.service.JwtBlacklistService;
import org.unreal.modelrouter.auth.security.service.impl.JwtTokenIndexManager;
import org.unreal.modelrouter.auth.security.service.impl.RedisJwtBlacklistServiceImpl;
import org.unreal.modelrouter.persistence.store.StoreManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开启 {@code jairouter.security.jwt.blacklist.redis.enabled} 时的上下文装配测试（issue #194）。
 *
 * <p>背景：该开关打开时有两处定义争抢同一个 bean 名 {@code redisJwtBlacklistService} ——
 * {@code EnhancedJwtBlacklistService} 类上的 {@code @Service("redisJwtBlacklistService")}
 * 与 {@code JwtServiceConfiguration} 里同名的 {@code @Bean}，且两者条件相同。Spring Boot 默认
 * 禁止 bean 覆盖，故上下文 refresh 失败、应用启动失败。而
 * {@code deploy/k8s/base/configmap.yaml} 恰好把该开关设为 true，导致部署清单里的迁移 Job 与
 * Deployment 都起不来；其默认值为 {@code false}，因此既有测试对该配置零覆盖。
 *
 * <p>本测试自行 {@link SpringApplication#run} 起一个隔离最小应用（本仓无 {@code @SpringBootTest}，
 * 沿用 {@code GracefulShutdownInFlightRequestTest} 的范式）。真实应用是
 * {@code @SpringBootApplication} 全包扫描，这里用一个受限的 {@code ComponentScan} 只扫这两个
 * 实现类，但**是否登记完全取决于它们自身有没有 {@code @Service}**（见
 * {@link ServiceAnnotatedTargetFilter}）—— 这样既忠实保留「扫描期按注解取名」这一冲突来源，
 * 又不会把同包其它无关组件拖进来。
 */
@DisplayName("JWT 黑名单 Redis 开关开启时的上下文装配测试（issue #194）")
class BlacklistRedisEnabledContextTest {

    @Test
    @DisplayName("开关开启后上下文应能装配，且 redisJwtBlacklistService 只有一条定义")
    void contextShouldStartWhenRedisBlacklistEnabled() {
        try (ConfigurableApplicationContext context = new SpringApplication(IsolatedApp.class).run(
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--jairouter.security.jwt.blacklist.redis.enabled=true")) {

            assertTrue(context.isActive(), "上下文应处于活动状态");

            assertEquals(1, context.getBeanNamesForType(EnhancedJwtBlacklistService.class).length,
                    "EnhancedJwtBlacklistService 应有且仅有一个 bean（由 EnhancedSecurityConfiguration 提供）");

            Object redisBlacklist = context.getBean("redisJwtBlacklistService");
            assertInstanceOf(JwtBlacklistService.class, redisBlacklist,
                    "redisJwtBlacklistService 必须是 JwtBlacklistService（CompositeJwtBlacklistServiceImpl 的限定名注入对象）");
            assertInstanceOf(RedisJwtBlacklistServiceImpl.class, redisBlacklist,
                    "redisJwtBlacklistService 应由 JwtServiceConfiguration 的 @Bean 提供，而非被组件扫描顶掉");

            assertEquals(1, context.getBeanNamesForType(JwtBlacklistService.class).length,
                    "本配置集合内 JwtBlacklistService 候选应唯一（storeManagerJwtBlacklistService 在该开关下不生效）");
        }
    }

    /**
     * 只登记「既是这两个目标实现类之一、又带 {@code @Service}」的类。
     *
     * <p>等价于真实全包扫描在这两个类上的判定结果：注解在，类就是组件（其 bean 名按注解取名，
     * 这正是冲突来源）；注解被拿掉，类就不再注册。用自定义过滤器而不是
     * {@code FilterType.ANNOTATION}，是因为后者会把同包其它 {@code @Service}（如
     * {@code AccountManager}）一并拉进来，那些组件与本测试无关。
     */
    static class ServiceAnnotatedTargetFilter implements TypeFilter {

        @Override
        public boolean match(final MetadataReader metadataReader, final MetadataReaderFactory factory) {
            final String className = metadataReader.getClassMetadata().getClassName();
            final boolean isTarget = EnhancedJwtBlacklistService.class.getName().equals(className)
                    || RedisJwtBlacklistServiceImpl.class.getName().equals(className);
            return isTarget && metadataReader.getAnnotationMetadata().hasAnnotation(Service.class.getName());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaRepositoriesAutoConfiguration.class,
            RedisAutoConfiguration.class,
            RedisReactiveAutoConfiguration.class,
            RedisRepositoriesAutoConfiguration.class,
            ReactiveSecurityAutoConfiguration.class,
            ReactiveUserDetailsServiceAutoConfiguration.class,
            ReactiveManagementWebSecurityAutoConfiguration.class,
            ReactiveOAuth2ResourceServerAutoConfiguration.class
    })
    @Import({JwtServiceConfiguration.class, EnhancedSecurityConfiguration.class})
    @ComponentScan(
            basePackages = "org.unreal.modelrouter.auth.security.service",
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.CUSTOM, classes = ServiceAnnotatedTargetFilter.class))
    static class IsolatedApp {

        /** 真实应用中存在，本测试用桩替代，避免为装配测试引入持久层。 */
        @Bean
        StoreManager storeManager() {
            return Mockito.mock(StoreManager.class);
        }

        @Bean
        JwtTokenIndexManager jwtTokenIndexManager() {
            return Mockito.mock(JwtTokenIndexManager.class);
        }

        /**
         * 真实应用中该限定名 bean 由 {@code RedisJwtCacheConfiguration} 提供，其条件是
         * {@code jairouter.security.jwt.persistence.redis.enabled=true}。本测试只开黑名单开关
         * （即 issue #194 的触发条件），故用桩补上这个模板。
         *
         * <p>两个开关在真实配置里是耦合的（只开黑名单会因缺此 bean 而起不来），且定额侧在
         * 「自动化装配的 redis 工厂 + JWT 专属工厂」并存时另有注入不唯一的缺陷 —— 那两点都在
         * 本测试范围之外、由其它 issue 跟踪；本测试只负责守住「同名 bean 不得重复定义」。
         */
        @Bean
        @SuppressWarnings("unchecked")
        ReactiveRedisTemplate<String, String> jwtReactiveRedisTemplate() {
            return Mockito.mock(ReactiveRedisTemplate.class);
        }

        @Bean
        ReactiveStringRedisTemplate reactiveStringRedisTemplate() {
            return Mockito.mock(ReactiveStringRedisTemplate.class);
        }
    }
}
