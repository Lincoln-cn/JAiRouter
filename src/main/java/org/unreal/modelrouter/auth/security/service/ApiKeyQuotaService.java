package org.unreal.modelrouter.auth.security.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.dto.ApiKeyUpdateRequest;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import org.unreal.modelrouter.auth.security.quota.QuotaLedgerService;
import org.unreal.modelrouter.auth.security.quota.QuotaLimits;
import org.unreal.modelrouter.auth.security.quota.QuotaUsage;
import org.unreal.modelrouter.auth.security.quota.QuotaWindow;

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

    /**
     * 配额账本（v3.1 PR-1 旁路账本）。
     *
     * <p>采用可选字段注入而非构造器注入，保持既有构造器签名与单元测试装配方式不变；
     * 字段为 {@code null}（或账本未启用）时全部走既有内存统计路径。</p>
     */
    @Autowired(required = false)
    private QuotaLedgerService quotaLedgerService;

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

        // v3.1 PR-1：账本启用时优先读取账本的今日（DAY 窗口）用量；
        // 账本未启用、无今日数据或读取失败时回退上面的内存路径，既有返回结构不变。
        final Optional<LedgerDayUsage> ledgerUsage = readTodayUsageFromLedger(keyId);
        if (ledgerUsage.isPresent()) {
            todayRequests = ledgerUsage.get().requests();
            todayTokens = ledgerUsage.get().tokens();
        }

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
        detail.calculateRemaining();

        return Optional.of(detail);
    }

    /**
     * 从配额账本读取指定 API Key 今日（DAY 窗口）的聚合用量（v3.1 PR-1）。
     *
     * <p>账本未启用、无今日数据或读取异常时返回 {@link Optional#empty()}，调用方回退既有内存路径。
     * 账本读库失败时其内部会降级为“仅内存态”，表现为无今日数据，同样触发回退。</p>
     *
     * @param keyId API Key ID
     * @return 今日聚合用量，无法从账本读取时返回 empty
     */
    private Optional<LedgerDayUsage> readTodayUsageFromLedger(final String keyId) {
        if (quotaLedgerService == null || !quotaLedgerService.isEnabled()) {
            return Optional.empty();
        }
        try {
            final LocalDateTime dayStart = QuotaWindow.DAY.windowStart(LocalDateTime.now());
            long requests = 0;
            long tokens = 0;
            boolean found = false;
            // 管理面聚合：仅读内存账本，避免 Redis/JPA 慢查询拖死列表页
            for (final QuotaUsage usage : quotaLedgerService.usageAllFromMemory(keyId)) {
                if (usage.window() == QuotaWindow.DAY && dayStart.equals(usage.windowStart())) {
                    requests += usage.requestCount();
                    tokens += usage.tokenCount();
                    found = true;
                }
            }
            return found ? Optional.of(new LedgerDayUsage(requests, tokens)) : Optional.empty();
        } catch (Exception e) {
            log.warn("读取配额账本失败，回退内存用量统计: keyId={}, error={}", keyId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * 重置账本中指定 API Key 的用量（仅账本启用时执行）。
     *
     * <p>账本启用后今日用量来自账本，若不一起清空会出现“重置后配额未归零”的语义偏差，
     * 因此重置每日配额时同步清空账本；账本异常不影响既有重置流程。</p>
     *
     * @param keyId API Key ID
     */
    private void resetLedger(final String keyId) {
        if (quotaLedgerService == null || !quotaLedgerService.isEnabled()) {
            return;
        }
        try {
            quotaLedgerService.reset(keyId);
        } catch (Exception e) {
            log.warn("重置配额账本失败（忽略，内存配额已重置）: keyId={}, error={}", keyId, e.toString());
        }
    }

    /**
     * 账本读取出的今日聚合用量（v3.1 PR-1）。
     *
     * @param requests 今日请求数
     * @param tokens   今日 token 数
     */
    private record LedgerDayUsage(long requests, long tokens) {
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
        // P2：管理接口也在 WebFlux 链路上，block 必须带超时且工作放到 boundedElastic
        apiKeyService.resetDailyQuota(keyId)
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .block(java.time.Duration.ofSeconds(5));
        rateLimiter.reset(keyId);
        resetLedger(keyId);
        log.info("已重置 API Key 每日配额和速率限制: {}", keyId);
    }

    /**
     * 仅更新 API Key 配额字段（v3.2.1 便捷操作）.
     *
     * <p>委托 {@link ApiKeyService#updateApiKey} 做部分更新（null 字段不改动），
     * 成功后返回刷新后的用量详情。</p>
     *
     * @param keyId   API Key ID
     * @param request 仅含配额相关字段的更新请求
     * @return 更新后的配额详情
     */
    public QuotaUsageDetail updateQuotaLimits(final String keyId, final ApiKeyUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("配额更新请求不能为空");
        }
        if (request.getQuotaAlertThreshold() != null) {
            // issue #96：区间与创建路径统一走 QuotaLimits（[0.05, 1.0]），不再各写一份
            QuotaLimits.validateAlertThreshold(request.getQuotaAlertThreshold());
        }
        if (request.getDailyRequestLimit() != null && request.getDailyRequestLimit() < 0) {
            throw new IllegalArgumentException("dailyRequestLimit 不能为负（0 表示不限制）");
        }
        if (request.getDailyTokenLimit() != null && request.getDailyTokenLimit() < 0) {
            throw new IllegalArgumentException("dailyTokenLimit 不能为负（0 表示不限制）");
        }
        if (request.getRateLimitPerMinute() != null && request.getRateLimitPerMinute() < 0) {
            throw new IllegalArgumentException("rateLimitPerMinute 不能为负（0 表示不限制）");
        }
        apiKeyService.updateApiKey(keyId, request)
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .block(java.time.Duration.ofSeconds(5));
        return getQuotaUsage(keyId)
                .orElseThrow(() -> new IllegalArgumentException("API Key 不存在: " + keyId));
    }

    /**
     * 批量重置指定 API Key 的每日配额（跳过不存在的 Key）.
     *
     * @param keyIds Key ID 列表
     * @return 实际成功重置的数量
     */
    public int resetDailyQuotas(final List<String> keyIds) {
        if (keyIds == null || keyIds.isEmpty()) {
            return 0;
        }
        int ok = 0;
        final Map<String, String> index = apiKeyService.getKeyIdIndex();
        for (final String keyId : keyIds) {
            if (keyId == null || keyId.isBlank() || !index.containsKey(keyId)) {
                continue;
            }
            try {
                resetDailyQuota(keyId);
                ok++;
            } catch (Exception e) {
                log.warn("批量重置配额失败: keyId={}, error={}", keyId, e.toString());
            }
        }
        return ok;
    }

    /**
     * 重置所有 API Key 的每日配额计数器
     */
    public void resetAllDailyQuotas() {
        Map<String, ApiKey> cache = apiKeyService.getApiKeyCache();
        Map<String, String> index = apiKeyService.getKeyIdIndex();

        for (String keyId : index.keySet()) {
            apiKeyService.resetDailyQuota(keyId)
                    .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                    .block(java.time.Duration.ofSeconds(5));
            resetLedger(keyId);
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
        /** 剩余请求数；限额 0（不限制）时为 -1 */
        private long remainingRequests = -1L;
        /** 剩余 Token 数；限额 0（不限制）时为 -1 */
        private long remainingTokens = -1L;

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

        /** 计算剩余量：限额为 0 表示不限制（-1），否则 max(0, limit - used) */
        public void calculateRemaining() {
            remainingRequests = dailyRequestLimit > 0
                ? Math.max(0L, dailyRequestLimit - todayRequestCount)
                : -1L;
            remainingTokens = dailyTokenLimit > 0
                ? Math.max(0L, dailyTokenLimit - todayTokenUsage)
                : -1L;
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
