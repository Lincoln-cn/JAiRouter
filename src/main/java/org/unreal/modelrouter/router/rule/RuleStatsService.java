package org.unreal.modelrouter.router.rule;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.config.core.MonitoringProperties;
import org.unreal.modelrouter.router.rule.model.RuleStat;

import java.util.ArrayList;
import java.util.List;

/**
 * 规则命中统计服务
 * 命中计数挂载在决策生效点(ModelServiceRegistry.selectInstance),dry-run 与适配器二次求值不计数
 */
@Service
public class RuleStatsService {

    public static final String METRIC_NAME_SUFFIX = "rule_hits_total";

    private final MeterRegistry meterRegistry;
    private final String metricPrefix;

    public RuleStatsService(final MeterRegistry meterRegistry, final MonitoringProperties monitoringProperties) {
        this.meterRegistry = meterRegistry;
        String prefix = monitoringProperties.getPrefix();
        this.metricPrefix = (prefix != null && !prefix.isEmpty()) ? prefix + "_" : "";
    }

    /**
     * 记录规则命中
     */
    public void recordHit(final String ruleId, final String actionType) {
        Counter.builder(metricPrefix + METRIC_NAME_SUFFIX)
                .tag("ruleId", ruleId)
                .tag("actionType", actionType)
                .description("Number of requests matched by routing rule")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 聚合全部规则的命中计数(按 ruleId+actionType 系列)
     */
    public List<RuleStat> getStats() {
        String metricName = metricPrefix + METRIC_NAME_SUFFIX;
        List<RuleStat> stats = new ArrayList<>();
        meterRegistry.find(metricName).counters().forEach(counter -> {
            String ruleId = counter.getId().getTag("ruleId");
            String actionType = counter.getId().getTag("actionType");
            if (ruleId != null) {
                stats.add(new RuleStat(ruleId, null, actionType, (long) counter.count()));
            }
        });
        return stats;
    }
}
