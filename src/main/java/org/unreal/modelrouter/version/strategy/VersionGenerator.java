package org.unreal.modelrouter.version.strategy;

import org.unreal.modelrouter.version.VersionContext;

/**
 * 版本号生成策略接口
 * 定义生成新版本号的契约，支持多种生成策略（自增、时间戳、语义化等）
 */
public interface VersionGenerator {

    /**
     * 生成下一个版本号
     *
     * @param context 版本生成上下文，包含当前版本、历史版本等信息
     * @return 生成的版本号
     */
    int generateNextVersion(VersionContext context);

    /**
     * 获取策略名称，用于日志和配置识别
     *
     * @return 策略名称
     */
    String getStrategyName();

    /**
     * 检查版本号是否由本策略生成（用于验证）
     *
     * @param version 待检查的版本号
     * @return true 如果该版本号符合本策略格式
     */
    boolean isValidVersion(int version);
}
