package org.unreal.modelrouter.common.cluster;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.unreal.modelrouter.common.cluster.MultiReplicaSharedStateGate.Assessment;
import org.unreal.modelrouter.common.cluster.MultiReplicaSharedStateGate.Mode;
import org.unreal.modelrouter.common.cluster.MultiReplicaSharedStateGate.SharedStateSwitch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link MultiReplicaSharedStateGate} 单元测试（#162）。
 *
 * <p>重点覆盖两个容易出错、后果又重的判定：<b>不得把单机 Docker 误判为多副本</b>
 * （会产生无意义告警），以及<b>单副本环境永不告警</b>（共享态开关在单机下关闭本就是正常状态）。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
class MultiReplicaSharedStateGateTest {

    private static Map<String, Boolean> allEnabled() {
        Map<String, Boolean> switches = new LinkedHashMap<>();
        for (SharedStateSwitch item : MultiReplicaSharedStateGate.SHARED_STATE_SWITCHES) {
            switches.put(item.key(), Boolean.TRUE);
        }
        return switches;
    }

    private static Map<String, String> env(final String... keyValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    // ---------- parseMode ----------

    @Test
    @DisplayName("模式未配置或无法识别时回退 WARN，而非静默关闭检查")
    void parseModeFallsBackToWarn() {
        assertEquals(Mode.WARN, MultiReplicaSharedStateGate.parseMode(null));
        assertEquals(Mode.WARN, MultiReplicaSharedStateGate.parseMode(""));
        assertEquals(Mode.WARN, MultiReplicaSharedStateGate.parseMode("   "));
        assertEquals(Mode.WARN, MultiReplicaSharedStateGate.parseMode("bogus"));
    }

    @Test
    @DisplayName("模式解析大小写与空白不敏感")
    void parseModeIsCaseInsensitive() {
        assertEquals(Mode.OFF, MultiReplicaSharedStateGate.parseMode("off"));
        assertEquals(Mode.OFF, MultiReplicaSharedStateGate.parseMode("OFF"));
        assertEquals(Mode.OFF, MultiReplicaSharedStateGate.parseMode("  Off  "));
        assertEquals(Mode.WARN, MultiReplicaSharedStateGate.parseMode("warn"));
        assertEquals(Mode.FAIL, MultiReplicaSharedStateGate.parseMode("fail"));
        assertEquals(Mode.FAIL, MultiReplicaSharedStateGate.parseMode("FAIL"));
    }

    // ---------- parseReplicas ----------

    @Test
    @DisplayName("副本数无法解析时返回 0（未知），交由环境探测兜底")
    void parseReplicasHandlesInvalidInput() {
        assertEquals(0, MultiReplicaSharedStateGate.parseReplicas(null));
        assertEquals(0, MultiReplicaSharedStateGate.parseReplicas(""));
        assertEquals(0, MultiReplicaSharedStateGate.parseReplicas("abc"));
        assertEquals(1, MultiReplicaSharedStateGate.parseReplicas("1"));
        assertEquals(3, MultiReplicaSharedStateGate.parseReplicas(" 3 "));
    }

    // ---------- detectMultiReplica ----------

    @Test
    @DisplayName("显式副本数优先于环境探测")
    void explicitReplicasWinOverEnvironment() {
        assertTrue(MultiReplicaSharedStateGate.detectMultiReplica(3, env()));
        // 显式声明单副本时，即便存在 K8s 环境变量也判定为单副本
        assertFalse(MultiReplicaSharedStateGate.detectMultiReplica(
                1, env("KUBERNETES_SERVICE_HOST", "10.0.0.1", "POD_NAME", "jairouter-0")));
    }

    @Test
    @DisplayName("未显式声明副本数时按 K8s 环境变量探测")
    void detectsKubernetesEnvironment() {
        assertTrue(MultiReplicaSharedStateGate.detectMultiReplica(0, env("KUBERNETES_SERVICE_HOST", "10.0.0.1")));
        assertTrue(MultiReplicaSharedStateGate.detectMultiReplica(0, env("POD_NAME", "jairouter-7d9f-x2k")));
    }

    @Test
    @DisplayName("HOSTNAME 不作为多副本依据——普通 Docker 容器也会设置它")
    void hostnameIsNotEvidenceOfReplicas() {
        assertFalse(MultiReplicaSharedStateGate.detectMultiReplica(
                0, env("HOSTNAME", "a1b2c3d4e5f6", "INSTANCE_ID", "node-1")));
    }

    @Test
    @DisplayName("空白环境变量不算数，空环境变量表判为单副本")
    void blankEnvironmentValuesAreIgnored() {
        assertFalse(MultiReplicaSharedStateGate.detectMultiReplica(0, env("POD_NAME", "   ")));
        assertFalse(MultiReplicaSharedStateGate.detectMultiReplica(0, env()));
        assertFalse(MultiReplicaSharedStateGate.detectMultiReplica(0, null));
    }

    // ---------- assess ----------

    @Test
    @DisplayName("全部开关开启时无缺口")
    void assessWithAllSwitchesEnabled() {
        Assessment assessment = MultiReplicaSharedStateGate.assess(true, allEnabled());
        assertTrue(assessment.disabledSwitches().isEmpty());
        assertFalse(assessment.needsAttention());
    }

    @Test
    @DisplayName("缺失的开关被逐个列出，且顺序与清单一致")
    void assessListsMissingSwitchesInDeclaredOrder() {
        Map<String, Boolean> switches = allEnabled();
        String first = MultiReplicaSharedStateGate.SHARED_STATE_SWITCHES.get(0).key();
        switches.remove(first);

        Assessment assessment = MultiReplicaSharedStateGate.assess(true, switches);
        assertEquals(List.of(first), assessment.disabledSwitches());
    }

    @Test
    @DisplayName("开关缺失（null）一律视为未开启，不假定默认值")
    void assessTreatsNullAndNonTrueAsDisabled() {
        Assessment nullSwitches = MultiReplicaSharedStateGate.assess(true, null);
        assertEquals(MultiReplicaSharedStateGate.SHARED_STATE_SWITCHES.size(),
                nullSwitches.disabledSwitches().size());

        Map<String, Boolean> switches = allEnabled();
        String first = MultiReplicaSharedStateGate.SHARED_STATE_SWITCHES.get(0).key();
        switches.put(first, Boolean.FALSE);
        assertEquals(List.of(first),
                MultiReplicaSharedStateGate.assess(true, switches).disabledSwitches());
    }

    @Test
    @DisplayName("单副本即便开关全关也不告警——这是单机部署的正常状态")
    void singleReplicaNeverNeedsAttention() {
        Assessment assessment = MultiReplicaSharedStateGate.assess(false, null);
        assertFalse(assessment.multiReplica());
        assertFalse(assessment.disabledSwitches().isEmpty());
        assertFalse(assessment.needsAttention());
    }

    // ---------- enforce ----------

    @Test
    @DisplayName("FAIL 模式在多副本缺口时抛异常")
    void failModeThrowsOnGap() {
        Assessment assessment = MultiReplicaSharedStateGate.assess(true, null);
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> MultiReplicaSharedStateGate.enforce(Mode.FAIL, assessment));
        assertTrue(error.getMessage().contains("多副本"));
    }

    @Test
    @DisplayName("WARN 模式只告警不抛异常")
    void warnModeDoesNotThrow() {
        Assessment assessment = MultiReplicaSharedStateGate.assess(true, null);
        MultiReplicaSharedStateGate.enforce(Mode.WARN, assessment);
    }

    @Test
    @DisplayName("FAIL 模式在开关齐全时放行")
    void failModePassesWhenAllEnabled() {
        MultiReplicaSharedStateGate.enforce(Mode.FAIL, MultiReplicaSharedStateGate.assess(true, allEnabled()));
    }

    @Test
    @DisplayName("FAIL 模式在单副本时放行")
    void failModePassesForSingleReplica() {
        MultiReplicaSharedStateGate.enforce(Mode.FAIL, MultiReplicaSharedStateGate.assess(false, null));
    }

    // ---------- describe ----------

    @Test
    @DisplayName("告警消息含缺失开关键、缺失后果与关闭检查的逃生方式")
    void describeExplainsImpactAndEscapeHatch() {
        String message = MultiReplicaSharedStateGate.describe(
                MultiReplicaSharedStateGate.assess(true, null));
        assertTrue(message.contains("jairouter.security.jwt.blacklist.redis.enabled"));
        assertTrue(message.contains("在副本 A 吊销的令牌在副本 B 仍被放行"));
        assertTrue(message.contains("multi-replica.md"));
        assertTrue(message.contains("OFF"));
    }
}
