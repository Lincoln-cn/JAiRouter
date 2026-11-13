package org.unreal.modelrouter.config.cluster;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 上游实例健康状态在副本之间共享的配置（issue #1137）。
 *
 * <p>默认关闭：单机部署没有任何共享收益，集群部署也需要运维明确选择语义，
 * 因此 {@code enabled=false}，由「系统 → 集群设置」页按需打开。</p>
 */
@Component
@ConfigurationProperties(prefix = "jairouter.cluster.health-sharing")
public class HealthSharingProperties {

    /** 是否启用上游实例健康状态共享。 */
    private boolean enabled = false;

    /**
     * 判定语义：
     * <ul>
     *   <li>{@code local} —— 只用本副本探测结果（未启用共享时的行为）</li>
     *   <li>{@code shared} —— 只要共享视图里有副本认为健康就算健康（乐观，容忍探测抖动）</li>
     *   <li>{@code quorum} —— 本地健康 或 共享视图中「认为健康的副本数 ≥ tolerance-count」才算健康（折中，默认）</li>
     * </ul>
     */
    private String mode = "quorum";

    /** quorum 模式下，认为健康所需的最小副本数。 */
    private int toleranceCount = 1;

    /** 共享视图的有效期（秒）：超过该时长未更新的上报不再参与判定。 */
    private int graceSeconds = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public int getToleranceCount() {
        return toleranceCount;
    }

    public void setToleranceCount(int toleranceCount) {
        this.toleranceCount = toleranceCount;
    }

    public int getGraceSeconds() {
        return graceSeconds;
    }

    public void setGraceSeconds(int graceSeconds) {
        this.graceSeconds = graceSeconds;
    }
}
