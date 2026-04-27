package org.unreal.modelrouter.version.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.version.VersionContext;

/**
 * 顺序递增版本号生成策略
 * 生成简单的自增整数版本号：1, 2, 3, ...
 * 特点：
 * - 版本号连续，便于人类阅读和理解
 * - 能直观看出版本数量
 * - 简单易维护
 */
@Component
public class SequentialVersionGenerator implements VersionGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SequentialVersionGenerator.class);

    private static final String STRATEGY_NAME = "SEQUENTIAL";

    @Override
    public int generateNextVersion(final VersionContext context) {
        // 获取当前最大版本号，如果没有则返回 0
        int maxVersion = context.getMaxVersion();

        // 简单递增，确保版本号连续
        int nextVersion = maxVersion + 1;

        // 安全检查：如果由于某种原因版本号已存在，继续递增直到找到可用版本号
        int attempts = 0;
        int maxAttempts = 100;
        while (context.versionExists(nextVersion) && attempts < maxAttempts) {
            nextVersion++;
            attempts++;
            logger.warn("版本号 {} 已存在，尝试下一个版本号: {}", nextVersion - 1, nextVersion);
        }

        if (attempts >= maxAttempts) {
            throw new IllegalStateException(
                    String.format("无法生成可用版本号，已尝试 %d 次。当前最大版本: %d",
                            maxAttempts, maxVersion));
        }

        logger.debug("顺序递增策略生成版本号: {} (基于当前最大版本: {})", nextVersion, maxVersion);
        return nextVersion;
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public boolean isValidVersion(final int version) {
        // 顺序递增版本号应该是正整数
        return version > 0 && version < 100_000_000;
    }
}
