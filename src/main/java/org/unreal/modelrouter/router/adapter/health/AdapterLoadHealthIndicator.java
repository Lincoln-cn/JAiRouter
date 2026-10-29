package org.unreal.modelrouter.router.adapter.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 适配器加载失败健康指标（#778）。
 *
 * <p>把 {@link AdapterRegistry#getLoadFailures()} 挂到 {@code /actuator/health} 的
 * {@code adapterLoad} 组件详情：列表非空即表示「配置里声明了这个 adapter，但运行时没有它」，
 * 此时限制到该 adapter 的请求会失败。修复前这类失败只有一行 {@code logger.error}，
 * 写入侧全绿、运行期才发现没有可用适配器。</p>
 *
 * <p><b>默认恒为 UP</b>（状态放在 details 里），与 {@code SharedStateHealthIndicator}、
 * {@code RbacEndpointCoverageHealthIndicator} 的理由一致：适配器定义写坏是「配置缺陷」而非
 * 「进程不可用」，直接判 DOWN 会让所有以 {@code /actuator/health} 为探针的容器变成 unhealthy，
 * 在 K8s 上还可能触发重启循环 —— 而重启修不好一份坏定义。</p>
 *
 * <p>需要让编排层感知时显式设 {@code jairouter.router.adapter.health.fail-health=true}
 * （降级为 DOWN）。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.6
 */
@Component("adapterLoad")
@ConditionalOnProperty(
        name = "jairouter.router.adapter.health.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AdapterLoadHealthIndicator implements HealthIndicator {

    private final AdapterRegistry adapterRegistry;
    private final boolean failHealth;

    public AdapterLoadHealthIndicator(final AdapterRegistry adapterRegistry,
                                     final org.springframework.core.env.Environment environment) {
        this.adapterRegistry = adapterRegistry;
        this.failHealth = Boolean.parseBoolean(
                environment.getProperty("jairouter.router.adapter.health.fail-health", "false"));
    }

    @Override
    public Health health() {
        final Map<String, String> failures = adapterRegistry.getLoadFailures();
        final Map<String, Object> details = new LinkedHashMap<>();
        details.put("failedAdapterCount", failures.size());

        if (failures.isEmpty()) {
            return Health.up().withDetails(details).build();
        }

        details.put("failedAdapters", failures);
        details.put("advice",
                "这些适配器在配置里有定义但没能加载成功，限制到它们的请求会失败；"
                        + "修正定义（如 extend 的 parent 缺失、capabilities/auth 缺字段）后重启生效，"
                        + "或显式设 jairouter.router.adapter.health.fail-health=true 让健康检查转 DOWN");
        final Status status = failHealth ? Status.DOWN : Status.UP;
        return Health.status(status).withDetails(details).build();
    }
}
