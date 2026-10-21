package org.unreal.modelrouter.auth.security.sanitization.impl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.unreal.modelrouter.auth.sanitization.SanitizationRuleEngine;
import org.unreal.modelrouter.auth.sanitization.impl.DefaultSanitizationRuleEngine;
import org.unreal.modelrouter.auth.sanitization.impl.OptimizedSanitizationRuleEngine;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * 锁定「运行时到底用哪个脱敏引擎」。
 *
 * <p>存在两个引擎：{@link DefaultSanitizationRuleEngine} 与带 {@code @Primary} 的
 * {@link OptimizedSanitizationRuleEngine}（{@code matchIfMissing = true}）。语料回归测试
 * 直接 new 的是 Default 引擎，因此必须有一条测试证明**生产默认装配的是 Optimized**
 * —— 否则在 Default 上做的行为修正（如 #561 的 JSON 键名豁免）在线上不生效。</p>
 */
class SanitizationRuleEngineWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(DefaultSanitizationRuleEngine.class, OptimizedSanitizationRuleEngine.class);

    @Test
    void optimizedEngineIsThePrimaryBeanByDefault() {
        runner.run(context -> assertInstanceOf(
                OptimizedSanitizationRuleEngine.class,
                context.getBean(SanitizationRuleEngine.class),
                "默认装配必须是 Optimized 引擎：两个实现都是 @Component，Optimized 带 @Primary 且 matchIfMissing=true"));
    }

    @Test
    void defaultEngineIsUsedWhenOptimizedSwitchTurnedOff() {
        runner.withPropertyValues("jairouter.security.sanitization.optimized=false")
                .run(context -> assertInstanceOf(
                        DefaultSanitizationRuleEngine.class,
                        context.getBean(SanitizationRuleEngine.class),
                        "显式关闭 optimized 开关后必须回落到 Default 引擎"));
    }
}
