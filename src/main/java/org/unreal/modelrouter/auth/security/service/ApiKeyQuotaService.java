package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * API Key 配额管理服务
 * 提供配额查询、重置、告警等管理功能
 *
 * @since v2.7.6
 */
@Slf4j
@Service
public class ApiKeyQuotaService {

    private final ApiKeyService apiKeyService;
    private final TokenBucketRateLimiter rateLimiter;

    @Autowired
    public ApiKeyQuotaService(ApiKeyService apiKeyService,
                              TokenBucketRateLimiter rateLimiter) {
        this.apiKeyService = apiKeyService;
        this.rateLimiter = rateLimiter;
    }

    /**
     * 获取指定 API Key 的配额使用详情
     *
     * @param keyId API Key ID
     * @return 配额使用详情，未找到返回 empty
     */
    public Optional<QuotaUsageDetail> getQuotaUsage(String keyId) {
        Map<String, ApiKey> cache = apiKeyService.getApiKeyCache();
        Map<String, String> index = apiKeyService.getKeyIdIndex();

        String keyHash = index.get(keyId);
        if (keyHash == null) {
            return Optional.empty();
        }

        ApiKey apiKey = cache.get(keyHash);
        if (apiKey == null) {
            return Optional.empty();
        }

        UsageStatistics usage = apiKey.getUsage();
        String today = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE);

        long todayRequests = 0;
        long todayTokens = 0;
        if (usage != null) {
            Map<String, Long> dailyUsage = usage.getDailyUsage();
            if (dailyUsage != null) {
                todayRequests = dailyUsage.getOrDefault(today, 0L);
            }
            Map<String, Long> dailyTokenUsage = usage.getDailyTokenUsage();
            if (dailyTokenUsage != null) {
                todayTokens = dailyTokenUsage.getOrDefault(today, 0L);
            }
        }

        int currentRate = rateLimiter.getCurrentCount(keyId);

        QuotaUsageDetail detail = QuotaUsageDetail.builder()
            .keyId(keyId)
            .description(apiKey.getDescription())
            .dailyRequestLimit(apiKey.getDailyRequestLimit())
            .dailyTokenLimit(apiKey.getDailyTokenLimit())
            .rateLimitPerMinute(apiKey.getRateLimitPerMinute())
            .quotaAlertThreshold(apiKey.getQuotaAlertThreshold())
            .todayRequestCount(todayRequests)
            .todayTokenUsage(todayTokens)
            .currentRatePerMinute(currentRate)
            .totalRequests(usage != null ? usage.getTotalRequests() : 0L)
            .build();

        // 计算使用百分比
        detail.calculateUsagePercent();

        return Optional.of(detail);
    }

    /**
     * 获取所有 API Key 的配额告警列表
     * 返回所有触发了告警阈值的 API Key
     *
     * @return 告警列表
     */
    public List<QuotaAlertInfo> getAlerts() {
        Map<String, ApiKey> cache = apiKeyService.getApiKeyCache();
        Map<String, String> index = apiKeyService.getKeyIdIndex();

        return index.keySet().stream()
            .map(keyId -> {
                Optional<QuotaUsageDetail> detail = getQuotaUsage(keyId);
                return detail.orElse(null);
            })
            .filter(Objects::nonNull)
            .filter(QuotaUsageDetail::isAlertTriggered)
            .map(detail -> QuotaAlertInfo.builder()
                .keyId(detail.getKeyId())
                .description(detail.getDescription())
                .alertType(determineAlertType(detail))
                .dailyRequestUsagePercent(detail.getDailyRequestUsagePercent())
                .dailyTokenUsagePercent(detail.getDailyTokenUsagePercent())
                .message(buildAlertMessage(detail))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 获取所有 API Key 的配额使用概览
     *
     * @return 配额使用概览列表
     */
    public List<QuotaUsageDetail> getAllQuotaUsage() {
        Map<String, String> index = apiKeyService.getKeyIdIndex();
        return index.keySet().stream()
            .map(this::getQuotaUsage)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    /**
     * 重置指定 API Key 的每日配额计数器
     *
     * @param keyId API Key ID
     */
    public void resetDailyQuota(String keyId) {
        apiKeyService.resetDailyQuota(keyId).block();
        rateLimiter.reset(keyId);
        log.info("已重置 API Key 每日配额和速率限制: {}", keyId);
    }

    /**
     * 重置所有 API Key 的每日配额计数器
     */
    public void resetAllDailyQuotas() {
        Map<String, ApiKey> cache = apiKeyService.getApiKeyCache();
        Map<String, String> index = apiKeyService.getKeyIdIndex();

        for (String keyId : index.keySet()) {
            apiKeyService.resetDailyQuota(keyId).block();
        }
        rateLimiter.resetAll();
        log.info("已重置所有 API Key 每日配额和速率限制");
    }

    /**
     * 清理所有 API Key 过期的每日使用记录
     * 删除超过 30 天的历史使用数据
     */
    public int cleanupExpiredDailyUsage() {
        Map<String, ApiKey> cache = apiKeyService.getApiKeyCache();
        int cleaned = 0;
        String cutoffDate = LocalDateTime.now().minusDays(30)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);

        for (ApiKey apiKey : cache.values()) {
            UsageStatistics usage = apiKey.getUsage();
            if (usage == null) {
                continue;
            }

            Map<String, Long> dailyUsage = usage.getDailyUsage();
            if (dailyUsage != null) {
                List<String> expiredKeys = dailyUsage.keySet().stream()
                    .filter(date -> date.compareTo(cutoffDate) < 0)
                    .toList();
                for (String key : expiredKeys) {
                    dailyUsage.remove(key);
                    cleaned++;
                }
            }

            Map<String, Long> dailyTokenUsage = usage.getDailyTokenUsage();
            if (dailyTokenUsage != null) {
                List<String> expiredTokenKeys = dailyTokenUsage.keySet().stream()
                    .filter(date -> date.compareTo(cutoffDate) < 0)
                    .toList();
                for (String key : expiredTokenKeys) {
                    dailyTokenUsage.remove(key);
                    cleaned++;
                }
            }
        }

        if (cleaned > 0) {
            apiKeyService.getApiKeyCache(); // trigger any necessary persistence
            log.info("已清理 {} 条过期的每日使用记录", cleaned);
        }
        return cleaned;
    }

    private String determineAlertType(QuotaUsageDetail detail) {
        if (detail.getDailyRequestUsagePercent() >= detail.getQuotaAlertThreshold() * 100) {
            return "REQUEST_QUOTA";
        }
        if (detail.getDailyTokenUsagePercent() >= detail.getQuotaAlertThreshold() * 100) {
            return "TOKEN_QUOTA";
        }
        return "GENERAL";
    }

    private String buildAlertMessage(QuotaUsageDetail detail) {
        List<String> messages = new ArrayList<>();
        double threshold = detail.getQuotaAlertThreshold() * 100;

        if (detail.getDailyRequestUsagePercent() >= threshold) {
            messages.add(String.format("请求配额已使用 %.1f%%（阈值 %.0f%%）",
                detail.getDailyRequestUsagePercent(), threshold));
        }
        if (detail.getDailyTokenUsagePercent() >= threshold) {
            messages.add(String.format("Token 配额已使用 %.1f%%（阈值 %.0f%%）",
                detail.getDailyTokenUsagePercent(), threshold));
        }
        return String.join("；", messages);
    }

    // ===== 内部 DTO =====

    @lombok.Data
    @lombok.Builder
    public static class QuotaUsageDetail {
        private String keyId;
        private String description;
        private long dailyRequestLimit;
        private long dailyTokenLimit;
        private int rateLimitPerMinute;
        private double quotaAlertThreshold;
        private long todayRequestCount;
        private long todayTokenUsage;
        private int currentRatePerMinute;
        private long totalRequests;

        /** 请求配额使用百分比 (0.0-100.0)，无限时为 -1 */
        private double dailyRequestUsagePercent;
        /** Token 配额使用百分比 (0.0-100.0)，无限时为 -1 */
        private double dailyTokenUsagePercent;
        /** 是否触发告警 */
        private boolean alertTriggered;

        public void calculateUsagePercent() {
            dailyRequestUsagePercent = dailyRequestLimit > 0
                ? (double) todayRequestCount / dailyRequestLimit * 100
                : -1;
            dailyTokenUsagePercent = dailyTokenLimit > 0
                ? (double) todayTokenUsage / dailyTokenLimit * 100
                : -1;

            double thresholdPercent = quotaAlertThreshold * 100;
            alertTriggered = (dailyRequestUsagePercent >= 0 && dailyRequestUsagePercent >= thresholdPercent)
                || (dailyTokenUsagePercent >= 0 && dailyTokenUsagePercent >= thresholdPercent);
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class QuotaAlertInfo {
        private String keyId;
        private String description;
        private String alertType;
        private double dailyRequestUsagePercent;
        private double dailyTokenUsagePercent;
        private String message;
    }
}
