package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组件特定配置
 *
 * <p>子段与消费方（issue #224 逐项核对过）：{@code load-balancer} / {@code rate-limiter} /
 * {@code circuit-breaker} 的 {@code enabled} 由 {@code TracingWrapperFactory} 真正读取，是行为开关；
 * {@code http} 下各字段**只被 {@code TracingInfoContributor} 回显到 /actuator/info**，不改变采集行为。</p>
 *
 * <p>历史上还有 {@code database} / {@code cache} / {@code messaging} 三段，其字段（{@code enabled}、
 * {@code capture-sql} / {@code max-sql-length}、{@code capture-keys} / {@code capture-values}、
 * {@code capture-headers} / {@code capture-body}）**全仓没有任何读者**：{@code getComponents()} 只有两处
 * 调用点（{@code TracingInfoContributor} 只读 http、{@code TracingWrapperFactory} 只读上面三个开关）。
 * 这类「绑定得上却永远不生效」的配置是缺陷，已随 issue #224 删除 —— 需要真实能力时，**先实现消费方，
 * 再按字段实名加回来**。</p>
 */
@Data
public class TracingComponentConfig {
    private HttpConfig http = new HttpConfig();
    private LoadBalancerConfig loadBalancer = new LoadBalancerConfig();
    private RateLimiterConfig rateLimiter = new RateLimiterConfig();
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /**
     * HTTP 侧插桩配置。
     *
     * <p>⚠️ 这几个字段目前**只被 {@code TracingInfoContributor} 回显到 /actuator/info**，没有任何生产
     * 代码读它们 ⇒ 调参不会改变采集行为（issue #224）。保留它们是因为那份回显就是它们的唯一消费方，
     * 且 {@code TracingInfoContributorReportContractTest} 把这个契约钉住了；若哪天连回显也要去掉，
     * 这些字段应当一并删除。</p>
     */
    @Data
    public static class HttpConfig {
        private boolean enabled = true;
        private boolean captureHeaders = true;
        private boolean captureBody = false;
        private List<String> excludedPaths = new ArrayList<>();
    }

    @Data
    public static class LoadBalancerConfig {
        private boolean enabled = true;
        private boolean captureStrategy = true;
        private boolean captureCandidates = true;
        private boolean captureSelection = true;
        private boolean captureStatistics = true;
    }

    @Data
    public static class RateLimiterConfig {
        private boolean enabled = true;
        private boolean captureAlgorithm = true;
        private boolean captureQuota = true;
        private boolean captureDecision = true;
        private boolean captureStatistics = true;
    }

    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private boolean captureState = true;
        private boolean captureStateChanges = true;
        private boolean captureStatistics = true;
        private boolean captureFailureRate = true;
    }
}
