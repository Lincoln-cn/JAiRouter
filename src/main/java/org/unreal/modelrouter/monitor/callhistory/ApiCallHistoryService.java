package org.unreal.modelrouter.monitor.callhistory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryQueryDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ApiCallHistoryRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * API 调用历史服务
 * 提供调用历史的记录、查询、统计和清理功能
 *
 * @author JAiRouter Team
 * @since 2.7.8
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiCallHistoryService {

    private final ApiCallHistoryRepository repository;
    private final CallHistoryProperties properties;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * 记录 API 调用
     *
     * @param dto 调用记录 DTO
     * @return 保存的实体
     */
    @Transactional
    public ApiCallHistoryEntity record(CallHistoryRecordDTO dto) {
        try {
            ApiCallHistoryEntity entity = buildEntity(dto);
            return repository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to record API call history: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 批量记录 API 调用
     *
     * @param records 调用记录列表
     */
    @Transactional
    public void batchRecord(List<CallHistoryRecordDTO> records) {
        if (records == null || records.isEmpty()) {
            return;
        }

        try {
            List<ApiCallHistoryEntity> entities = records.stream()
                    .map(this::buildEntity)
                    .collect(Collectors.toList());
            repository.saveAll(entities);
            log.debug("Batch recorded {} API call history entries", entities.size());
        } catch (Exception e) {
            log.warn("Failed to batch record API call history: {}", e.getMessage());
        }
    }

    /**
     * 分页查询调用历史
     *
     * @param query 查询条件
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<ApiCallHistoryEntity> query(CallHistoryQueryDTO query) {
        LocalDateTime startTime = parseTime(query.getStartTime());
        LocalDateTime endTime = parseTime(query.getEndTime());

        // 默认查询最近7天
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        // 构建分页和排序
        Sort sort = Sort.by(Sort.Direction.fromString(
                query.getSortDirection() != null ? query.getSortDirection() : "desc"),
                query.getSortField() != null ? query.getSortField() : "createdAt");
        PageRequest pageRequest = PageRequest.of(
                query.getPage() != null ? query.getPage() : 0,
                query.getSize() != null ? query.getSize() : 20,
                sort);

        return repository.findWithFilters(
                startTime, endTime,
                query.getModelName(),
                query.getServiceType(),
                query.getApiKeyId(),
                query.getIsSuccess(),
                query.getHttpStatusCode(),
                pageRequest);
    }

    /**
     * 根据 traceId 查询调用链路
     *
     * @param traceId 追踪 ID
     * @return 调用记录列表
     */
    @Transactional(readOnly = true)
    public List<ApiCallHistoryEntity> findByTraceId(String traceId) {
        return repository.findByTraceId(traceId);
    }

    /**
     * 获取最近的调用记录
     *
     * @param limit 最大数量
     * @return 调用记录列表
     */
    @Transactional(readOnly = true)
    public List<ApiCallHistoryEntity> findRecent(int limit) {
        PageRequest pageRequest = PageRequest.of(0, Math.min(limit, 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(pageRequest).getContent();
    }

    /**
     * 查询错误调用
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     最大数量
     * @return 错误调用列表
     */
    @Transactional(readOnly = true)
    public List<ApiCallHistoryEntity> findErrors(LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return repository.findErrors(
                startTime != null ? startTime : LocalDateTime.now().minusDays(7),
                endTime != null ? endTime : LocalDateTime.now(),
                PageRequest.of(0, Math.min(limit, 100)));
    }

    /**
     * 查询慢调用
     *
     * @param threshold 慢调用阈值 (毫秒)
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     最大数量
     * @return 慢调用列表
     */
    @Transactional(readOnly = true)
    public List<ApiCallHistoryEntity> findSlowCalls(Long threshold, LocalDateTime startTime,
                                                     LocalDateTime endTime, int limit) {
        long effectiveThreshold = threshold != null ? threshold : properties.getSlowCallThresholdMs();
        return repository.findSlowCalls(
                effectiveThreshold,
                startTime != null ? startTime : LocalDateTime.now().minusDays(7),
                endTime != null ? endTime : LocalDateTime.now(),
                PageRequest.of(0, Math.min(limit, 100)));
    }

    /**
     * 获取统计信息
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计信息 DTO
     */
    @Transactional(readOnly = true)
    public CallHistoryStatisticsDTO getStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        // 默认查询最近7天
        LocalDateTime effectiveStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEndTime = endTime != null ? endTime : LocalDateTime.now();

        // 构建统计 DTO
        CallHistoryStatisticsDTO dto = CallHistoryStatisticsDTO.builder()
                .startTime(effectiveStartTime.format(FORMATTER))
                .endTime(effectiveEndTime.format(FORMATTER))
                .build();

        // 汇总统计 - 使用独立查询避免 H2 Object[] 映射问题
        try {
            long totalReqs = repository.countAllInRange(effectiveStartTime, effectiveEndTime);
            long totalToks = repository.sumTokensInRange(effectiveStartTime, effectiveEndTime);
            double avgResp = repository.avgResponseTimeInRange(effectiveStartTime, effectiveEndTime);
            long successReqs = repository.countSuccessInRange(effectiveStartTime, effectiveEndTime);

            dto.setTotalRequests(totalReqs);
            dto.setTotalTokens(totalToks);
            dto.setAvgResponseTimeMs(avgResp);
            dto.setSuccessfulRequests(successReqs);

            if (totalReqs > 0) {
                dto.setSuccessRate((double) successReqs / totalReqs * 100);
                dto.setFailedRequests(totalReqs - successReqs);
                dto.setAvgTokensPerRequest((double) totalToks / totalReqs);
            } else {
                dto.setSuccessRate(0.0);
                dto.setFailedRequests(0L);
                dto.setAvgTokensPerRequest(0.0);
            }
        } catch (Exception e) {
            log.warn("Failed to get summary statistics: {}", e.getMessage());
        }

        // 按模型统计
        try {
            List<Object[]> byModel = repository.countByModel(effectiveStartTime, effectiveEndTime);
            dto.setByModel(safeMapByModel(byModel));
        } catch (Exception e) {
            log.warn("Failed to get model statistics: {}", e.getMessage());
            dto.setByModel(new ArrayList<>());
        }

        // 按服务类型统计
        try {
            List<Object[]> byServiceType = repository.countByServiceType(effectiveStartTime, effectiveEndTime);
            dto.setByServiceType(safeMapByServiceType(byServiceType));
        } catch (Exception e) {
            log.warn("Failed to get service type statistics: {}", e.getMessage());
            dto.setByServiceType(new ArrayList<>());
        }

        // 按日期统计
        try {
            List<Object[]> byDay = repository.countByDay(effectiveStartTime, effectiveEndTime);
            dto.setByDay(safeMapByDay(byDay));
        } catch (Exception e) {
            log.warn("Failed to get daily statistics: {}", e.getMessage());
            dto.setByDay(new ArrayList<>());
        }

        // 按小时统计（今天）
        try {
            List<Object[]> byHour = repository.countByHour(effectiveEndTime.toLocalDate().toString());
            dto.setByHour(safeMapByHour(byHour));
        } catch (Exception e) {
            log.warn("Failed to get hourly statistics: {}", e.getMessage());
            dto.setByHour(new ArrayList<>());
        }

        // HTTP 状态码分布
        try {
            List<Object[]> byStatusCode = repository.countByStatusCode(effectiveStartTime, effectiveEndTime);
            dto.setByStatusCode(safeMapByStatusCode(byStatusCode));
        } catch (Exception e) {
            log.warn("Failed to get status code statistics: {}", e.getMessage());
            dto.setByStatusCode(new ArrayList<>());
        }

        // 错误码分布
        try {
            List<Object[]> byErrorCode = repository.countByErrorCode(effectiveStartTime, effectiveEndTime);
            dto.setByErrorCode(safeMapByErrorCode(byErrorCode));
        } catch (Exception e) {
            log.warn("Failed to get error code statistics: {}", e.getMessage());
            dto.setByErrorCode(new ArrayList<>());
        }

        return dto;
    }

    /**
     * 清理过期数据
     *
     * @param cutoffTime 截止时间
     * @return 删除的记录数
     */
    @Transactional
    public int cleanup(LocalDateTime cutoffTime) {
        log.info("Cleaning up API call history records before {}", cutoffTime);
        int deleted = repository.deleteByCreatedAtBefore(cutoffTime);
        log.info("Cleaned up {} API call history records", deleted);
        return deleted;
    }

    /**
     * 根据保留天数清理过期数据
     *
     * @return 删除的记录数
     */
    @Transactional
    public int cleanupByRetentionDays() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(properties.getRetentionDays());
        return cleanup(cutoffTime);
    }

    /**
     * 获取总记录数
     *
     * @return 记录总数
     */
    @Transactional(readOnly = true)
    public long countAll() {
        return repository.countAll();
    }

    /**
     * 构建实体
     */
    private ApiCallHistoryEntity buildEntity(CallHistoryRecordDTO dto) {
        // 生成摘要
        String requestBodySummary = null;
        if (properties.isRequestBodySummaryEnabled() && dto.getRequestBody() != null) {
            requestBodySummary = truncateAndSummarize(dto.getRequestBody(),
                    properties.getRequestBodySummaryMaxLength());
        }

        String responseBodySummary = null;
        if (properties.isResponseBodySummaryEnabled() && dto.getResponseBody() != null) {
            responseBodySummary = truncateAndSummarize(dto.getResponseBody(),
                    properties.getResponseBodySummaryMaxLength());
        }

        return ApiCallHistoryEntity.builder()
                .traceId(dto.getTraceId() != null ? dto.getTraceId() : UUID.randomUUID().toString())
                .requestId(dto.getRequestId() != null ? dto.getRequestId() : UUID.randomUUID().toString())
                .requestMethod(dto.getRequestMethod() != null ? dto.getRequestMethod() : "POST")
                .requestPath(dto.getRequestPath() != null ? dto.getRequestPath() : "/unknown")
                .requestBodySummary(requestBodySummary)
                .contentType(dto.getContentType())
                .serviceType(dto.getServiceType() != null ? dto.getServiceType() : "unknown")
                .modelName(dto.getModelName() != null ? dto.getModelName() : "unknown")
                .provider(dto.getProvider())
                .instanceName(dto.getInstanceName())
                .instanceUrl(dto.getInstanceUrl())
                .httpStatusCode(dto.getHttpStatusCode())
                .responseBodySummary(responseBodySummary)
                .promptTokens(dto.getPromptTokens() != null ? dto.getPromptTokens() : 0L)
                .completionTokens(dto.getCompletionTokens() != null ? dto.getCompletionTokens() : 0L)
                .totalTokens(dto.getTotalTokens() != null ? dto.getTotalTokens() : 0L)
                .responseTimeMs(dto.getResponseTimeMs())
                .isSuccess(dto.getIsSuccess() != null ? dto.getIsSuccess() : true)
                .errorCode(dto.getErrorCode())
                .errorMessage(dto.getErrorMessage())
                .apiKeyId(dto.getApiKeyId())
                .userId(dto.getUserId())
                .clientIp(dto.getClientIp())
                .userAgent(dto.getUserAgent())
                .rateLimited(dto.getRateLimited() != null ? dto.getRateLimited() : false)
                .circuitBroken(dto.getCircuitBroken() != null ? dto.getCircuitBroken() : false)
                .build();
    }

    // ========== 安全映射方法 ==========

    private List<CallHistoryStatisticsDTO.ModelStats> safeMapByModel(List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.ModelStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 5) {
                continue;
            }
            try {
                long reqCount = toLong(row[1]);
                long successCount = toLong(row[4]);
                result.add(CallHistoryStatisticsDTO.ModelStats.builder()
                        .modelName(String.valueOf(row[0]))
                        .requestCount(reqCount)
                        .totalTokens(toLong(row[2]))
                        .avgResponseTimeMs(toDouble(row[3]))
                        .successCount(successCount)
                        .successRate(reqCount > 0 ? (double) successCount / reqCount * 100 : 0.0)
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid model stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    private List<CallHistoryStatisticsDTO.ServiceTypeStats> safeMapByServiceType(List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.ServiceTypeStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 4) {
                continue;
            }
            try {
                result.add(CallHistoryStatisticsDTO.ServiceTypeStats.builder()
                        .serviceType(String.valueOf(row[0]))
                        .requestCount(toLong(row[1]))
                        .totalTokens(toLong(row[2]))
                        .avgResponseTimeMs(toDouble(row[3]))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid service type stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    private List<CallHistoryStatisticsDTO.DailyStats> safeMapByDay(List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.DailyStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 3) {
                continue;
            }
            try {
                result.add(CallHistoryStatisticsDTO.DailyStats.builder()
                        .date(String.valueOf(row[0]))
                        .requestCount(toLong(row[1]))
                        .totalTokens(toLong(row[2]))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid daily stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    private List<CallHistoryStatisticsDTO.HourlyStats> safeMapByHour(List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.HourlyStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            try {
                int hour = toLong(row[0]).intValue();
                result.add(CallHistoryStatisticsDTO.HourlyStats.builder()
                        .hour(hour)
                        .requestCount(toLong(row[1]))
                        .label(String.format("%02d:00", hour))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid hourly stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    private List<CallHistoryStatisticsDTO.StatusCodeStats> safeMapByStatusCode(List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.StatusCodeStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            try {
                result.add(CallHistoryStatisticsDTO.StatusCodeStats.builder()
                        .statusCode(toLong(row[0]).intValue())
                        .count(toLong(row[1]))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid status code stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    private List<CallHistoryStatisticsDTO.ErrorCodeStats> safeMapByErrorCode(List<Object[]> rows) {
        List<CallHistoryStatisticsDTO.ErrorCodeStats> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2) {
                continue;
            }
            try {
                result.add(CallHistoryStatisticsDTO.ErrorCodeStats.builder()
                        .errorCode(String.valueOf(row[0]))
                        .count(toLong(row[1]))
                        .build());
            } catch (Exception e) {
                log.debug("Skipping invalid error code stats row: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 截断并生成摘要
     */
    private String truncateAndSummarize(String content, int maxLength) {
        if (content == null) {
            return null;
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...[truncated]";
    }

    /**
     * 解析时间字符串
     */
    private LocalDateTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(timeStr, FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse time: {}", timeStr);
            return null;
        }
    }

    /**
     * 安全转换为 Long
     */
    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    /**
     * 安全转换为 Double
     */
    private Double toDouble(Object value) {
        if (value == null) {
            return 0.0;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }
}
