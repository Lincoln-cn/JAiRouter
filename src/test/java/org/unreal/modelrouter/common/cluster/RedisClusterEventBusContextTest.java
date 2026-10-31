package org.unreal.modelrouter.common.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 守卫 {@link RedisClusterEventBus} 能被 Spring 正确实例化（#918）。
 *
 * <p>背景：该类有两个 public 构造器却都没有标注 {@code @Autowired}。Spring 在「多个构造器且
 * 均无 {@code @Autowired}」时退回无参构造器，而该类没有无参构造器 ⇒ 开启
 * {@code jairouter.cluster.events.enabled=true} 时启动直接失败：
 * {@code Failed to instantiate ... No default constructor found}。</p>
 *
 * <p>该缺陷只在「真的开启集群事件」的多副本部署里暴露：单副本开关默认 false，而既有单测都是
 * 直接 {@code new} 三参构造器，绕过了 Spring 的构造器解析。因此这里用包扫描 + 真实容器刷新
 * 复现装配路径，而不是手工 registerBean（后者不会走构造器推断）。</p>
 */
class RedisClusterEventBusContextTest {

    @Test
    @DisplayName("918-001 包扫描装配时能创建 RedisClusterEventBus（多构造器必须能被自动装配）")
    void contextCreatesBeanThroughComponentScan() {
        try (AnnotationConfigApplicationContext context = newContext(true)) {
            assertThat(context.getBean(RedisClusterEventBus.class)).isNotNull();
        }
    }

    @Test
    @DisplayName("918-002 Redis 不可用时仍能创建（降级为仅本副本可见），不得阻断启动")
    void degradesWhenRedisMissing() {
        try (AnnotationConfigApplicationContext context = newContext(false)) {
            assertThat(context.getBean(RedisClusterEventBus.class)).isNotNull();
        }
    }

    private AnnotationConfigApplicationContext newContext(final boolean withRedis) {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                "test", Map.of("jairouter.cluster.events.enabled", "true")));
        context.registerBean(ObjectMapper.class, (Supplier<ObjectMapper>) ObjectMapper::new);
        if (withRedis) {
            context.registerBean(ReactiveRedisConnectionFactory.class,
                    (Supplier<ReactiveRedisConnectionFactory>) () -> mock(ReactiveRedisConnectionFactory.class));
        }
        context.scan("org.unreal.modelrouter.common.cluster");
        context.refresh();
        return context;
    }
}
