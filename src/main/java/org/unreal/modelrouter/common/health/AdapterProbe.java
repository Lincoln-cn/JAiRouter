package org.unreal.modelrouter.common.health;

import java.util.function.Predicate;
import org.unreal.modelrouter.router.model.ModelRouterProperties;

/**
 * 适配器探针：该实例有没有解析出一个**已注册**的适配器。
 *
 * <p>这个探针直接对应 {@code #1004}：实例的 adapter 为空时，网关在转发阶段才会
 * 502，而健康检查（只测端口）一直显示 HEALTHY，用户看到的是「实例健康、调用全挂」。
 * 把这个事实提前到健康检查里，界面就能直接标出来。</p>
 *
 * <p>判为 {@link HealthSignal.Severity#SOFT}：适配器解析不出通常是配置疏漏，
 * 实例本身没坏。判成 FATAL 会让一个提示性问题变成整条链路不可用。</p>
 *
 * @since v3.8.0
 */
public class AdapterProbe implements HealthProbe {

    private final String globalAdapter;
    private final Predicate<String> registered;

    /**
     * @param globalAdapter 全局默认适配器名，用于回退
     * @param registered    判断某个适配器名是否已注册（由上层注入，避免探针依赖容器）
     */
    public AdapterProbe(final String globalAdapter, final Predicate<String> registered) {
        this.globalAdapter = globalAdapter;
        this.registered = registered == null ? name -> false : registered;
    }

    @Override
    public String name() {
        return "adapter";
    }

    @Override
    public HealthSignal probe(final String serviceType,
                              final ModelRouterProperties.ModelInstance instance) {
        if (instance == null) {
            return HealthSignal.unknown("实例为空");
        }

        final String resolved = resolve(instance);
        if (resolved == null || resolved.isBlank()) {
            return HealthSignal.down(HealthSignal.Severity.SOFT,
                    "没有生效的适配器：实例级与服务级都未配置，且全局默认也为空");
        }

        if (!registered.test(resolved)) {
            return HealthSignal.down(HealthSignal.Severity.SOFT,
                    "适配器 '" + resolved + "' 未注册：请检查 model.services."
                            + serviceType + ".adapter 的取值");
        }

        return HealthSignal.up();
    }

    private String resolve(final ModelRouterProperties.ModelInstance instance) {
        final String own = instance.getAdapter();
        if (own != null && !own.isBlank()) {
            return own;
        }
        return globalAdapter;
    }
}
