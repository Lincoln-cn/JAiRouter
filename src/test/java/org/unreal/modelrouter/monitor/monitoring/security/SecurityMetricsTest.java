package org.unreal.modelrouter.monitor.monitoring.security;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link SecurityMetrics} 的登记面守卫。
 *
 * <p>背景：本类曾在构造器里**急切登记**了一个 {@code jairouter.security.sanitization.rule.matches}
 * 计数器，而对应的 {@code recordSanitizationRuleMatch} 全仓零调用方 ⇒ 该指标在
 * {@code /actuator/prometheus} 上一直存在、且恒为 0（"指标在说谎"，比"没有指标"更糟）。
 * 用户在 issue #456 裁定按 B 处置：删掉这条未落地的意图，而不是给一个还没决定要做的能力先埋点。
 *
 * <p>本测试守住两件事，缺一不可：
 * <ol>
 *   <li><b>不再重新登记</b>那条恒 0 的规则命中指标 —— 这是回归方向；</li>
 *   <li><b>已接线的指标仍然登记着</b> —— 否则"删掉假的"会掩盖"顺手删了真的"，
 *       而中英文档的 monitoring/metrics.md 明确列了
 *       {@code jairouter_security_authentication_attempts_total} /
 *       {@code ..._authentication_duration_seconds} /
 *       {@code ..._sanitization_operations_total} /
 *       {@code ..._sanitization_duration_seconds} 四个名字。</li>
 * </ol>
 *
 * <p>第三个用例把"登记面"钉成一份显式清单：以后谁再往构造器里加一个 Meter，必须同步改这份清单，
 * 也就必须回答"它的写入点在哪"——这正是本缺陷当初溜进来的路径。
 */
class SecurityMetricsTest {

    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        new SecurityMetrics(registry);
    }

    @Test
    @DisplayName("规则命中指标不再被登记（#456 选 B 后恒 0 指标已删）")
    void ruleMatchMetricIsNoLongerRegistered() {
        assertNull(registry.find("jairouter.security.sanitization.rule.matches").counter(),
                "recordSanitizationRuleMatch 已删除；若重新登记这条指标，它会在 Prometheus 上恒为 0");
    }

    @Test
    @DisplayName("文档列出的四个安全指标仍然被登记（删除不得误伤已接线指标）")
    void documentedMetricsAreStillRegistered() {
        assertNotNull(registry.find("jairouter.security.authentication.attempts").counter());
        assertNotNull(registry.find("jairouter.security.authentication.duration").timer());
        assertNotNull(registry.find("jairouter.security.sanitization.operations").counter());
        assertNotNull(registry.find("jairouter.security.sanitization.duration").timer());
    }

    @Test
    @DisplayName("登记面恰好等于这份清单（新增指标必须显式改这里并回答写入点）")
    void registeredMetersMatchExplicitInventory() {
        Set<String> actual = new TreeSet<>();
        registry.getMeters().forEach(meter -> actual.add(meter.getId().getName()));

        Set<String> expected = new TreeSet<>(Set.of(
                // 认证
                "jairouter.security.authentication.attempts",
                "jairouter.security.authentication.successes",
                "jairouter.security.authentication.failures",
                "jairouter.security.authentication.duration",
                // JWT
                "jairouter.security.jwt.validations",
                "jairouter.security.jwt.refreshes",
                "jairouter.security.jwt.expired",
                "jairouter.security.jwt.validation.duration",
                // 脱敏（操作 / 耗时；规则命中不计，见 #456）
                "jairouter.security.sanitization.operations",
                "jairouter.security.sanitization.duration",
                // 安全事件
                "jairouter.security.violations"));

        assertEquals(expected, actual,
                "登记面发生变化：新增指标前先确认它有真实写入点，并同步更新本清单");
    }
}
