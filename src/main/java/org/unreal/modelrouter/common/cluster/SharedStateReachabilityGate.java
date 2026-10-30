package org.unreal.modelrouter.common.cluster;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 共享态依赖可达性门闩（#825）。
 *
 * <p>{@link MultiReplicaSharedStateGate} 只判断「共享态开关开没开」，不判断「开关背后的
 * Redis 在不在」。{@code staging} / {@code prod} 上把
 * {@code jairouter.security.jwt.persistence.redis.enabled} 设成 {@code true} 却没部署 Redis 时，
 * 应用能正常启动，直到第一次写令牌状态才报错 —— 那时用户已经在登录了。</p>
 *
 * <p>本类补上启动期的那一问：<b>已开启的 Redis 支撑开关，其依赖是否连得上</b>。只在
 * {@code staging} / {@code prod} 生效（{@code dev} / {@code test} 不做该检查），默认
 * {@link Mode#FAIL} —— 带病启动比启动失败更难排查。</p>
 *
 * <p>与既有门闩同一约定：判定做成静态纯函数，便于单元测试；只有探测动作
 * （{@link #isReachable}）带 IO。</p>
 *
 * @author JAiRouter Team
 * @since 3.5.7
 */
public final class SharedStateReachabilityGate {

    /** 可达性检查模式。 */
    public enum Mode {
        /** 不检查（等价于显式接受「开关开着但依赖不在」的风险）。 */
        OFF,
        /** 连不上只告警，继续启动。 */
        WARN,
        /** 连不上直接 fail-fast（默认）。 */
        FAIL
    }

    /** 检查模式配置键。 */
    public static final String MODE_KEY = "jairouter.cluster.shared-state-check.redis-reachability";

    /** 探测超时配置键。 */
    public static final String TIMEOUT_KEY = "jairouter.cluster.shared-state-check.redis-timeout-ms";

    /** 默认探测超时（毫秒）—— 启动期最多多等 2 秒。 */
    public static final long DEFAULT_TIMEOUT_MS = 2000L;

    /** 探测超时的下限与上限，避免配置成 0 或一分钟级。 */
    private static final long MIN_TIMEOUT_MS = 200L;
    private static final long MAX_TIMEOUT_MS = 30000L;

    /** 需要做依赖检查的 profile（小写比较）。 */
    private static final List<String> GUARDED_PROFILES = List.of("staging", "prod", "production");

    private SharedStateReachabilityGate() {
    }

    /**
     * 解析检查模式。
     *
     * <p>未配置时回落到 {@link Mode#FAIL}：默认就防住「staging 开了 Redis 却没部署」；
     * 值写得无法识别时回落到 {@link Mode#WARN} —— 打错字不该让整个集群起不来。</p>
     *
     * @param raw 配置原文，可为 null
     * @return 解析结果，绝不返回 null
     */
    public static Mode parseMode(final String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Mode.FAIL;
        }
        try {
            return Mode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Mode.WARN;
        }
    }

    /**
     * 当前 profile 是否需要做依赖检查（仅 staging / prod）。
     *
     * @param activeProfiles 生效的 profile
     * @return true 表示需要检查
     */
    public static boolean appliesTo(final String[] activeProfiles) {
        if (activeProfiles == null) {
            return false;
        }
        for (String profile : activeProfiles) {
            if (profile == null) {
                continue;
            }
            final String normalized = profile.trim().toLowerCase(Locale.ROOT);
            if (GUARDED_PROFILES.contains(normalized)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找出已开启、且依赖 Redis 的共享态开关。
     *
     * <p>沿用 {@link MultiReplicaSharedStateGate#SHARED_STATE_SWITCHES} 的清单 —— 那份清单
     * 就是「跨副本共享态」的定义，新增开关时只需改一处。</p>
     *
     * @param switches 配置键 → 是否开启
     * @return 已开启的开关键，按清单顺序；无则空列表
     */
    public static List<String> enabledSwitches(final Map<String, Boolean> switches) {
        final List<String> enabled = new ArrayList<>();
        if (switches == null) {
            return enabled;
        }
        for (MultiReplicaSharedStateGate.SharedStateSwitch item
                : MultiReplicaSharedStateGate.SHARED_STATE_SWITCHES) {
            if (Boolean.TRUE.equals(switches.get(item.key()))) {
                enabled.add(item.key());
            }
        }
        return enabled;
    }

    /**
     * 解析探测超时，越界时钳到 [200ms, 30s]，无法解析时取默认值。
     *
     * @param raw 配置原文，可为 null
     * @return 毫秒数，保证落在区间内
     */
    public static long parseTimeoutMs(final String raw) {
        long parsed = DEFAULT_TIMEOUT_MS;
        if (raw != null && !raw.trim().isEmpty()) {
            try {
                parsed = Long.parseLong(raw.trim());
            } catch (NumberFormatException e) {
                parsed = DEFAULT_TIMEOUT_MS;
            }
        }
        return Math.max(MIN_TIMEOUT_MS, Math.min(MAX_TIMEOUT_MS, parsed));
    }

    /**
     * 探测 Redis 端口是否可连接。
     *
     * <p>只做 TCP 建连：启动期的目标是「依赖在不在」，不是「Redis 是否健康」；
     * 建连成功即认为部署完成，真正的连通性与鉴权交给首次使用时的既有错误处理。</p>
     *
     * @param host      主机
     * @param port      端口
     * @param timeoutMs 超时毫秒
     * @return true 表示建连成功
     */
    public static boolean isReachable(final String host, final int port, final long timeoutMs) {
        if (host == null || host.trim().isEmpty() || port <= 0 || port > 65535) {
            return false;
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), port), (int) Math.min(Integer.MAX_VALUE, timeoutMs));
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * 生成可直接贴进 issue 的失败消息。
     *
     * @param host            配置的 Redis 主机
     * @param port            配置的 Redis 端口
     * @param enabledSwitches 已开启的开关
     * @param activeProfiles  生效 profile
     * @return 多行消息
     */
    public static String describe(final String host, final int port,
            final List<String> enabledSwitches, final String[] activeProfiles) {
        final StringBuilder sb = new StringBuilder();
        sb.append("已开启的共享态开关依赖 Redis，但 ").append(host).append(':').append(port)
                .append(" 在启动期连不上（profile=")
                .append(activeProfiles == null ? "unknown" : String.join(",", activeProfiles))
                .append("）。");
        sb.append(System.lineSeparator()).append("已开启的开关：");
        for (String key : enabledSwitches) {
            sb.append(System.lineSeparator()).append("  - ").append(key);
        }
        sb.append(System.lineSeparator())
                .append("请部署 Redis 并把 spring.data.redis.host/port 指向它；若确实要在无 Redis 的环境里运行，")
                .append("把这些开关设为 false，或将 ")
                .append(MODE_KEY)
                .append(" 设为 WARN / OFF 显式接受该风险。");
        return sb.toString();
    }
}
