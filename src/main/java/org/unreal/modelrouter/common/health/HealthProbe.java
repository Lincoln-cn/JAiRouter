package org.unreal.modelrouter.common.health;

import org.unreal.modelrouter.router.model.ModelRouterProperties;

/**
 * 一个健康探针。
 *
 * <p>把「健康检查」拆成独立的探针，是为了让新增一个健康维度不需要改核心判定 ——
 * 例如以后要判「上游的模型列表里有没有这个 model」「上游返回 401 说明 key 过期」，
 * 都是新增一个实现，不碰 {@link InstanceHealthEvaluator}、不碰路由。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *   <li>**必须允许被并发调用**，且不得阻塞超过探针自己的超时。</li>
 *   <li>**不得抛异常**。任何内部故障都应转成 {@link HealthSignal#unknown(String)}，
 *       否则一次探针的 bug 会让整个检查循环停摆。</li>
 *   <li>返回 {@link HealthSignal.Severity#FATAL} 的 DOWN 会让实例整体判为
 *       {@link InstanceHealth#UNHEALTHY}，调用方要谨慎。</li>
 * </ul>
 *
 * @since v3.8.0
 */
public interface HealthProbe {

    /**
     * 探针名字，用于日志与指标标签。
     *
     * @return 稳定的短名字，例如 {@code tcp} / {@code adapter}
     */
    String name();

    /**
     * 对一个实例做一次探测。
     *
     * @param serviceType 服务类型（用于日志与依赖查找）
     * @param instance    被探测的实例
     * @return 探针结论，永不为 {@code null}
     */
    HealthSignal probe(String serviceType, ModelRouterProperties.ModelInstance instance);
}
