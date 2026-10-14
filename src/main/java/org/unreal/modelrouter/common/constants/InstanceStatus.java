package org.unreal.modelrouter.common.constants;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 实例**生命周期状态**词表（issue #354）。
 *
 * <p>只有两个取值：{@link #ACTIVE}（可被路由）与 {@link #INACTIVE}（显式停用）。
 * **健康是另一个维度**，由 {@code ServiceInstanceEntity.healthStatus}
 * （{@code HEALTHY} / {@code UNHEALTHY} / {@code UNKNOWN}）承担 ——
 * 把健康结果写进 {@code status}（例如 {@code "healthy"}）会让实例被可用性过滤
 * 整体排除，而过滤只认 {@code active}（见 {@code SelectInstanceOptimizer}）。</p>
 *
 * <p><b>为什么是本类</b>：写入侧原先不校验（{@code "HEALTHY"} 被原样落库）、
 * 路由过滤与错误分类器各自写了一遍 {@code "active".equalsIgnoreCase(...)}、
 * 而 {@code HttpConfigPushService} 又是大小写精确匹配 —— 同一条词表在四处各自为政。
 * 现在判据统一走这里。</p>
 *
 * <p>规范值取**大写**：与既有种子数据（{@code JpaDatabaseInitializer}、{@code ConfigSyncService}）
 * 及 {@code HttpConfigPushService} 的大小写精确匹配保持一致，不改变既有数据与判据行为。
 * 读取侧一律大小写不敏感（历史数据里两种大小写都存在）。</p>
 *
 * @since v3.3.3
 */
public final class InstanceStatus {

    /** 可被路由的实例状态。 */
    public static final String ACTIVE = "ACTIVE";

    /** 显式停用的实例状态。 */
    public static final String INACTIVE = "INACTIVE";

    /** 允许写入的取值（保持声明顺序，供错误消息与文档引用）。 */
    public static final List<String> ALLOWED = List.of(ACTIVE, INACTIVE);

    private static final Set<String> ALLOWED_UPPER = Set.of(ACTIVE, INACTIVE);

    private InstanceStatus() {
    }

    /**
     * 是否可被路由（大小写不敏感；{@code null} 视为不可路由）。
     *
     * @param status 实例的 status 取值
     * @return 是否为 {@code active}
     */
    public static boolean isActive(final String status) {
        return status != null && ACTIVE.equalsIgnoreCase(status.trim());
    }

    /**
     * 把外部传入的 status 归一为规范值。
     *
     * @param status 外部传入值；{@code null} 返回 {@code null}（默认值由调用方决定）
     * @return 规范值（{@link #ACTIVE} 或 {@link #INACTIVE}）
     * @throws IllegalArgumentException 取值不在词表内（经 {@code ApiExceptions.wrap} 映射为
     *                                  400 {@code INVALID_REQUEST}）
     */
    public static String normalizeOrThrow(final String status) {
        if (status == null) {
            return null;
        }
        final String trimmed = status.trim();
        for (String allowed : ALLOWED) {
            if (allowed.equalsIgnoreCase(trimmed)) {
                return allowed;
            }
        }
        throw new IllegalArgumentException(
                "非法的实例 status 取值: '" + status + "'；只接受 " + ALLOWED + "（大小写不敏感）。"
                        + "健康状态请通过 healthStatus 表达 —— 写入非 active 取值会让该实例"
                        + "被可用性过滤整体排除，客户端随后会收到「找不到该模型」的 404。");
    }

    /**
     * 取值是否在词表内（大小写不敏感）。
     *
     * @param status 待检查取值
     * @return 是否合法
     */
    public static boolean isAllowed(final String status) {
        return status != null && ALLOWED_UPPER.contains(status.trim().toUpperCase(Locale.ROOT));
    }
}
