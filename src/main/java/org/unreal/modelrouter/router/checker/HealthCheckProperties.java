package org.unreal.modelrouter.router.checker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 实例健康检查配置（v3.5.4，#650 / #651；#833 起默认改为 HTTP 探活）。
 *
 * <p>配置前缀 {@code jairouter.health-check}。间隔、超时与并发度保持 v3.5.4 的默认值；
 * 探活方式默认 {@link Mode#HTTP}（#833）—— TCP 只能证明端口能连上，上游进程僵死时
 * 仍会被判健康，请求会持续路由给一个不再响应的实例。</p>
 *
 * <p>HTTP 探活默认请求 {@code /v1/models}，判定口径见 {@link HttpHealthProbe}：
 * 状态码 &lt; 500 视为健康（含 404 / 405 —— 端点不存在或方法不允许，但进程在处理请求），
 * 5xx、超时与连接失败判不健康。上游没有该端点时仍然可用；想要更贴业务的判定，
 * 把 {@link #httpPath} 改成该上游的健康端点即可。</p>
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

    /** 探活方式，默认 HTTP（#833）：TCP 只看端口，无法发现进程僵死。 */
    private Mode mode = Mode.HTTP;

    /** HTTP 模式的请求路径，默认 {@code /v1/models}；留空时使用实例自己的 path。 */
    private String httpPath = "/v1/models";

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
