package org.unreal.modelrouter.monitor.monitoring.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 安全告警配置 —— <b>安全告警唯一的配置面</b>（issue #288）。
 *
 * <p>此前同一语义的阈值散落在三处：本服务的类内常量、{@code AuditConfig.AlertConfig}
 * （只被审计定时任务读取）、{@code AuditConfig.AlertThresholds}（只被运行时配置校验与回显），
 * 而 {@code security-base.yml} 里这块真正被文档化的 {@code jairouter.security.monitoring.alerts.*}
 * 反而没有任何绑定类。现在统一到这里：实现读本配置，其余重复来源已删除。</p>
 *
 * <p>语义是<b>窗口计数</b>（如「5 分钟内认证失败 5 次」），不是比率 —— 原配置块里的
 * {@code authentication-failure-rate} 等比率键从未有实现，已随本次统一移除。</p>
 *
 * <p>开关 {@code jairouter.security.monitoring.alerts.enabled} 由告警 bean 上的
 * {@code @ConditionalOnProperty} 消费（因此本类不再重复声明该字段）。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "jairouter.security.monitoring.alerts")
public class SecurityAlertProperties {

    private Thresholds thresholds = new Thresholds();

    /**
     * 告警阈值与时间窗口
     */
    @Data
    public static class Thresholds {

        /** 认证失败次数阈值 */
        private int authenticationFailureCount = 5;

        /** 认证失败时间窗口（分钟） */
        private int authenticationFailureWindowMinutes = 5;

        /** 同一 IP 的认证失败次数阈值 */
        private int suspiciousIpCount = 10;

        /** 同一 IP 的时间窗口（分钟） */
        private int suspiciousIpWindowMinutes = 10;

        /** 脱敏操作次数阈值 */
        private int sanitizationCount = 100;

        /** 脱敏操作时间窗口（分钟） */
        private int sanitizationWindowMinutes = 60;

        /** JWT 相关失败次数阈值 */
        private int jwtFailureCount = 20;

        /** JWT 相关失败时间窗口（分钟） */
        private int jwtFailureWindowMinutes = 10;

        /** 同类型告警的冷却时间（分钟） */
        private int cooldownMinutes = 15;
    }
}
