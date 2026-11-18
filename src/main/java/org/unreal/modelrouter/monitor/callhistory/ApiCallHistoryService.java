package org.unreal.modelrouter.monitor.callhistory;

import org.unreal.modelrouter.auth.security.permission.DataScope;
import org.unreal.modelrouter.persistence.jpa.repository.spec.ApiCallHistorySpecifications;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.monitor.callhistory.config.CallHistoryProperties;
import org.unreal.modelrouter.monitor.callhistory.config.RecordLevel;
import org.unreal.modelrouter.monitor.callhistory.crypto.RecordContentCipher;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryQueryDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryRecordDTO;
import org.unreal.modelrouter.monitor.callhistory.dto.CallHistoryStatisticsDTO;
import org.unreal.modelrouter.persistence.jpa.entity.ApiCallHistoryEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ApiCallHistoryRepository;
import org.unreal.modelrouter.persistence.jpa.repository.spec.ApiCallHistorySpecifications;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private final RecordContentCipher recordContentCipher;
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
        return query(query, DataScope.unrestricted());
    }

    /**
     * 分页查询，带数据面归属范围。
     *
     * <p>{@code scope} 受限时只返回「本人」的记录（userId 命中或密钥属于本人），
     * 并在同一处覆盖前端传入的 userId，避免出现「前端传别人 id 就能看别人数据」的越权。</p>
     *
     * @param query 查询条件
     * @param scope 数据面范围
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<ApiCallHistoryEntity> query(final CallHistoryQueryDTO query, final DataScope scope) {
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

        return repository.findAll(
                ApiCallHistorySpecifications.withFilters(
                        startTime, endTime,
                        query.getModelName(),
                        query.getServiceType(),
                        query.getApiKeyId(),
                        query.getIsSuccess(),
                        query.getHttpStatusCode(),
                        // 受限主体强制用服务端解析的账号名，忽略前端传的 userId，避免越权查询
                        scope.filterByUser() ? scope.userId() : query.getUserId(),
                        scope.filterByKeys() ? scope.apiKeyIds() : null),
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
    /**
     * 最近调用，带数据面范围。
     *
     * @param limit 最大数量
     * @param scope 数据面范围
     * @return 记录列表
     */
    @Transactional(readOnly = true)
    public List<ApiCallHistoryEntity> findRecent(final int limit, final DataScope scope) {
        return findScoped(ApiCallHistorySpecifications.withFilters(null, null, null, null, null, null, null,
                scope.filterByUser() ? scope.userId() : null,
                scope.filterByKeys() ? scope.apiKeyIds() : null), limit);
    }

    /**
     * 失败调用，带数据面范围。
     *
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @param limit     最大数量
     * @param scope     数据面范围
     * @return 记录列表
     */
    @Transactional(readOnly = true)
    public List<ApiCallHistoryEntity> findErrors(final LocalDateTime startTime, final LocalDateTime endTime,
                                                 final int limit, final DataScope scope) {
        return findScoped(ApiCallHistorySpecifications.errors(startTime, endTime)
                .and(ApiCallHistorySpecifications.ownership(
                        scope.filterByUser() ? scope.userId() : null,
                        scope.filterByKeys() ? scope.apiKeyIds() : null)), limit);
    }

    /**
     * 慢调用，带数据面范围。
     *
     * @param threshold 阈值毫秒
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @param limit     最大数量
     * @param scope     数据面范围
     * @return 记录列表
     */
    @Transactional(readOnly = true)
    public List<ApiCallHistoryEntity> findSlowCalls(final Long threshold, final LocalDateTime startTime,
                                                    final LocalDateTime endTime, final int limit,
                                                    final DataScope scope) {
        return findScoped(ApiCallHistorySpecifications.slowCalls(threshold, startTime, endTime)
                .and(ApiCallHistorySpecifications.ownership(
                        scope.filterByUser() ? scope.userId() : null,
                        scope.filterByKeys() ? scope.apiKeyIds() : null)), limit);
    }

    private List<ApiCallHistoryEntity> findScoped(final Specification<ApiCallHistoryEntity> spec,
                                                  final int limit) {
        final PageRequest pageRequest = PageRequest.of(0, Math.max(1, Math.min(limit, 200)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findAll(spec, pageRequest).getContent();
    }

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
        return repository.findAll(
                ApiCallHistorySpecifications.errors(
                        startTime != null ? startTime : LocalDateTime.now().minusDays(7),
                        endTime != null ? endTime : LocalDateTime.now()),
                PageRequest.of(0, Math.min(limit, 100))).getContent();
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
        return repository.findAll(
                ApiCallHistorySpecifications.slowCalls(
                        effectiveThreshold,
                        startTime != null ? startTime : LocalDateTime.now().minusDays(7),
                        endTime != null ? endTime : LocalDateTime.now()),
                PageRequest.of(0, Math.min(limit, 100))).getContent();
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
        // 曾经这里委托 3 参重载、3 参在 unrestricted 时又委托回 2 参 ⇒ 无限递归（StackOverflowError）。
        // 全局聚合只有一个实现（getStatisticsGlobal），两个入口都直接调它。
        return getStatisticsGlobal(startTime, endTime);
    }

    /**
     * 统计信息，带数据面归属范围。
     *
     * <p>全局读走原有的聚合查询；「本人读」走窗口内记录的内存聚合（上限 {@value #SELF_SCOPE_MAX_ROWS} 行）。
     * 之所以不把八个 {@code @Query} 聚合全部加参数：那要改 8 条 JPQL、且每条都要重复一遍归属条件，
     * 改动面与出错概率都远大于按需拉一份个人记录在内存里聚合。数据量上界由常量兜住，超出部分截断并记日志。</p>
     *
     * @param startTime 起始时间
     * @param endTime   结束时间
     * @param scope     数据面范围
     * @return 统计结果
     */
    @Transactional(readOnly = true)
    public CallHistoryStatisticsDTO getStatistics(final LocalDateTime startTime,
                                                  final LocalDateTime endTime,
                                                  final DataScope scope) {
        if (!scope.restricted()) {
            return getStatisticsGlobal(startTime, endTime);
        }
        final LocalDateTime effectiveStart = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        final LocalDateTime effectiveEnd = endTime != null ? endTime : LocalDateTime.now();
        final List<ApiCallHistoryEntity> rows = repository.findAll(
                ApiCallHistorySpecifications.withFilters(effectiveStart, effectiveEnd, null, null, null, null, null,
                        scope.filterByUser() ? scope.userId() : null,
                        scope.filterByKeys() ? scope.apiKeyIds() : null),
                PageRequest.of(0, SELF_SCOPE_MAX_ROWS, Sort.by(Sort.Direction.ASC, "createdAt")))
                .getContent();
        if (rows.size() >= SELF_SCOPE_MAX_ROWS) {
            log.warn("本人读统计达到上限 {} 行，结果按前 N 行计算：user={}", SELF_SCOPE_MAX_ROWS, scope.userId());
        }
        return aggregateInMemory(rows, effectiveStart, effectiveEnd);
    }

    private CallHistoryStatisticsDTO aggregateInMemory(final List<ApiCallHistoryEntity> rows,
                                                       final LocalDateTime startTime,
                                                       final LocalDateTime endTime) {
        final long total = rows.size();
        final long success = rows.stream().filter(row -> Boolean.TRUE.equals(row.getIsSuccess())).count();
        final long tokens = rows.stream().mapToLong(row -> row.getTotalTokens() == null ? 0L : row.getTotalTokens()).sum();
        final double avgResponse = rows.stream()
                .mapToDouble(row -> row.getResponseTimeMs() == null ? 0D : row.getResponseTimeMs())
                .average().orElse(0D);
        final CallHistoryStatisticsDTO dto = CallHistoryStatisticsDTO.builder()
                .startTime(startTime.format(FORMATTER))
                .endTime(endTime.format(FORMATTER))
                .totalRequests(total)
                .successfulRequests(success)
                .failedRequests(total - success)
                .successRate(total == 0 ? 0D : (double) success / total * 100)
                .totalTokens(tokens)
                .avgResponseTimeMs(avgResponse)
                .avgTokensPerRequest(total == 0 ? 0D : (double) tokens / total)
                .byModel(groupByModel(rows))
                .byServiceType(groupByServiceType(rows))
                .byDay(groupByDay(rows))
                .byHour(groupByHour(rows))
                .byStatusCode(groupByStatusCode(rows))
                .byErrorCode(groupByErrorCode(rows))
                .build();
        return dto;
    }

    private static List<CallHistoryStatisticsDTO.ModelStats> groupByModel(final List<ApiCallHistoryEntity> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(row -> nullSafe(row.getModelName())))
                .entrySet().stream()
                .map(entry -> {
                    final List<ApiCallHistoryEntity> group = entry.getValue();
                    final long groupSuccess = group.stream().filter(row -> Boolean.TRUE.equals(row.getIsSuccess())).count();
                    return CallHistoryStatisticsDTO.ModelStats.builder()
                            .modelName(entry.getKey())
                            .requestCount((long) group.size())
                            .totalTokens(group.stream().mapToLong(row -> row.getTotalTokens() == null ? 0L : row.getTotalTokens()).sum())
                            .avgResponseTimeMs(group.stream().mapToDouble(row -> row.getResponseTimeMs() == null ? 0D : row.getResponseTimeMs()).average().orElse(0D))
                            .successCount(groupSuccess)
                            .successRate((double) groupSuccess / group.size() * 100)
                            .build();
                })
                .sorted(Comparator.comparing(CallHistoryStatisticsDTO.ModelStats::getRequestCount).reversed())
                .toList();
    }

    private static List<CallHistoryStatisticsDTO.ServiceTypeStats> groupByServiceType(final List<ApiCallHistoryEntity> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(row -> nullSafe(row.getServiceType())))
                .entrySet().stream()
                .map(entry -> CallHistoryStatisticsDTO.ServiceTypeStats.builder()
                        .serviceType(entry.getKey())
                        .requestCount((long) entry.getValue().size())
                        .totalTokens(entry.getValue().stream().mapToLong(row -> row.getTotalTokens() == null ? 0L : row.getTotalTokens()).sum())
                        .avgResponseTimeMs(entry.getValue().stream().mapToDouble(row -> row.getResponseTimeMs() == null ? 0D : row.getResponseTimeMs()).average().orElse(0D))
                        .build())
                .sorted(Comparator.comparing(CallHistoryStatisticsDTO.ServiceTypeStats::getRequestCount).reversed())
                .toList();
    }

    private static List<CallHistoryStatisticsDTO.DailyStats> groupByDay(final List<ApiCallHistoryEntity> rows) {
        return rows.stream()
                .filter(row -> row.getCreatedAt() != null)
                .collect(Collectors.groupingBy(row -> row.getCreatedAt().toLocalDate().toString()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> CallHistoryStatisticsDTO.DailyStats.builder()
                        .date(entry.getKey())
                        .requestCount((long) entry.getValue().size())
                        .totalTokens(entry.getValue().stream().mapToLong(row -> row.getTotalTokens() == null ? 0L : row.getTotalTokens()).sum())
                        .build())
                .toList();
    }

    private static List<CallHistoryStatisticsDTO.HourlyStats> groupByHour(final List<ApiCallHistoryEntity> rows) {
        final Map<Integer, Long> counts = new TreeMap<>();
        for (ApiCallHistoryEntity row : rows) {
            if (row.getCreatedAt() != null) {
                counts.merge(row.getCreatedAt().getHour(), 1L, Long::sum);
            }
        }
        return counts.entrySet().stream()
                .map(entry -> CallHistoryStatisticsDTO.HourlyStats.builder()
                        .hour(entry.getKey())
                        .label(String.format("%02d:00", entry.getKey()))
                        .requestCount(entry.getValue())
                        .build())
                .toList();
    }

    private static List<CallHistoryStatisticsDTO.StatusCodeStats> groupByStatusCode(final List<ApiCallHistoryEntity> rows) {
        return rows.stream()
                .filter(row -> row.getHttpStatusCode() != null)
                .collect(Collectors.groupingBy(ApiCallHistoryEntity::getHttpStatusCode, TreeMap::new, Collectors.counting()))
                .entrySet().stream()
                .map(entry -> CallHistoryStatisticsDTO.StatusCodeStats.builder()
                        .statusCode(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private static List<CallHistoryStatisticsDTO.ErrorCodeStats> groupByErrorCode(final List<ApiCallHistoryEntity> rows) {
        return rows.stream()
                .filter(row -> row.getErrorCode() != null && !row.getErrorCode().isBlank())
                .collect(Collectors.groupingBy(ApiCallHistoryEntity::getErrorCode, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> CallHistoryStatisticsDTO.ErrorCodeStats.builder()
                        .errorCode(entry.getKey())
                        .count(entry.getValue())
                        .build())
                .toList();
    }

    private static String nullSafe(final String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /**
     * 本人读统计的行数上限：个人记录量级远小于全局，超过说明样本不典型，截断并告警。
     */
    private static final int SELF_SCOPE_MAX_ROWS = 5000;

    @Transactional(readOnly = true)
    public CallHistoryStatisticsDTO getStatisticsGlobal(LocalDateTime startTime, LocalDateTime endTime) {
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
            dto.setByModel(ApiCallHistoryStatisticsMapper.mapByModel(byModel));
        } catch (Exception e) {
            log.warn("Failed to get model statistics: {}", e.getMessage());
            dto.setByModel(new ArrayList<>());
        }

        // 按服务类型统计
        try {
            List<Object[]> byServiceType = repository.countByServiceType(effectiveStartTime, effectiveEndTime);
            dto.setByServiceType(ApiCallHistoryStatisticsMapper.mapByServiceType(byServiceType));
        } catch (Exception e) {
            log.warn("Failed to get service type statistics: {}", e.getMessage());
            dto.setByServiceType(new ArrayList<>());
        }

        // 按日期统计
        try {
            List<Object[]> byDay = repository.countByDay(effectiveStartTime, effectiveEndTime);
            dto.setByDay(ApiCallHistoryStatisticsMapper.mapByDay(byDay));
        } catch (Exception e) {
            log.warn("Failed to get daily statistics: {}", e.getMessage());
            dto.setByDay(new ArrayList<>());
        }

        // 按小时统计（今天）
        try {
            List<Object[]> byHour = repository.countByHour(effectiveEndTime.toLocalDate().toString());
            dto.setByHour(ApiCallHistoryStatisticsMapper.mapByHour(byHour));
        } catch (Exception e) {
            log.warn("Failed to get hourly statistics: {}", e.getMessage());
            dto.setByHour(new ArrayList<>());
        }

        // HTTP 状态码分布
        try {
            List<Object[]> byStatusCode = repository.countByStatusCode(effectiveStartTime, effectiveEndTime);
            dto.setByStatusCode(ApiCallHistoryStatisticsMapper.mapByStatusCode(byStatusCode));
        } catch (Exception e) {
            log.warn("Failed to get status code statistics: {}", e.getMessage());
            dto.setByStatusCode(new ArrayList<>());
        }

        // 错误码分布
        try {
            List<Object[]> byErrorCode = repository.countByErrorCode(effectiveStartTime, effectiveEndTime);
            dto.setByErrorCode(ApiCallHistoryStatisticsMapper.mapByErrorCode(byErrorCode));
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
     * 根据 ID 查询单条记录
     *
     * @param id 记录 ID
     * @return 实体（Optional 包装）
     */
    @Transactional(readOnly = true)
    public Optional<ApiCallHistoryEntity> findById(final Long id) {
        return repository.findById(id);
    }

    /**
     * 构建实体
     */
    private ApiCallHistoryEntity buildEntity(CallHistoryRecordDTO dto) {
        RecordLevel level = properties.getRecordLevel();
        String recordLevelStr = level.name();

        // 生成摘要（所有级别都可能用到）
        String requestBodySummary = null;
        String responseBodySummary = null;
        String requestBodyEncrypted = null;
        String responseBodyEncrypted = null;

        switch (level) {
            case METADATA_ONLY -> {
                // 仅元数据模式：根据配置生成摘要，不捕获内容
                if (properties.isRequestBodySummaryEnabled() && dto.getRequestBody() != null) {
                    requestBodySummary = truncateAndSummarize(dto.getRequestBody(),
                            properties.getRequestBodySummaryMaxLength());
                }
                if (properties.isResponseBodySummaryEnabled() && dto.getResponseBody() != null) {
                    responseBodySummary = truncateAndSummarize(dto.getResponseBody(),
                            properties.getResponseBodySummaryMaxLength());
                }
            }
            case SUMMARY -> {
                // 摘要模式：适配器层已脱敏，截断后存入摘要列
                if (dto.getRequestBody() != null) {
                    String safeBody = truncateToMaxLength(dto.getRequestBody());
                    requestBodySummary = truncateAndSummarize(safeBody,
                            properties.getRequestBodySummaryMaxLength());
                }
                if (dto.getResponseBody() != null) {
                    String safeBody = truncateToMaxLength(dto.getResponseBody());
                    responseBodySummary = truncateAndSummarize(safeBody,
                            properties.getResponseBodySummaryMaxLength());
                }
            }
            case FULL -> {
                // 完整模式：加密存储，摘要列存预览
                if (dto.getRequestBody() != null) {
                    String rawBody = truncateToMaxLength(dto.getRequestBody());
                    requestBodyEncrypted = recordContentCipher.encrypt(rawBody);
                    requestBodySummary = truncateAndSummarize(rawBody,
                            properties.getRequestBodySummaryMaxLength());
                }
                if (dto.getResponseBody() != null) {
                    String rawBody = truncateToMaxLength(dto.getResponseBody());
                    responseBodyEncrypted = recordContentCipher.encrypt(rawBody);
                    responseBodySummary = truncateAndSummarize(rawBody,
                            properties.getResponseBodySummaryMaxLength());
                }
            }
        }

        return ApiCallHistoryEntity.builder()
                .traceId(dto.getTraceId() != null
                        ? dto.getTraceId()
                        : UUID.randomUUID().toString())
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
                .recordLevel(recordLevelStr)
                .requestBodyEncrypted(requestBodyEncrypted)
                .responseBodyEncrypted(responseBodyEncrypted)
                .build();
    }

    /**
     * 解密实体中加密的请求/响应体内容
     * 解密结果填充到实体的 @Transient 字段中
     *
     * @param entity 需要解密的实体
     * @return 填充了解密内容的同一实体
     */
    public ApiCallHistoryEntity decryptBodies(ApiCallHistoryEntity entity) {
        if (entity == null) {
            return null;
        }
        try {
            if (entity.getRequestBodyEncrypted() != null) {
                entity.setRequestBodyDecrypted(
                        recordContentCipher.decrypt(entity.getRequestBodyEncrypted()));
            }
        } catch (Exception e) {
            log.warn("解密请求体失败: id={}", entity.getId(), e);
        }
        try {
            if (entity.getResponseBodyEncrypted() != null) {
                entity.setResponseBodyDecrypted(
                        recordContentCipher.decrypt(entity.getResponseBodyEncrypted()));
            }
        } catch (Exception e) {
            log.warn("解密响应体失败: id={}", entity.getId(), e);
        }
        return entity;
    }

    /**
     * 按最大内容长度截断内容
     */
    private String truncateToMaxLength(String content) {
        if (content == null) {
            return null;
        }
        int maxLen = properties.getMaxContentLength();
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen);
    }

    // ========== 工具方法（委托给 ApiCallHistoryStatisticsMapper）==========

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

    private Long toLong(Object value) {
        return ApiCallHistoryStatisticsMapper.toLong(value);
    }

    private Double toDouble(Object value) {
        return ApiCallHistoryStatisticsMapper.toDouble(value);
    }
}
