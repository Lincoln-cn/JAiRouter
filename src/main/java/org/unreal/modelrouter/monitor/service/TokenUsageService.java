package org.unreal.modelrouter.monitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.monitor.dto.TokenUsageRecordDTO;
import org.unreal.modelrouter.monitor.dto.TokenUsageStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.TokenUsageEntity;
import org.unreal.modelrouter.auth.security.permission.DataScope;
import org.unreal.modelrouter.persistence.jpa.repository.TokenUsageRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Token 使用量统计服务
 * 提供 Token 使用量的记录、查询和统计功能
 *
 * @author JAiRouter Team
 * @since 1.9.5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenUsageService {

    /**
     * 「本人」视图一次最多聚合多少行。
     *
     * <p>受限统计走内存聚合（复用既有全局 SQL 会需要十几组带 owner 条件的重载查询），
     * 因此给一个上限，防止极长时间范围把内存打满；超出部分按时间倒序截断（取最近的）。</p>
     */
    private static final int MAX_SCOPED_ROWS = 20_000;

    private final TokenUsageRepository tokenUsageRepository;

    /**
     * 记录 Token 使用量
     *
     * @param record Token 使用量记录
     */
    @Transactional
    public void recordTokenUsage(TokenUsageRecordDTO record) {
        try {
            TokenUsageEntity entity = TokenUsageEntity.builder()
                    .traceId(record.getTraceId())
                    .serviceType(record.getServiceType())
                    .modelName(record.getModelName())
                    .provider(record.getProvider())
                    .instanceName(record.getInstanceName())
                    .instanceUrl(record.getInstanceUrl())
                    .promptTokens(record.getPromptTokens() != null ? record.getPromptTokens() : 0L)
                    .completionTokens(record.getCompletionTokens() != null ? record.getCompletionTokens() : 0L)
                    .totalTokens(record.getTotalTokens() != null ? record.getTotalTokens() : 0L)
                    .apiKeyId(record.getApiKeyId())
                    .userId(record.getUserId())
                    .clientIp(record.getClientIp())
                    .isSuccess(record.getIsSuccess())
                    .errorCode(record.getErrorCode())
                    .errorMessage(record.getErrorMessage())
                    .responseTimeMs(record.getResponseTimeMs())
                    .occurredAt(record.getOccurredAt() != null ? record.getOccurredAt() : LocalDateTime.now())
                    .metadata(record.getMetadata())
                    .build();

            tokenUsageRepository.save(entity);
            log.debug("Token usage recorded: model={}, totalTokens={}, serviceType={}",
                    record.getModelName(), record.getTotalTokens(), record.getServiceType());
        } catch (Exception e) {
            log.error("Failed to record token usage", e);
        }
    }

    /**
     * 批量记录 Token 使用量
     *
     * @param records Token 使用量记录列表
     */
    @Transactional
    public void recordTokenUsageBatch(List<TokenUsageRecordDTO> records) {
        for (TokenUsageRecordDTO record : records) {
            recordTokenUsage(record);
        }
    }

    /**
     * 获取 Token 使用量统计信息
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计信息 DTO
     */
    @Transactional(readOnly = true)
    public TokenUsageStatisticsDTO getTokenUsageStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        // 设置默认时间范围
        LocalDateTime effectiveStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEndTime = endTime != null ? endTime : LocalDateTime.now();

        // 构建统计 DTO
        TokenUsageStatisticsDTO dto = TokenUsageStatisticsDTO.builder()
                .startTime(effectiveStartTime)
                .endTime(effectiveEndTime)
                .build();

        // 基本统计
        // 分母与分子同口径：都用请求的时间范围，避免历史数据把成功率稀释（#714）
        dto.setTotalRequests(tokenUsageRepository.countByTimeRange(effectiveStartTime, effectiveEndTime));
        dto.setTotalTokens(tokenUsageRepository.countTotalTokensByTimeRange(effectiveStartTime, effectiveEndTime));
        dto.setSuccessfulRequests(tokenUsageRepository.countSuccessByTimeRange(effectiveStartTime, effectiveEndTime));
        dto.setFailedRequests(tokenUsageRepository.countFailedByTimeRange(effectiveStartTime, effectiveEndTime));
        dto.setAvgResponseTimeMs(tokenUsageRepository.avgResponseTimeByTimeRange(effectiveStartTime, effectiveEndTime));

        // 计算输入输出 token（需要单独查询）
        List<Object[]> tokenDetails =
                tokenUsageRepository.countTokensByServiceType(effectiveStartTime, effectiveEndTime);
        long totalPrompt = 0L;
        long totalCompletion = 0L;
        for (Object[] row : tokenDetails) {
            totalPrompt += (Long) row[2];
            totalCompletion += (Long) row[3];
        }
        dto.setTotalPromptTokens(totalPrompt);
        dto.setTotalCompletionTokens(totalCompletion);

        // 计算成功率
        if (dto.getTotalRequests() > 0) {
            dto.setSuccessRate((double) dto.getSuccessfulRequests() / dto.getTotalRequests() * 100);
        } else {
            dto.setSuccessRate(0.0);
        }

        // 按模型统计
        List<Object[]> byModel = tokenUsageRepository.countTokensByModel(effectiveStartTime, effectiveEndTime);
        List<TokenUsageStatisticsDTO.ModelTokenStats> modelStats = new ArrayList<>();
        for (Object[] row : byModel) {
            String modelName = (String) row[0];
            Long totalTokens = (Long) row[1];
            Long promptTokens = (Long) row[2];
            Long completionTokens = (Long) row[3];
            Long requestCount = (Long) row[4];
            Double avgTokensPerRequest = requestCount > 0 ? (double) totalTokens / requestCount : 0.0;

            modelStats.add(new TokenUsageStatisticsDTO.ModelTokenStats(
                    modelName, totalTokens, promptTokens, completionTokens, requestCount, avgTokensPerRequest));
        }
        dto.setByModel(modelStats);

        // 按服务类型统计
        List<Object[]> byServiceType =
                tokenUsageRepository.countTokensByServiceType(effectiveStartTime, effectiveEndTime);
        List<TokenUsageStatisticsDTO.ServiceTypeStats> serviceTypeStats = new ArrayList<>();
        for (Object[] row : byServiceType) {
            String serviceType = (String) row[0];
            Long totalTokens = (Long) row[1];
            Long promptTokens = (Long) row[2];
            Long completionTokens = (Long) row[3];
            Long requestCount = (Long) row[4];

            serviceTypeStats.add(new TokenUsageStatisticsDTO.ServiceTypeStats(
                    serviceType, totalTokens, promptTokens, completionTokens, requestCount));
        }
        dto.setByServiceType(serviceTypeStats);

        // 按提供商统计
        List<Object[]> byProvider = tokenUsageRepository.countTokensByProvider(effectiveStartTime, effectiveEndTime);
        List<TokenUsageStatisticsDTO.ProviderStats> providerStats = new ArrayList<>();
        for (Object[] row : byProvider) {
            String provider = (String) row[0];
            Long totalTokens = (Long) row[1];
            Long requestCount = (Long) row[2];

            providerStats.add(new TokenUsageStatisticsDTO.ProviderStats(provider, totalTokens, requestCount));
        }
        dto.setByProvider(providerStats);

        // 按日期统计
        List<Object[]> byDay = tokenUsageRepository.countTokensByDay(effectiveStartTime, effectiveEndTime);
        List<TokenUsageStatisticsDTO.DailyStats> dailyStats = new ArrayList<>();
        for (Object[] row : byDay) {
            String date = (String) row[0];
            Long totalTokens = (Long) row[1];
            Long promptTokens = (Long) row[2];
            Long completionTokens = (Long) row[3];
            Long requestCount = (Long) row[4];

            dailyStats.add(new TokenUsageStatisticsDTO.DailyStats(
                    date, totalTokens, promptTokens, completionTokens, requestCount));
        }
        dto.setByDay(dailyStats);

        // 按周统计
        List<Object[]> byWeek = tokenUsageRepository.countTokensByWeek(effectiveStartTime, effectiveEndTime);
        List<TokenUsageStatisticsDTO.WeeklyStats> weeklyStats = new ArrayList<>();
        for (Object[] row : byWeek) {
            Integer year = (Integer) row[0];
            Integer week = (Integer) row[1];
            Long totalTokens = (Long) row[2];
            Long promptTokens = (Long) row[3];
            Long completionTokens = (Long) row[4];
            Long requestCount = (Long) row[5];
            String weekLabel = year + "-W" + String.format("%02d", week);

            weeklyStats.add(new TokenUsageStatisticsDTO.WeeklyStats(
                    year, week, totalTokens, promptTokens, completionTokens, requestCount, weekLabel));
        }
        dto.setByWeek(weeklyStats);

        // 按月统计
        List<Object[]> byMonth = tokenUsageRepository.countTokensByMonth(effectiveStartTime, effectiveEndTime);
        List<TokenUsageStatisticsDTO.MonthlyStats> monthlyStats = new ArrayList<>();
        for (Object[] row : byMonth) {
            Integer year = (Integer) row[0];
            Integer month = (Integer) row[1];
            Long totalTokens = (Long) row[2];
            Long promptTokens = (Long) row[3];
            Long completionTokens = (Long) row[4];
            Long requestCount = (Long) row[5];
            String monthLabel = year + "-" + String.format("%02d", month);

            monthlyStats.add(new TokenUsageStatisticsDTO.MonthlyStats(
                    year, month, totalTokens, promptTokens, completionTokens, requestCount, monthLabel));
        }
        dto.setByMonth(monthlyStats);

        // 按小时统计
        List<Object[]> byHour = tokenUsageRepository.countTokensByHour(effectiveStartTime, effectiveEndTime);
        List<TokenUsageStatisticsDTO.HourlyStats> hourlyStats = new ArrayList<>();
        for (Object[] row : byHour) {
            Integer hour = (Integer) row[0];
            Long totalTokens = (Long) row[1];
            Long requestCount = (Long) row[2];
            String hourLabel = String.format("%02d:00", hour);

            hourlyStats.add(new TokenUsageStatisticsDTO.HourlyStats(hour, totalTokens, requestCount, hourLabel));
        }
        dto.setByHour(hourlyStats);

        // 按 API Key 统计（可选）
        List<Object[]> byApiKey = tokenUsageRepository.countTokensByApiKey(effectiveStartTime, effectiveEndTime);
        List<TokenUsageStatisticsDTO.ApiKeyStats> apiKeyStats = new ArrayList<>();
        for (Object[] row : byApiKey) {
            String apiKeyId = (String) row[0];
            Long totalTokens = (Long) row[1];
            Long requestCount = (Long) row[2];

            apiKeyStats.add(new TokenUsageStatisticsDTO.ApiKeyStats(apiKeyId, totalTokens, requestCount));
        }
        dto.setByApiKey(apiKeyStats);

        // 按用户统计（可选）
        List<Object[]> byUser = tokenUsageRepository.countTokensByUser(effectiveStartTime, effectiveEndTime);
        List<TokenUsageStatisticsDTO.UserStats> userStats = new ArrayList<>();
        for (Object[] row : byUser) {
            String userId = (String) row[0];
            Long totalTokens = (Long) row[1];
            Long requestCount = (Long) row[2];

            userStats.add(new TokenUsageStatisticsDTO.UserStats(userId, totalTokens, requestCount));
        }
        dto.setByUser(userStats);

        return dto;
    }

    /**
     * 获取最近的使用记录
     *
     * @param limit 最大数量
     * @return 使用记录列表
     */
    /**
     * 按数据面范围取统计（issue #1185）。
     *
     * <p>持全局码（{@code monitoring:tokenusage:read}）走原来的 SQL 聚合；只持「本人读」码
     * （{@code monitoring:tokenusage:self:read}）时按 {@link DataScope#owns} 过滤后在内存聚合，
     * 口径与全局版一致（成功率分母用请求数、平均响应只统计有响应时间的行）。</p>
     *
     * @param startTime 开始时间，空则取最近 7 天
     * @param endTime   结束时间，空则取当前
     * @param scope     数据面范围，{@code null} 或不限行时等价于全局统计
     * @return 统计信息 DTO
     */
    @Transactional(readOnly = true)
    public TokenUsageStatisticsDTO getTokenUsageStatistics(final LocalDateTime startTime,
                                                           final LocalDateTime endTime,
                                                           final DataScope scope) {
        if (scope == null || !scope.restricted()) {
            return getTokenUsageStatistics(startTime, endTime);
        }
        final LocalDateTime effectiveStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime effectiveEndTime = endTime != null ? endTime : LocalDateTime.now();
        final List<TokenUsageEntity> rows = tokenUsageRepository
                .findByTimeRange(effectiveStartTime, effectiveEndTime).stream()
                .filter(row -> scope.owns(row.getUserId(), row.getApiKeyId()))
                .limit(MAX_SCOPED_ROWS)
                .collect(Collectors.toList());
        return aggregateRestricted(effectiveStartTime, effectiveEndTime, rows);
    }

    /**
     * 按数据面范围取最近使用记录。
     */
    @Transactional(readOnly = true)
    public List<TokenUsageEntity> getRecentUsage(final int limit, final DataScope scope) {
        if (scope == null || !scope.restricted()) {
            return getRecentUsage(limit);
        }
        final int capped = Math.min(Math.max(limit, 1), 100);
        final LocalDateTime since = LocalDateTime.now().minusDays(90);
        return tokenUsageRepository.findByTimeRange(since, LocalDateTime.now()).stream()
                .filter(row -> scope.owns(row.getUserId(), row.getApiKeyId()))
                .limit(capped)
                .collect(Collectors.toList());
    }

    /**
     * 内存聚合（受限视图）。维度口径与全局 SQL 版保持同名字段一致。
     */
    private TokenUsageStatisticsDTO aggregateRestricted(final LocalDateTime startTime,
                                                        final LocalDateTime endTime,
                                                        final List<TokenUsageEntity> rows) {
        final TokenUsageStatisticsDTO dto = TokenUsageStatisticsDTO.builder()
                .startTime(startTime)
                .endTime(endTime)
                .build();
        final long totalRequests = rows.size();
        final long successful = rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsSuccess())).count();
        final long failed = rows.stream().filter(row -> Boolean.FALSE.equals(row.getIsSuccess())).count();
        final long totalTokens = rows.stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum();
        final long totalPrompt = rows.stream().mapToLong(row -> zeroIfNull(row.getPromptTokens())).sum();
        final long totalCompletion = rows.stream().mapToLong(row -> zeroIfNull(row.getCompletionTokens())).sum();
        final double avgResponse = rows.stream()
                .filter(row -> row.getResponseTimeMs() != null)
                .mapToLong(TokenUsageEntity::getResponseTimeMs)
                .average()
                .orElse(0.0);
        dto.setTotalRequests(totalRequests);
        dto.setSuccessfulRequests(successful);
        dto.setFailedRequests(failed);
        dto.setTotalTokens(totalTokens);
        dto.setTotalPromptTokens(totalPrompt);
        dto.setTotalCompletionTokens(totalCompletion);
        dto.setAvgResponseTimeMs(avgResponse);
        dto.setSuccessRate(totalRequests > 0 ? (double) successful / totalRequests * 100 : 0.0);

        dto.setByModel(rows.stream()
                .collect(Collectors.groupingBy(row -> nullToEmpty(row.getModelName()), LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    final List<TokenUsageEntity> group = entry.getValue();
                    final long tokens = group.stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum();
                    final long count = group.size();
                    return new TokenUsageStatisticsDTO.ModelTokenStats(entry.getKey(), tokens,
                            group.stream().mapToLong(row -> zeroIfNull(row.getPromptTokens())).sum(),
                            group.stream().mapToLong(row -> zeroIfNull(row.getCompletionTokens())).sum(),
                            count, count > 0 ? (double) tokens / count : 0.0);
                })
                .sorted(Comparator.comparing(TokenUsageStatisticsDTO.ModelTokenStats::getTotalTokens,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList()));

        dto.setByServiceType(rows.stream()
                .collect(Collectors.groupingBy(row -> nullToEmpty(row.getServiceType()), LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new TokenUsageStatisticsDTO.ServiceTypeStats(entry.getKey(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getPromptTokens())).sum(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getCompletionTokens())).sum(),
                        (long) entry.getValue().size()))
                .collect(Collectors.toList()));

        dto.setByProvider(rows.stream()
                .collect(Collectors.groupingBy(row -> nullToEmpty(row.getProvider()), LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new TokenUsageStatisticsDTO.ProviderStats(entry.getKey(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum(),
                        (long) entry.getValue().size()))
                .collect(Collectors.toList()));

        dto.setByDay(rows.stream()
                .collect(Collectors.groupingBy(row -> nullToEmpty(row.getUsageDate()), TreeMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new TokenUsageStatisticsDTO.DailyStats(entry.getKey(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getPromptTokens())).sum(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getCompletionTokens())).sum(),
                        (long) entry.getValue().size()))
                .collect(Collectors.toList()));

        dto.setByWeek(rows.stream()
                .collect(Collectors.groupingBy(row -> row.getYear() + "-" + row.getWeekOfYear(), TreeMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    final TokenUsageEntity sample = entry.getValue().get(0);
                    return new TokenUsageStatisticsDTO.WeeklyStats(sample.getYear(), sample.getWeekOfYear(),
                            entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum(),
                            entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getPromptTokens())).sum(),
                            entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getCompletionTokens())).sum(),
                            (long) entry.getValue().size(),
                            String.format("%d-W%02d", sample.getYear(), sample.getWeekOfYear()));
                })
                .collect(Collectors.toList()));

        dto.setByMonth(rows.stream()
                .collect(Collectors.groupingBy(row -> row.getYear() + "-" + row.getMonth(), TreeMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> {
                    final TokenUsageEntity sample = entry.getValue().get(0);
                    return new TokenUsageStatisticsDTO.MonthlyStats(sample.getYear(), sample.getMonth(),
                            entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum(),
                            entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getPromptTokens())).sum(),
                            entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getCompletionTokens())).sum(),
                            (long) entry.getValue().size(),
                            String.format("%d-%02d", sample.getYear(), sample.getMonth()));
                })
                .collect(Collectors.toList()));

        dto.setByHour(rows.stream()
                .collect(Collectors.groupingBy(row -> row.getHour() == null ? -1 : row.getHour(),
                        TreeMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new TokenUsageStatisticsDTO.HourlyStats(entry.getKey(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum(),
                        (long) entry.getValue().size(),
                        entry.getKey() < 0 ? "-" : String.format("%02d:00", entry.getKey())))
                .collect(Collectors.toList()));

        dto.setByApiKey(rows.stream()
                .collect(Collectors.groupingBy(row -> nullToEmpty(row.getApiKeyId()), LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new TokenUsageStatisticsDTO.ApiKeyStats(entry.getKey(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum(),
                        (long) entry.getValue().size()))
                .collect(Collectors.toList()));

        dto.setByUser(rows.stream()
                .collect(Collectors.groupingBy(row -> nullToEmpty(row.getUserId()), LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new TokenUsageStatisticsDTO.UserStats(entry.getKey(),
                        entry.getValue().stream().mapToLong(row -> zeroIfNull(row.getTotalTokens())).sum(),
                        (long) entry.getValue().size()))
                .collect(Collectors.toList()));

        return dto;
    }

    private static long zeroIfNull(final Long value) {
        return value == null ? 0L : value;
    }

    private static String nullToEmpty(final String value) {
        return value == null ? "" : value;
    }

    @Transactional(readOnly = true)
    public List<TokenUsageEntity> getRecentUsage(int limit) {
        return tokenUsageRepository.findRecentUsage(Math.min(limit, 100));
    }

    /**
     * 获取指定模型的最近使用记录
     *
     * @param modelName 模型名称
     * @param limit     最大数量
     * @return 使用记录列表
     */
    @Transactional(readOnly = true)
    public List<TokenUsageEntity> getRecentUsageByModel(String modelName, int limit) {
        return tokenUsageRepository.findRecentUsageByModel(modelName, Math.min(limit, 100));
    }

    /**
     * 删除过期使用记录
     *
     * @param cutoffTime 截止时间
     * @return 删除的数量
     */
    @Transactional
    public int deleteOldUsageRecords(LocalDateTime cutoffTime) {
        log.info("删除截止时间 {} 之前的 Token 使用记录", cutoffTime);
        return tokenUsageRepository.deleteByOccurredAtBefore(cutoffTime);
    }

    /**
     * 获取模型使用量排名
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     最大数量
     * @return 模型使用量排名
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopModels(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        List<Object[]> results = tokenUsageRepository.countTokensByModel(startTime, endTime);
        List<Map<String, Object>> topModels = new ArrayList<>();

        for (int i = 0; i < Math.min(limit, results.size()); i++) {
            Object[] row = results.get(i);
            Map<String, Object> modelInfo = new HashMap<>();
            modelInfo.put("rank", i + 1);
            modelInfo.put("modelName", row[0]);
            modelInfo.put("totalTokens", row[1]);
            modelInfo.put("promptTokens", row[2]);
            modelInfo.put("completionTokens", row[3]);
            modelInfo.put("requestCount", row[4]);
            topModels.add(modelInfo);
        }

        return topModels;
    }

    /**
     * 获取服务类型使用量排名
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     最大数量
     * @return 服务类型使用量排名
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopServiceTypes(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        List<Object[]> results = tokenUsageRepository.countTokensByServiceType(startTime, endTime);
        List<Map<String, Object>> topServiceTypes = new ArrayList<>();

        for (int i = 0; i < Math.min(limit, results.size()); i++) {
            Object[] row = results.get(i);
            Map<String, Object> serviceInfo = new HashMap<>();
            serviceInfo.put("rank", i + 1);
            serviceInfo.put("serviceType", row[0]);
            serviceInfo.put("totalTokens", row[1]);
            serviceInfo.put("promptTokens", row[2]);
            serviceInfo.put("completionTokens", row[3]);
            serviceInfo.put("requestCount", row[4]);
            topServiceTypes.add(serviceInfo);
        }

        return topServiceTypes;
    }
}
