package org.unreal.modelrouter.router.rule;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.router.rule.model.RuleStat;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RuleStatsService 命中统计测试
 */
@DisplayName("RuleStatsService 命中统计测试")
class RuleStatsServiceTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RuleStatsService service = new RuleStatsService(meterRegistry, new MonitoringProperties());

    @Test
    @DisplayName("recordHit 递增计数(指标名 jairouter_rule_hits_total, 按 ruleId+actionType 系列)")
    void recordHit_incrementsCounter() {
        service.recordHit("rule-1", "TARGET_MODEL");
        service.recordHit("rule-1", "TARGET_MODEL");
        service.recordHit("rule-2", "LB_STRATEGY");

        var counters = meterRegistry.find("jairouter_rule_hits_total").counters();
        assertEquals(2, counters.size());

        double rule1 = counters.stream()
                .filter(c -> "rule-1".equals(c.getId().getTag("ruleId"))
                        && "TARGET_MODEL".equals(c.getId().getTag("actionType")))
                .map(Counter::count)
                .findFirst()
                .orElseThrow();
        assertEquals(2.0, rule1);
    }

    @Test
    @DisplayName("getStats 聚合各规则命中数")
    void getStats_aggregatesByRuleAndAction() {
        service.recordHit("rule-1", "TARGET_MODEL");
        service.recordHit("rule-1", "TARGET_MODEL");
        service.recordHit("rule-2", "LB_STRATEGY");

        List<RuleStat> stats = service.getStats();
        assertEquals(2, stats.size());

        RuleStat rule1 = stats.stream().filter(s -> s.getRuleId().equals("rule-1")).findFirst().orElseThrow();
        assertEquals(2, rule1.getHits());
        assertEquals("TARGET_MODEL", rule1.getActionType());

        RuleStat rule2 = stats.stream().filter(s -> s.getRuleId().equals("rule-2")).findFirst().orElseThrow();
        assertEquals(1, rule2.getHits());
        assertTrue(rule2.getActionType().equals("LB_STRATEGY"));
    }
}
