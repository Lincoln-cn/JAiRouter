package org.unreal.modelrouter.version.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.version.VersionContext;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间戳版本号生成策略
 * 基于当前时间生成版本号，格式：YYYYMMDDHHMMSS（14位数字）
 * 例如：20250407143025 表示 2025年4月7日 14:30:25
 * 特点：
 * - 版本号包含时间信息，直观知道版本创建时间
 * - 天然按时间排序
 * - 适合需要追踪版本创建时间的场景
 */
@Component
public class TimestampVersionGenerator implements VersionGenerator {

    private static final Logger logger = LoggerFactory.getLogger(TimestampVersionGenerator.class);

    private static final String STRATEGY_NAME = "TIMESTAMP";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public int generateNextVersion(VersionContext context) {
        // 基于当前时间生成版本号
        LocalDateTime now = LocalDateTime.now();
        String timestampStr = now.format(FORMATTER);

        int version;
        try {
            version = Integer.parseInt(timestampStr);
        } catch (NumberOverflowException e) {
            // 如果整数溢出（理论上不会发生，因为14位数字在int范围内），使用简化版时间戳
            String simplifiedTimestamp = now.format(DateTimeFormatter.ofPattern("yyMMddHHmm"));
            version = Integer.parseInt(simplifiedTimestamp);
        }

        // 安全检查：如果版本号已存在（同一秒内多次创建），添加秒级递增后缀
        int attempts = 0;
        int maxAttempts = 60; // 最多尝试60次（一分钟内）
        while (context.versionExists(version) && attempts < maxAttempts) {
            version++;
            attempts++;
        }

        if (attempts >= maxAttempts) {
            throw new IllegalStateException(
                    String.format("无法生成可用的时间戳版本号，已尝试 %d 次", maxAttempts));
        }

        if (attempts > 0) {
            logger.warn("时间戳版本号冲突，自动递增 {} 次，最终版本号: {}", attempts, version);
        }

        logger.debug("时间戳策略生成版本号: {} (时间: {})", version, now);
        return version;
    }

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public boolean isValidVersion(int version) {
        // 时间戳版本号应该是 14 位数字（YYYYMMDDHHMMSS）
        // 范围：20000101000000 到 20991231235959
        return version >= 2000010100 && version <= 2100000000;
    }

    /**
     * 将版本号转换为可读的日期时间字符串
     *
     * @param version 版本号
     * @return 格式化的时间字符串
     */
    public static String versionToDateTime(int version) {
        try {
            String str = String.valueOf(version);
            if (str.length() >= 12) {
                LocalDateTime dateTime = LocalDateTime.parse(
                        str.substring(0, 14),
                        FORMATTER
                );
                return dateTime.toString();
            }
        } catch (Exception e) {
            // 解析失败，返回原始字符串
        }
        return String.valueOf(version);
    }

    private static class NumberOverflowException extends RuntimeException {}
}
