package org.unreal.modelrouter.common.cluster;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 多副本共享态自检门闩（#162）。
 *
 * <p>仓库已具备跨副本共享态的完整抽象（Redis 实现），但开关默认全关。多副本部署下若
 * 这些开关未开启，各副本会退化成互不知情的单机实例——在副本 A 吊销的令牌在副本 B 仍被
 * 放行、API Key 与权限缓存不跨副本失效、配额计数按副本数放大。本类负责识别
 * 「多副本 + 共享态开关未开」这一组合，并按配置告警或 fail-fast。</p>
 *
 * <p>触发前提是<b>确认处于多副本环境</b>（见 {@link #detectMultiReplica}）：单副本部署
 * 保持完全静默，不会误伤——共享态开关在单机下关闭本就是正常状态。</p>
 *
 * <p>与 {@code StartupSecurityGate} 同一约定：核心判定做成静态纯函数，便于单元测试。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.3
 */
public final class MultiReplicaSharedStateGate {

    /** 共享态检查模式。 */
    public enum Mode {
        /** 不执行检查。 */
        OFF,
        /** 多副本且共享态开关缺失时告警（默认）。 */
        WARN,
        /** 多副本且共享态开关缺失时 fail-fast，禁止带病上线。 */
        FAIL
    }

    /**
     * 一个跨副本共享态开关及其缺失后果。
     *
     * @param key         配置键
     * @param consequence 未开启时的后果
     */
    public record SharedStateSwitch(String key, String consequence) {
    }

    /**
     * 需要跨副本共享的开关清单。
     *
     * <p>顺序固定，便于告警消息与文档对齐。</p>
     */
    public static final List<SharedStateSwitch> SHARED_STATE_SWITCHES = List.of(
            new SharedStateSwitch(
                    "jairouter.security.jwt.blacklist.redis.enabled",
                    "在副本 A 吊销的令牌在副本 B 仍被放行"),
            new SharedStateSwitch(
                    "jairouter.security.jwt.persistence.redis.enabled",
                    "令牌状态各副本独立，重启后可能丢失已签发状态"),
            new SharedStateSwitch(
                    "jairouter.security.cache.redis.enabled",
                    "API Key 缓存各副本独立，吊销/新建不跨副本生效"),
            new SharedStateSwitch(
                    "jairouter.quota.distributed.enabled",
                    "配额计数每副本一份，实际放行量约为配置值 × 副本数"),
            new SharedStateSwitch(
                    "jairouter.persistence.redis.enabled",
                    "状态持久化退坡到本机 H2/文件，不做跨节点共享"));

    private MultiReplicaSharedStateGate() {
    }

    /**
     * 解析检查模式，无法识别时回退 {@link Mode#WARN}。
     *
     * @param raw 配置原文，可为 null
     * @return 解析结果，绝不返回 null
     */
    public static Mode parseMode(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Mode.WARN;
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mode.WARN;
        }
    }

    /**
     * 判定是否运行在多副本环境。
     *
     * <p>显式 {@code replicas} 优先：{@code >1} 判为多副本，{@code 1} 判为单副本。
     * 未显式声明（{@code 0}）时才回退环境探测——存在 {@code KUBERNETES_SERVICE_HOST}
     * （K8s 自动注入）或 {@code POD_NAME}（downward API）即判为多副本。</p>
     *
     * <p>刻意<b>不</b>以 {@code HOSTNAME} 为据：普通 Docker 容器也会设置它，会把单机
     * 部署误判为多副本，进而产生无意义的告警。</p>
     *
     * @param replicas 显式副本数，{@code <=0} 表示未知
     * @param env      环境变量快照（便于测试注入）
     * @return 是否多副本
     */
    public static boolean detectMultiReplica(final int replicas, final Map<String, String> env) {
        if (replicas > 1) {
            return true;
        }
        if (replicas == 1) {
            return false;
        }
        if (env == null) {
            return false;
        }
        return isSet(env.get("KUBERNETES_SERVICE_HOST")) || isSet(env.get("POD_NAME"));
    }

    /**
     * 解析副本数，无法解析时返回 0（表示未知）。
     *
     * @param raw 配置原文，可为 null
     * @return 副本数，{@code <=0} 表示未知
     */
    public static int parseReplicas(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 评估共享态配置。
     *
     * @param multiReplica 是否多副本
     * @param switches     配置键 → 是否已开启；缺失或非 {@code true} 一律视为未开启
     * @return 评估结果
     */
    public static Assessment assess(final boolean multiReplica, final Map<String, Boolean> switches) {
        List<String> disabled = new ArrayList<>();
        for (SharedStateSwitch item : SHARED_STATE_SWITCHES) {
            if (switches == null || !Boolean.TRUE.equals(switches.get(item.key()))) {
                disabled.add(item.key());
            }
        }
        return new Assessment(multiReplica, List.copyOf(disabled));
    }

    /**
     * 按模式处置：{@link Mode#FAIL} 且需要关注时抛出异常。
     *
     * @param mode       检查模式
     * @param assessment 评估结果
     * @throws IllegalStateException 当模式为 {@link Mode#FAIL} 且确有开关未开
     */
    public static void enforce(final Mode mode, final Assessment assessment) {
        if (mode == Mode.FAIL && assessment.needsAttention()) {
            throw new IllegalStateException(describe(assessment));
        }
    }

    /**
     * 生成可读的告警消息，含每一项的缺失后果与修复指引。
     *
     * @param assessment 评估结果
     * @return 多行消息
     */
    public static String describe(final Assessment assessment) {
        StringBuilder sb = new StringBuilder();
        sb.append("检测到多副本部署，但以下跨副本共享态开关未开启，各副本将退化为互不知情的单机实例：");
        for (String key : assessment.disabledSwitches()) {
            sb.append(System.lineSeparator()).append("  - ").append(key);
            consequenceOf(key).ifPresent(consequence -> sb.append(" → ").append(consequence));
        }
        sb.append(System.lineSeparator())
                .append("单副本部署可忽略；多副本请开启上述开关（详见 docs/zh/deployment/multi-replica.md），")
                .append("或将 jairouter.cluster.shared-state-check.mode 设为 OFF 显式接受该风险。");
        return sb.toString();
    }

    private static Optional<String> consequenceOf(final String key) {
        return SHARED_STATE_SWITCHES.stream()
                .filter(item -> item.key().equals(key))
                .map(SharedStateSwitch::consequence)
                .findFirst();
    }

    private static boolean isSet(final String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 共享态评估结果。
     *
     * @param multiReplica     是否多副本环境
     * @param disabledSwitches 未开启的共享态开关（配置键），按清单顺序
     */
    public record Assessment(boolean multiReplica, List<String> disabledSwitches) {

        /**
         * 是否需要关注：仅当确认多副本且确有开关未开。
         *
         * @return 是否应告警/fail-fast
         */
        public boolean needsAttention() {
            return multiReplica && !disabledSwitches.isEmpty();
        }
    }
}
