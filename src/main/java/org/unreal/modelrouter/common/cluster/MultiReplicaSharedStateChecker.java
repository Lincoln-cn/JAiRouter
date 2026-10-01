package org.unreal.modelrouter.common.cluster;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多副本共享态启动自检（#162）。
 *
 * <p>在应用就绪时读取共享态开关配置并交给 {@link MultiReplicaSharedStateGate} 判定，
 * 结果既用于启动告警（或 fail-fast），也通过 {@link SharedStateHealthIndicator} 暴露到
 * {@code /actuator/health}。</p>
 *
 * <p>之所以在 {@link ApplicationReadyEvent} 而非 {@code @PostConstruct}：此时配置已完全
 * 绑定、环境变量已生效，判定基于最终值；{@code mode=FAIL} 抛出的异常会终止
 * {@code SpringApplication.run}，应用不会带病对外提供服务。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "jairouter.cluster.shared-state-check.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MultiReplicaSharedStateChecker implements ApplicationListener<ApplicationReadyEvent> {

    /** 检查模式配置键。 */
    public static final String MODE_KEY = "jairouter.cluster.shared-state-check.mode";

    /** WARN 模式下是否同时把健康端点置为 DOWN。 */
    public static final String FAIL_HEALTH_KEY = "jairouter.cluster.shared-state-check.fail-health";

    /** 副本数配置键。 */
    public static final String REPLICAS_KEY = "jairouter.cluster.replicas";

    private final Environment environment;

    /** 最近一次自检结果；未执行检查时为空评估。 */
    private volatile MultiReplicaSharedStateGate.Assessment assessment =
            new MultiReplicaSharedStateGate.Assessment(false, List.of());

    private volatile boolean failHealth;

    public MultiReplicaSharedStateChecker(final Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(final ApplicationReadyEvent event) {
        check();
    }

    /**
     * 执行自检。包级可见，便于测试直接驱动而不必启动完整上下文。
     */
    void check() {
        MultiReplicaSharedStateGate.Mode mode =
                MultiReplicaSharedStateGate.parseMode(environment.getProperty(MODE_KEY));
        failHealth = Boolean.parseBoolean(environment.getProperty(FAIL_HEALTH_KEY, "false"));

        if (mode == MultiReplicaSharedStateGate.Mode.OFF) {
            assessment = new MultiReplicaSharedStateGate.Assessment(false, List.of());
            log.debug("多副本共享态自检已关闭（{} = OFF）", MODE_KEY);
            return;
        }

        int replicas = MultiReplicaSharedStateGate.parseReplicas(environment.getProperty(REPLICAS_KEY));
        boolean multiReplica = MultiReplicaSharedStateGate.detectMultiReplica(replicas, System.getenv());
        assessment = MultiReplicaSharedStateGate.assess(multiReplica, readSwitches());

        if (!assessment.needsAttention()) {
            if (multiReplica) {
                log.info("多副本共享态自检通过：{} 项共享态开关均已开启",
                        MultiReplicaSharedStateGate.SHARED_STATE_SWITCHES.size());
            } else {
                log.debug("单副本环境，无需共享态自检");
            }
            return;
        }

        MultiReplicaSharedStateGate.enforce(mode, assessment);
        log.warn("{}{}", MultiReplicaSharedStateGate.describe(assessment),
                failHealth ? "（按配置，/actuator/health 将一并报告 DOWN）" : "");
    }

    private Map<String, Boolean> readSwitches() {
        Map<String, Boolean> switches = new LinkedHashMap<>();
        for (MultiReplicaSharedStateGate.SharedStateSwitch item
                : MultiReplicaSharedStateGate.SHARED_STATE_SWITCHES) {
            switches.put(item.key(), environment.getProperty(item.key(), Boolean.class, Boolean.FALSE));
        }
        return switches;
    }

    /**
     * 最近一次自检结果，供健康端点读取。
     *
     * @return 评估结果，绝不返回 null
     */
    public MultiReplicaSharedStateGate.Assessment getAssessment() {
        return assessment;
    }

    /**
     * WARN 模式下是否同时把健康端点置为 DOWN。
     *
     * @return 是否降级健康状态
     */
    public boolean isFailHealth() {
        return failHealth;
    }
}
