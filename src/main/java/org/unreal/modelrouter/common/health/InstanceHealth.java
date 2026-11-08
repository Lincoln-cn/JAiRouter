package org.unreal.modelrouter.common.health;

/**
 * 实例健康状态（分级）。
 *
 * <p>这个枚举是健康的**唯一词表**：数据库列、内存状态、API 响应、控制台展示都只用它。
 * 引入它之前，健康存在两处且默认值相反 —— 数据库里 {@code UNKNOWN} 表示「不知道」，
 * 而路由用的 {@code BitSet} 默认 {@code true} 表示「健康」（见 {@code #1004} 的排查记录）。</p>
 *
 * <p>各档的语义边界：</p>
 * <ul>
 *   <li>{@link #UNKNOWN} —— 还没被判过（冷启动、刚加实例）。**怎么对待由路由策略决定**，
 *       不在这里假设。</li>
 *   <li>{@link #HEALTHY} —— 所有探针都 UP。</li>
 *   <li>{@link #DEGRADED} —— 能服务，但有已知降级因素（适配器解析不出、熔断开着）。
 *       这类问题通常是**配置**造成的，实例本身仍可能返回正确结果。</li>
 *   <li>{@link #UNHEALTHY} —— 不能服务（端口不可达、应用层不响应、连续失败）。</li>
 * </ul>
 *
 * <p><b>为什么不把「能不能路由」写进这个枚举</b>：健康是事实，路由是策略。同一个
 * {@code DEGRADED} 实例，有人希望照样打过去（宁可慢也别 503），有人希望跳过。
 * 把它们混在一起，就是上一版方案里「误判一个配置问题导致全量不可用」的根源。</p>
 *
 * @since v3.8.0
 */
public enum InstanceHealth {

    UNKNOWN,
    HEALTHY,
    DEGRADED,
    UNHEALTHY
}
