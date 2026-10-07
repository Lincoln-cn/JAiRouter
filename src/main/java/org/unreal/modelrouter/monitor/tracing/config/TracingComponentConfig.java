package org.unreal.modelrouter.monitor.tracing.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 组件特定配置
 *
 * <p>子段与消费方（issue #224 逐项核对过，方法 = 按 lombok 生成的 getter 名在 {@code src/main} 全仓计消费点）：
 * {@code load-balancer} / {@code rate-limiter} / {@code circuit-breaker} 的 {@code enabled} 由
 * {@code TracingWrapperFactory} 真正读取，是行为开关；其余 {@code capture-*} 标志的**唯一消费者是
 * {@code TracingInfoContributor} 往 /actuator/info 的回显**，不改变采集行为。</p>
 *
 * <p>已经删除两批零消费方字段：</p>
 * <ul>
 *   <li>批 A（#224）：{@code database} / {@code cache} / {@code messaging} 三段整棵子树 ——
 *       {@code getComponents()} 只有两个读者（{@code TracingInfoContributor} 只读 http、
 *       {@code TracingWrapperFactory} 只读三个开关），这三棵树既不影响行为也不出现在 /actuator/info；</li>
 *   <li>批 B2（#224）：{@code loadBalancer.capture-candidates} / {@code capture-selection}、
 *       {@code rateLimiter.capture-quota} / {@code capture-decision}、
 *       {@code circuitBreaker.capture-state-changes} / {@code capture-failure-rate} ——
 *       **既没有消费方，也不被 /actuator/info 回显**，属于"改它完全没有任何可观测后果"。
 *       注意它们与被保留的 {@code capture-strategy} / {@code capture-algorithm} / {@code capture-state} /
 *       {@code capture-statistics} 不同：后者至少会被回显。</li>
 * </ul>
 *
 * <p>这类「绑定得上却永远不生效」的配置是缺陷。需要真实能力时，**先实现消费方，再按字段实名加回来**。</p>
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

    /** 各字段的消费方 = {@code TracingInfoContributor} 的 http 段回显（见 {@link HttpConfig} 的说明） */
    @Data
    public static class LoadBalancerConfig {
        private boolean enabled = true;
        private boolean captureStrategy = true;
        private boolean captureStatistics = true;
    }

    @Data
    public static class RateLimiterConfig {
        private boolean enabled = true;
        private boolean captureAlgorithm = true;
        private boolean captureStatistics = true;
    }

    @Data
    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private boolean captureState = true;
        private boolean captureStatistics = true;
    }
}
