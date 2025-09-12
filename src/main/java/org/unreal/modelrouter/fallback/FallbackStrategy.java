package org.unreal.modelrouter.fallback;

/**
 * 降级策略接口
 */
public interface FallbackStrategy<T> {
    /**
     * 执行降级逻辑
     * @param cause 引起降级的异常或原因
     * @return 降级返回的结果
     */
    T fallback(Exception cause);
}
