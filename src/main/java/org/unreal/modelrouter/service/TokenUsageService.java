package org.unreal.modelrouter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.common.dto.TokenUsageRecordDTO;
import org.unreal.modelrouter.common.dto.TokenUsageStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.TokenUsageEntity;
import org.unreal.modelrouter.persistence.jpa.repository.TokenUsageRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        // 构建统计 DTO
        TokenUsageStatisticsDTO dto = TokenUsageStatisticsDTO.builder()
                .startTime(startTime)
                .endTime(endTime)
                .build();

        // 基本统计
        dto.setTotalRequests(tokenUsageRepository.countAllUsage());
        dto.setTotalTokens(tokenUsageRepository.countTotalTokensByTimeRange(startTime, endTime));
        dto.setSuccessfulRequests(tokenUsageRepository.countSuccessByTimeRange(startTime, endTime));
        dto.setFailedRequests(tokenUsageRepository.countFailedByTimeRange(startTime, endTime));
        dto.setAvgResponseTimeMs(tokenUsageRepository.avgResponseTimeByTimeRange(startTime, endTime));

        // 计算输入输出 token（需要单独查询）
        List<Object[]> tokenDetails = tokenUsageRepository.countTokensByServiceType(startTime, endTime);
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
        List<Object[]> byModel = tokenUsageRepository.countTokensByModel(startTime, endTime);
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
        List<Object[]> byServiceType = tokenUsageRepository.countTokensByServiceType(startTime, endTime);
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
        List<Object[]> byProvider = tokenUsageRepository.countTokensByProvider(startTime, endTime);
        List<TokenUsageStatisticsDTO.ProviderStats> providerStats = new ArrayList<>();
        for (Object[] row : byProvider) {
            String provider = (String) row[0];
            Long totalTokens = (Long) row[1];
            Long requestCount = (Long) row[2];

            providerStats.add(new TokenUsageStatisticsDTO.ProviderStats(provider, totalTokens, requestCount));
        }
        dto.setByProvider(providerStats);

        // 按日期统计
        List<Object[]> byDay = tokenUsageRepository.countTokensByDay(startTime, endTime);
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
        List<Object[]> byWeek = tokenUsageRepository.countTokensByWeek(startTime, endTime);
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
        List<Object[]> byMonth = tokenUsageRepository.countTokensByMonth(startTime, endTime);
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
        List<Object[]> byHour = tokenUsageRepository.countTokensByHour(startTime, endTime);
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
        List<Object[]> byApiKey = tokenUsageRepository.countTokensByApiKey(startTime, endTime);
        List<TokenUsageStatisticsDTO.ApiKeyStats> apiKeyStats = new ArrayList<>();
        for (Object[] row : byApiKey) {
            String apiKeyId = (String) row[0];
            Long totalTokens = (Long) row[1];
            Long requestCount = (Long) row[2];

            apiKeyStats.add(new TokenUsageStatisticsDTO.ApiKeyStats(apiKeyId, totalTokens, requestCount));
        }
        dto.setByApiKey(apiKeyStats);

        // 按用户统计（可选）
        List<Object[]> byUser = tokenUsageRepository.countTokensByUser(startTime, endTime);
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
