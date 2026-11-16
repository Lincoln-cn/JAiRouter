package org.unreal.modelrouter.monitor.monitoring.config;

import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.ApiException;

/**
 * 监控配置热改时的取值校验（issue #1172）.
 *
 * <p><b>为什么要单独抽出来</b>：同一组「热改采样率 / 采集性能」的写入口有两条 —— 控制台走的
 * {@code PUT /api/monitoring/config/sampling|performance}，以及运维用的
 * {@code POST /actuator/metrics/sampling|performance-config}。原先只有前者校验取值，后者
 * 完全不校验：一条能把采样率设成 {@code 5}（等于全量采集，指标量级失控）、把
 * {@code bufferSize} 设成小于 {@code batchSize}（异步处理取不到整批，直接空转）的路径存在，
 * 校验面就必须收在同一处，而不是各写一份。</p>
 *
 * <p>边界与启动期 {@code MonitoringConfigurationValidator} 保持一致：采样率 0~1、批大小 ≥ 1、
 * 缓冲区 ≥ 批大小。这里只管「能不能写入」，不负责把值写回配置对象。</p>
 *
 * <p>抛出的 {@link ApiException} 带 {@link ErrorCode#INVALID_REQUEST}，控制台那条链路会由
 * {@code ApiExceptions.wrap} 归一成 400；{@code /actuator} 那条链路由调用方自己转成 400
 * （见 {@code MetricsManagementController}），不依赖全局异常处理器。</p>
 */
public final class MonitoringHotReloadValidator {

    private MonitoringHotReloadValidator() {
    }

    /**
     * 取「请求值，缺省回落到当前值」。
     *
     * @param value    请求里传来的值（可为 {@code null}，表示「不改这一项」）
     * @param fallback 当前生效值
     * @return 目标值
     */
    public static double valueOr(final Double value, final double fallback) {
        return value != null ? value : fallback;
    }

    /**
     * 校验采样率必须落在 0~1（含边界）。
     *
     * @param name  字段名（用于报错定位，如 {@code request-metrics}）
     * @param value 采样率
     * @throws ApiException 越界时抛 {@link ErrorCode#INVALID_REQUEST}
     */
    public static void requireSamplingRate(final String name, final double value) {
        if (value < 0.0 || value > 1.0) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST.code(),
                    "采样率必须在 0~1 之间: " + name + "=" + value);
        }
    }

    /**
     * 校验采集性能边界：批大小 ≥ 1，且缓冲区 ≥ 批大小。
     *
     * @param batchSize  批大小
     * @param bufferSize 缓冲区大小
     * @throws ApiException 越界时抛 {@link ErrorCode#INVALID_REQUEST}
     */
    public static void requirePerformanceBounds(final int batchSize, final int bufferSize) {
        if (batchSize < 1) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST.code(), "批大小必须 ≥ 1: " + batchSize);
        }
        if (bufferSize < batchSize) {
            throw ApiException.of(ErrorCode.INVALID_REQUEST.code(),
                    "缓冲区必须 ≥ 批大小: bufferSize=" + bufferSize + ", batchSize=" + batchSize);
        }
    }
}
