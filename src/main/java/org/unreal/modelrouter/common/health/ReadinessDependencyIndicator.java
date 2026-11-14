package org.unreal.modelrouter.common.health;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthContributor;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.config.health.HealthProbeProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 就绪探针的依赖聚合器（默认关闭）。
 *
 * <h2>为什么需要它</h2>
 * {@code management.endpoint.health.group.readiness.include} 里的名字会被 Spring Boot 做
 * <b>严格存在性校验</b>（{@code HealthEndpointGroupMembershipValidator}）：列一个当前上下文
 * 里不存在的贡献者，应用会<b>启动失败</b>。而 {@code db}、{@code redis} 是否装配取决于配置
 * （例如 {@code REDIS_ENABLED}），无法在 yml 里静态列全 —— 所以 readiness 默认只含
 * {@code readinessState}，代价是依赖故障不会把副本摘出就绪集合。
 *
 * <p>本类<b>恒装配</b>（不依赖任何条件键），因此把它写进 readiness 的 include 不会触发上述
 * 校验；它内部再按需查找 {@code db} / {@code redis} 贡献者。默认
 * {@code jairouter.health.readiness.include-dependencies=false}，此时恒定 UP，不改变任何既有行为。</p>
 *
 * <h2>启用后的副作用（务必写进文档）</h2>
 * 依赖故障时新副本会一直不就绪，滚动发布会卡住直到依赖恢复。这是「依赖故障必须摘流」这一
 * 诉求的必然代价，所以该能力默认关闭。
 *
 * <p>另外两条刻意的设计：未装配的依赖<b>跳过</b>而不是判 DOWN（否则没配 Redis 的部署会被
 * 误判）；贡献者抛异常时<b>吞掉</b>（探针自身不能成为故障源）。</p>
 */
@Component("readinessDependencies")
public class ReadinessDependencyIndicator implements HealthIndicator {

    /** 参与就绪判定的依赖组件名，与 Spring Boot 自动装配的贡献者名字一致。 */
    private static final List<String> DEPENDENCY_CONTRIBUTORS = List.of("db", "redis");

    private final ObjectProvider<HealthContributorRegistry> registryProvider;
    private final HealthProbeProperties healthProbeProperties;

    public ReadinessDependencyIndicator(
            ObjectProvider<HealthContributorRegistry> registryProvider,
            HealthProbeProperties healthProbeProperties) {
        this.registryProvider = registryProvider;
        this.healthProbeProperties = healthProbeProperties;
    }

    @Override
    public Health health() {
        if (!healthProbeProperties.getReadiness().isIncludeDependencies()) {
            return Health.up().withDetail("enabled", false).build();
        }

        HealthContributorRegistry registry = registryProvider.getIfAvailable();
        if (registry == null) {
            return Health.up()
                    .withDetail("enabled", true)
                    .withDetail("note", "no health contributor registry available")
                    .build();
        }

        Map<String, HealthContributor> contributors = new HashMap<>();
        registry.stream().forEach(entry -> contributors.put(entry.name(), entry.contributor()));
        List<String> evaluated = new ArrayList<>();
        List<String> unhealthy = new ArrayList<>();

        for (String name : DEPENDENCY_CONTRIBUTORS) {
            HealthContributor contributor = contributors.get(name);
            if (!(contributor instanceof HealthIndicator indicator)) {
                // 该依赖没有装配（例如没启用 Redis）—— 跳过，不能因此判 DOWN
                continue;
            }
            evaluated.add(name);
            try {
                Health health = indicator.health();
                if (!Status.UP.equals(health.getStatus())) {
                    unhealthy.add(name + "=" + health.getStatus().getCode());
                }
            } catch (RuntimeException e) {
                // 探针不能成为故障源：忽略该组件的异常
            }
        }

        Health.Builder builder = unhealthy.isEmpty() ? Health.up() : Health.down();
        return builder
                .withDetail("enabled", true)
                .withDetail("evaluated", evaluated)
                .withDetail("unhealthy", unhealthy)
                .build();
    }
}
