package org.unreal.modelrouter.router.checker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 实例健康检查配置（v3.5.4，#650 / #651）。
 *
 * <p>配置前缀 {@code jairouter.health-check}。默认值刻意与修复前的硬编码行为一致
 * （30 秒一轮、5 秒超时、TCP 端口探活），把「可配置」与「默认行为不变」分开，
 * 便于运维按部署规模调整。</p>
 *
 * @since v3.5.4
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.health-check")
public class HealthCheckProperties {

    /** 探活方式：TCP 只看端口能否建立连接；HTTP 会真正请求一次实例路径。 */
    public enum Mode {
        TCP,
        HTTP
    }

    /** 两轮检查之间的间隔（毫秒）。 */
    private long intervalMs = 30_000L;

    /** 单个实例的探活超时（毫秒）。 */
    private int timeoutMs = 5_000;

    /** 探活方式，默认 TCP（与修复前一致）。 */
    private Mode mode = Mode.TCP;

    /** HTTP 模式的请求路径；留空时使用实例自己的 path。 */
    private String httpPath = "";

    /** 单轮内并发检查服务类型的线程数。 */
    private int parallelism = 4;

    /**
     * 检查间隔，下限 1 秒（避免误配成 0 导致空转）。
     */
    public long effectiveIntervalMs() {
        return Math.max(1_000L, intervalMs);
    }

    /**
     * 探活超时，钳制在 200ms ~ 60s。
     */
    public int effectiveTimeoutMs() {
        return Math.min(60_000, Math.max(200, timeoutMs));
    }

    /**
     * 并发度，钳制在 1 ~ 16。
     */
    public int effectiveParallelism() {
        return Math.min(16, Math.max(1, parallelism));
    }
}
