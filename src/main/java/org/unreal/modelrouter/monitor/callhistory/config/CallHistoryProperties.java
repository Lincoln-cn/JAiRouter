package org.unreal.modelrouter.monitor.callhistory.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * API 调用历史配置属性
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@Data
@ConfigurationProperties(prefix = "jairouter.call-history")
public class CallHistoryProperties {

    /**
     * 是否启用调用历史记录
     */
    private boolean enabled = true;

    /**
     * 数据保留天数
     */
    private int retentionDays = 30;

    /**
     * 最大记录数限制
     */
    private long maxRecords = 1000000;

    /**
     * 清理定时任务 cron 表达式
     */
    private String cleanupCron = "0 2 * * *";

    /**
     * 内存缓冲区大小
     */
    private int bufferSize = 10000;

    /**
     * 批量写入大小
     */
    private int batchSize = 100;

    /**
     * 批量写入等待时间（毫秒）
     */
    private int batchWaitMs = 1000;

    /**
     * 是否保存请求体摘要
     */
    private boolean requestBodySummaryEnabled = true;

    /**
     * 请求体摘要最大长度
     */
    private int requestBodySummaryMaxLength = 200;

    /**
     * 是否保存响应体摘要
     */
    private boolean responseBodySummaryEnabled = true;

    /**
     * 响应体摘要最大长度
     */
    private int responseBodySummaryMaxLength = 200;

    /**
     * 慢调用阈值（毫秒）
     */
    private long slowCallThresholdMs = 1000;
}
