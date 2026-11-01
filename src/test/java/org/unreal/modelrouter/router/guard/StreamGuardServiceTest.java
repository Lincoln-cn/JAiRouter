package org.unreal.modelrouter.router.guard;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.router.guard.GuardProperties.Pattern;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 护栏服务的装配、开关与失败语义（#961）。
 *
 * @since 3.6.0
 */
@DisplayName("流式护栏服务")
class StreamGuardServiceTest {

    private static Pattern pattern(final String name, final String regex, final String replacement) {
        final Pattern p = new Pattern();
        p.setName(name);
        p.setRegex(regex);
        if (replacement != null) {
            p.setReplacement(replacement);
        }
        return p;
    }

    private static GuardProperties props(final boolean enabled,
                                         final List<Pattern> redact,
                                         final List<Pattern> block) {
        final GuardProperties properties = new GuardProperties();
        properties.setEnabled(enabled);
        properties.getRedact().setPatterns(redact);
        properties.getBlock().setPatterns(block);
        return properties;
    }

    @Test
    @DisplayName("总开关关闭时完全不介入")
    void disabledServiceAllowsEverything() {
        final GuardProperties properties = props(false,
                List.of(pattern("phone", "\\d{11}", "[PHONE]")), List.of());
        final StreamGuardService service = new StreamGuardService(properties, null);
        service.rebuild();

        assertThat(service.enabled()).isFalse();
        final GuardDecision decision = service.evaluate("13800138000");
        assertThat(decision.action()).isEqualTo(GuardAction.ALLOW);
    }

    @Test
    @DisplayName("开关打开但没有规则时同样不介入")
    void enabledWithoutRulesIsInert() {
        final StreamGuardService service = new StreamGuardService(props(true, List.of(), List.of()), null);
        service.rebuild();

        assertThat(service.enabled()).isFalse();
    }

    @Test
    @DisplayName("开关打开且有规则时命中脱敏")
    void redactsWhenEnabled() {
        final StreamGuardService service = new StreamGuardService(
                props(true, List.of(pattern("phone", "\\d{11}", "[PHONE]")), List.of()), null);
        service.rebuild();

        assertThat(service.enabled()).isTrue();
        final GuardDecision decision = service.evaluate("call 13800138000");
        assertThat(decision.redacted()).isTrue();
        assertThat(decision.output()).isEqualTo("call [PHONE]");
    }

    @Test
    @DisplayName("命中阻断时返回 ABORT")
    void abortsWhenEnabled() {
        final StreamGuardService service = new StreamGuardService(
                props(true, List.of(), List.of(pattern("secret", "sk-[a-z0-9]{8,}", null))), null);
        service.rebuild();

        assertThat(service.evaluate("sk-abcdef12").aborting()).isTrue();
    }

    @Test
    @DisplayName("命中的处置按动作与规则名计数")
    void recordsMetrics() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        final StreamGuardService service = new StreamGuardService(
                props(true,
                        List.of(pattern("phone", "\\d{11}", "[PHONE]")),
                        List.of(pattern("secret", "sk-[a-z0-9]{8,}", null))),
                registry);
        service.rebuild();

        service.evaluate("13800138000");
        service.evaluate("13800138000");
        service.evaluate("sk-abcdef12");
        service.evaluate("nothing to see");

        assertThat(registry.find("jairouter.guard.decisions")
                .tag("action", "REDACT").tag("policy", "phone").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.find("jairouter.guard.decisions")
                .tag("action", "ABORT").tag("policy", "secret").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.find("jairouter.guard.decisions")
                .tag("action", "ALLOW").counter())
                .as("放行不计数，避免高基数噪音")
                .isNull();
    }

    @Test
    @DisplayName("判定器抛异常时降级为放行，不打断流")
    void degradesToAllowOnFailure() {
        final StreamGuardService service = new StreamGuardService(
                props(true, List.of(pattern("phone", "\\d{11}", "[PHONE]")), List.of()), null);
        service.rebuild();
        // 替换成必定抛异常的链：护栏内部错误不得冒泡到流上
        final ChunkGuard throwing = new ChunkGuard() {
            @Override
            public GuardDecision evaluate(final String chunk) {
                throw new IllegalStateException("boom");
            }

            @Override
            public String name() {
                return "throwing";
            }
        };
        ReflectionTestUtils.setField(service, "chain", new GuardChain(List.of(throwing)));

        final GuardDecision decision = service.evaluate("13800138000");

        assertThat(decision.action()).isEqualTo(GuardAction.ALLOW);
        assertThat(decision.output()).isEqualTo("13800138000");
    }
}
