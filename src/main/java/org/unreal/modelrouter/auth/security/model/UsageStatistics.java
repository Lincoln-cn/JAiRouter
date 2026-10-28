package org.unreal.modelrouter.auth.security.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API Key使用统计数据模型
 */
@Data
@Builder
@NoArgsConstructor   // 这个很关键
@AllArgsConstructor
public class UsageStatistics {
    
    /**
     * 总请求次数
     */
    private long totalRequests;
    
    /**
     * 成功请求次数
     */
    private long successfulRequests;
    
    /**
     * 失败请求次数
     */
    private long failedRequests;
    
    /**
     * 最后使用时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private LocalDateTime lastUsedAt;
    
    /**
     * 每日使用统计（日期 -> 使用次数）
     */
    private Map<String, Long> dailyUsage;

    /**
     * 每日 Token 使用统计（日期 -> Token 总数）
     */
    private Map<String, Long> dailyTokenUsage;

    /**
     * 读取每日请求统计（并发安全的惰性初始化）.
     *
     * <p>#715：底层换成 {@link ConcurrentHashMap}，并在这里做一次「旧数据迁移」——
     * 反序列化（Jackson）与历史落库数据里都可能是普通 {@code HashMap}，直接换成并发容器
     * 才不会让并发的 {@code merge} 与 {@code clear} 相互踩踏。</p>
     *
     * @return 非空且并发安全的「日期 -> 请求数」映射
     */
    public Map<String, Long> getDailyUsage() {
        if (dailyUsage == null) {
            dailyUsage = new ConcurrentHashMap<>();
        } else if (!(dailyUsage instanceof ConcurrentHashMap)) {
            dailyUsage = new ConcurrentHashMap<>(dailyUsage);
        }
        return dailyUsage;
    }

    /**
     * 设置每日请求统计（写入时统一复制成并发容器）.
     *
     * @param dailyUsage 外部传入的映射，可为 {@code null}
     */
    public void setDailyUsage(final Map<String, Long> dailyUsage) {
        this.dailyUsage = dailyUsage == null
                ? new ConcurrentHashMap<>()
                : new ConcurrentHashMap<>(dailyUsage);
    }

    /**
     * 读取每日 Token 统计（并发安全的惰性初始化）.
     *
     * @return 非空且并发安全的「日期 -> Token 数」映射
     */
    public Map<String, Long> getDailyTokenUsage() {
        if (dailyTokenUsage == null) {
            dailyTokenUsage = new ConcurrentHashMap<>();
        } else if (!(dailyTokenUsage instanceof ConcurrentHashMap)) {
            dailyTokenUsage = new ConcurrentHashMap<>(dailyTokenUsage);
        }
        return dailyTokenUsage;
    }

    /**
     * 设置每日 Token 统计（写入时统一复制成并发容器）.
     *
     * @param dailyTokenUsage 外部传入的映射，可为 {@code null}
     */
    public void setDailyTokenUsage(final Map<String, Long> dailyTokenUsage) {
        this.dailyTokenUsage = dailyTokenUsage == null
                ? new ConcurrentHashMap<>()
                : new ConcurrentHashMap<>(dailyTokenUsage);
    }

    /**
     * 获取成功率
     * @return 成功率（0-1之间的小数）
     */
    public double getSuccessRate() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) successfulRequests / totalRequests;
    }

    /**
     * 增加请求统计
     * @param success 请求是否成功
     */
    public synchronized void incrementRequest(final boolean success) {
        totalRequests++;
        if (success) {
            successfulRequests++;
        } else {
            failedRequests++;
        }
        lastUsedAt = LocalDateTime.now();
        final String today = LocalDateTime.now().toLocalDate().toString();
        // #715：merge 是 ConcurrentHashMap 上的原子读改写，替代 getOrDefault + put
        getDailyUsage().merge(today, 1L, Long::sum);
    }

    /**
     * 增加 Token 使用量
     * @param tokens 本次请求消耗的 Token 数
     */
    public void incrementTokens(final long tokens) {
        if (tokens <= 0) {
            return;
        }
        final String today = LocalDateTime.now().toLocalDate().toString();
        getDailyTokenUsage().merge(today, tokens, Long::sum);
    }

    /**
     * 重置统计信息
     */
    public void reset() {
        totalRequests = 0L;
        successfulRequests = 0L;
        failedRequests = 0L;
        lastUsedAt = null;
        if (dailyUsage != null) {
            dailyUsage.clear();
        }
        if (dailyTokenUsage != null) {
            dailyTokenUsage.clear();
        }
    }

    /**
     * 重置每日统计（请求计数和 Token 计数）
     */
    public void resetDaily() {
        if (dailyUsage != null) {
            dailyUsage.clear();
        }
        if (dailyTokenUsage != null) {
            dailyTokenUsage.clear();
        }
    }
    
    /**
     * 获取失败率
     * @return 失败率（0-1之间的小数）
     */
    public double getFailureRate() {
        if (totalRequests == 0) {
            return 0.0;
        }
        return (double) failedRequests / totalRequests;
    }
}