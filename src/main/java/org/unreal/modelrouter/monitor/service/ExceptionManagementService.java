package org.unreal.modelrouter.monitor.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.monitor.dto.ExceptionEventDTO;
import org.unreal.modelrouter.monitor.dto.ExceptionQueryRequest;
import org.unreal.modelrouter.monitor.dto.ExceptionStatisticsDTO;
import org.unreal.modelrouter.common.dto.PagedResult;
import org.unreal.modelrouter.persistence.jpa.entity.ExceptionEventEntity;
import org.unreal.modelrouter.persistence.jpa.repository.ExceptionEventRepository;
import org.springframework.data.jpa.domain.Specification;
import org.unreal.modelrouter.auth.security.permission.DataScope;
import org.unreal.modelrouter.persistence.jpa.repository.spec.ExceptionEventSpecifications;
import org.unreal.modelrouter.monitor.monitoring.error.ExceptionPersistenceService;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 异常管理服务
 * 提供异常事件的查询、统计和管理功能
 * 
 * @author JAiRouter Team
 * @since 1.9.2
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExceptionManagementService {

    private final ExceptionEventRepository exceptionEventRepository;
    private final ExceptionPersistenceService exceptionPersistenceService;


    /**
     * 受限视角下内存聚合的行数上限 —— 与 TokenUsage 的取舍一致：超过上限按前 N 行计算并打 warn，
     * 宁可有界地少算，也不要无界地把整表拉进内存。
     */
    private static final int MAX_SCOPED_ROWS = 20_000;

    /** 计数字段累加（键为 null 时不计入）。 */
    private static void merge(final Map<String, Long> target, final String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        target.merge(key, 1L, Long::sum);
    }

    /** 取计数最大的前 N 项（保持降序）。 */
    private static List<Map.Entry<String, Long>> topN(final Map<String, Long> counts, final int limit) {
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 查询异常事件列表
     * 
     * @param request 查询请求
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PagedResult<ExceptionEventDTO> queryExceptionEvents(final ExceptionQueryRequest request) {
        return queryExceptionEvents(request, DataScope.unrestricted());
    }

    /**
     * 查询异常事件列表（带数据范围，issue #1189）。
     *
     * <p>受限范围（本人读码）只返回「本人 userId 或本人创建的 API Key」触发的异常。</p>
     *
     * @param request 查询请求
     * @param scope   数据范围；{@code null} 等价于不受限
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PagedResult<ExceptionEventDTO> queryExceptionEvents(final ExceptionQueryRequest request,
                                                              final DataScope scope) {
        // 设置默认时间范围
        LocalDateTime startTime = request.getStartTime();
        LocalDateTime endTime = request.getEndTime();
        
        if (startTime == null) {
            startTime = LocalDateTime.now().minusDays(7);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        // 设置分页和排序
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? Math.min(request.getSize(), 100) : 20;
        String sortBy = request.getSortBy() != null ? request.getSortBy() : "occurredAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(request.getSortDirection()) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, sortBy));

        // 执行查询
        Specification<ExceptionEventEntity> spec = ExceptionEventSpecifications.of(
                        startTime,
                        endTime,
                        request.getExceptionType(),
                        request.getOperation(),
                        request.getErrorCode(),
                        request.getErrorCategory(),
                        request.getTraceId(),
                        request.getClientIp(),
                        request.getAggregatedOnly(),
                request.getServiceType(),
                request.getModelName());
        Specification<ExceptionEventEntity> ownership = ExceptionEventSpecifications.ownership(scope);
        if (ownership != null) {
            spec = spec.and(ownership);
        }
        Page<ExceptionEventEntity> entityPage = exceptionEventRepository.findAll(spec, pageRequest);

        // 转换为 DTO
        List<ExceptionEventDTO> content = entityPage.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        // 构建分页结果
        PagedResult<ExceptionEventDTO> result = new PagedResult<>();
        result.setContent(content);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(entityPage.getTotalElements());
        result.setTotalPages(entityPage.getTotalPages());
        result.setFirst(entityPage.isFirst());
        result.setLast(entityPage.isLast());
        result.setHasNext(entityPage.hasNext());
        result.setHasPrevious(entityPage.hasPrevious());

        return result;
    }

    /**
     * 根据 ID 查询异常事件详情
     * 
     * @param eventId 事件 ID
     * @return 异常事件 DTO，不存在返回 null
     */
    @Transactional(readOnly = true)
    public ExceptionEventDTO getExceptionEventById(final String eventId) {
        return getExceptionEventById(eventId, DataScope.unrestricted());
    }

    /**
     * 按 ID 查异常详情（带数据范围，issue #1189）。
     *
     * <p>越界返回 {@code null}，由控制器转成 404 —— 不返回 403，
     * 否则等于告诉调用方「这个 ID 存在，只是不给你看」。</p>
     *
     * @param eventId 事件 ID
     * @param scope   数据范围
     * @return 详情；不存在或越界返回 {@code null}
     */
    @Transactional(readOnly = true)
    public ExceptionEventDTO getExceptionEventById(final String eventId, final DataScope scope) {
        return exceptionEventRepository.findByEventId(eventId)
                .filter(entity -> scope == null || !scope.restricted()
                        || scope.owns(entity.getUserId(), entity.getApiKeyId()))
                .map(this::convertToDTO)
                .orElse(null);
    }

    /**
     * 获取异常统计信息
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 统计信息 DTO
     */
    @Transactional(readOnly = true)
    public ExceptionStatisticsDTO getExceptionStatistics(final LocalDateTime startTime, final LocalDateTime endTime) {
        return getExceptionStatistics(startTime, endTime, DataScope.unrestricted());
    }

    /**
     * 异常统计（带数据范围，issue #1189）。
     *
     * <p>受限视角（本人读码）不复用库内聚合 SQL —— 那 6 个 count 方法都不带归属条件，
     * 逐个加参数会散到 6 处且易漏。这里改为「按归属拉行 + 内存聚合」，口径与不受限版
     * 逐项对齐（总数/按类型/按分类/按操作/按状态码/Top IP/小时分布/聚合与未聚合数）。</p>
     *
     * @param startTime 起始时间，{@code null} 表示近 7 天
     * @param endTime   结束时间，{@code null} 表示当前
     * @param scope     数据范围
     * @return 统计信息
     */
    @Transactional(readOnly = true)
    public ExceptionStatisticsDTO getExceptionStatistics(final LocalDateTime startTime, final LocalDateTime endTime,
                                                          final DataScope scope) {
        if (scope == null || !scope.restricted()) {
            return getExceptionStatisticsUnrestricted(startTime, endTime);
        }
        LocalDateTime effectiveStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEndTime = endTime != null ? endTime : LocalDateTime.now();

        Specification<ExceptionEventEntity> spec = ExceptionEventSpecifications.of(
                effectiveStartTime, effectiveEndTime, null, null, null, null, null, null, null, null, null);
        Specification<ExceptionEventEntity> ownership = ExceptionEventSpecifications.ownership(scope);
        if (ownership != null) {
            spec = spec.and(ownership);
        }
        PageRequest page = PageRequest.of(0, MAX_SCOPED_ROWS, Sort.by(Sort.Direction.DESC, "occurredAt"));
        List<ExceptionEventEntity> rows = exceptionEventRepository.findAll(spec, page).getContent().stream()
                .filter(entity -> scope.owns(entity.getUserId(), entity.getApiKeyId()))
                .collect(Collectors.toList());
        if (rows.size() >= MAX_SCOPED_ROWS) {
            log.warn("本人视角异常统计达到上限 {} 行，结果按前 N 行计算：user={}", MAX_SCOPED_ROWS, scope.userId());
        }

        Map<String, Long> byType = new HashMap<>();
        Map<String, Long> byCategory = new HashMap<>();
        Map<String, Long> byOperation = new HashMap<>();
        Map<String, Long> byHttpStatus = new HashMap<>();
        Map<String, Long> byClientIp = new HashMap<>();
        Map<String, Long> byHour = new HashMap<>();
        long aggregated = 0L;
        long unaggregated = 0L;
        for (ExceptionEventEntity row : rows) {
            merge(byType, row.getExceptionType());
            merge(byCategory, row.getErrorCategory());
            merge(byOperation, row.getOperation());
            merge(byHttpStatus, row.getHttpStatus());
            merge(byClientIp, row.getClientIp());
            if (row.getOccurredAt() != null) {
                merge(byHour, String.format("%02d", row.getOccurredAt().getHour()));
            }
            if (Boolean.TRUE.equals(row.getIsAggregated())) {
                aggregated++;
            } else {
                unaggregated++;
            }
        }

        ExceptionStatisticsDTO dto = ExceptionStatisticsDTO.builder()
                .startTime(effectiveStartTime)
                .endTime(effectiveEndTime)
                .totalCount((long) rows.size())
                .totalTypes(byType.size())
                .byType(byType)
                .byCategory(byCategory)
                .build();
        dto.setAggregatedCount(aggregated);
        dto.setUnaggregatedCount(unaggregated);
        dto.setByOperation(byOperation);
        dto.setByHttpStatus(byHttpStatus);
        dto.setTopClientIps(topN(byClientIp, 10).stream()
                .map(entry -> new ExceptionStatisticsDTO.ClientIpStats(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
        dto.setHourlyDistribution(topN(byHour, 24).stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ExceptionStatisticsDTO.HourlyStats(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList()));
        return dto;
    }

    private ExceptionStatisticsDTO getExceptionStatisticsUnrestricted(final LocalDateTime startTime,
                                                                      final LocalDateTime endTime) {
        // 设置默认时间范围
        LocalDateTime effectiveStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEndTime = endTime != null ? endTime : LocalDateTime.now();

        // 使用 PersistenceService 获取统计
        Map<String, Object> stats =
                exceptionPersistenceService.getExceptionStatistics(effectiveStartTime, effectiveEndTime);
        
        // 获取各类统计数据
        Map<String, Long> byType = (Map<String, Long>) stats.getOrDefault("byType", new HashMap<>());
        Map<String, Long> byCategory = (Map<String, Long>) stats.getOrDefault("byCategory", new HashMap<>());
        Long totalCount = getLongValue(stats, "total");
        
        // 构建统计 DTO
        ExceptionStatisticsDTO dto = ExceptionStatisticsDTO.builder()
                .startTime(effectiveStartTime)
                .endTime(effectiveEndTime)
                .totalCount(totalCount)
                .totalTypes(byType != null ? byType.size() : 0)
                .byType(byType)
                .byCategory(byCategory)
                .build();

        // 查询聚合和未聚合数量
        dto.setAggregatedCount(exceptionEventRepository.countAggregatedEvents());
        dto.setUnaggregatedCount(exceptionEventRepository.countUnaggregatedEvents());

        // 查询按操作统计
        List<Object[]> byOperation =
                exceptionEventRepository.countByOperation(effectiveStartTime, effectiveEndTime, 10);
        Map<String, Long> operationStats = new HashMap<>();
        byOperation.forEach(row -> operationStats.put((String) row[0], (Long) row[1]));
        dto.setByOperation(operationStats);

        // 查询按 HTTP 状态统计
        List<Object[]> byHttpStatus = exceptionEventRepository.countByHttpStatus(effectiveStartTime, effectiveEndTime);
        Map<String, Long> httpStatusStats = new HashMap<>();
        byHttpStatus.forEach(row -> httpStatusStats.put((String) row[0], (Long) row[1]));
        dto.setByHttpStatus(httpStatusStats);

        // 查询 Top 客户端 IP
        List<Object[]> topClientIps =
                exceptionEventRepository.countByClientIp(effectiveStartTime, effectiveEndTime, 10);
        List<ExceptionStatisticsDTO.ClientIpStats> clientIpStats = new ArrayList<>();
        topClientIps.forEach(row -> clientIpStats.add(
                new ExceptionStatisticsDTO.ClientIpStats((String) row[0], (Long) row[1])));
        dto.setTopClientIps(clientIpStats);

        // 查询小时分布
        List<Object[]> hourlyStats = exceptionEventRepository.countByHour(effectiveStartTime, effectiveEndTime);
        List<ExceptionStatisticsDTO.HourlyStats> hourlyDistribution = new ArrayList<>();
        hourlyStats.forEach(row -> hourlyDistribution.add(
                new ExceptionStatisticsDTO.HourlyStats(String.valueOf(row[0]), (Long) row[1])));
        dto.setHourlyDistribution(hourlyDistribution);

        return dto;
    }

    /**
     * 获取最近的异常事件
     * 
     * @param limit 最大数量
     * @return 异常事件列表
     */
    @Transactional(readOnly = true)
    public List<ExceptionEventDTO> getRecentExceptionEvents(final int limit) {
        return getRecentExceptionEvents(limit, DataScope.unrestricted());
    }

    /**
     * 最近的异常事件（带数据范围，issue #1189）。
     *
     * @param limit 最大数量
     * @param scope 数据范围
     * @return 异常事件列表
     */
    @Transactional(readOnly = true)
    public List<ExceptionEventDTO> getRecentExceptionEvents(final int limit, final DataScope scope) {
        int effectiveLimit = Math.min(limit, 100);
        if (scope == null || !scope.restricted()) {
            return exceptionEventRepository.findRecentEvents(effectiveLimit)
                    .stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        }
        Specification<ExceptionEventEntity> ownership = ExceptionEventSpecifications.ownership(scope);
        // 多取一些再截断：受限过滤发生在内存里，否则可能不足 limit 条
        PageRequest page = PageRequest.of(0, effectiveLimit * 5, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return exceptionEventRepository.findAll(ownership, page).getContent().stream()
                .filter(entity -> scope.owns(entity.getUserId(), entity.getApiKeyId()))
                .limit(effectiveLimit)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 获取指定类型的最近异常事件
     * 
     * @param exceptionType 异常类型
     * @param limit 最大数量
     * @return 异常事件列表
     */
    @Transactional(readOnly = true)
    public List<ExceptionEventDTO> getRecentExceptionEventsByType(final String exceptionType, final int limit) {
        return getRecentExceptionEventsByType(exceptionType, limit, DataScope.unrestricted());
    }

    /**
     * 按类型取最近异常（带数据范围，issue #1189）。
     *
     * @param exceptionType 异常类型
     * @param limit         最大数量
     * @param scope         数据范围
     * @return 异常事件列表
     */
    @Transactional(readOnly = true)
    public List<ExceptionEventDTO> getRecentExceptionEventsByType(final String exceptionType, final int limit,
                                                                 final DataScope scope) {
        int effectiveLimit = Math.min(limit, 100);
        if (scope == null || !scope.restricted()) {
            return exceptionEventRepository.findRecentEventsByType(exceptionType, effectiveLimit)
                    .stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
        }
        Specification<ExceptionEventEntity> ownership = ExceptionEventSpecifications.ownership(scope);
        PageRequest page = PageRequest.of(0, effectiveLimit * 5, Sort.by(Sort.Direction.DESC, "occurredAt"));
        return exceptionEventRepository.findAll(ownership, page).getContent().stream()
                .filter(entity -> exceptionType == null || exceptionType.equals(entity.getExceptionType()))
                .filter(entity -> scope.owns(entity.getUserId(), entity.getApiKeyId()))
                .limit(effectiveLimit)
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 删除过期异常事件
     * 
     * @param cutoffTime 截止时间
     * @return 删除的数量
     */
    @Transactional
    public int deleteOldExceptionEvents(LocalDateTime cutoffTime) {
        log.info("删除截止时间 {} 之前的异常事件", cutoffTime);
        return exceptionEventRepository.deleteByOccurredAtBefore(cutoffTime);
    }

    /**
     * 删除已聚合的过期异常事件
     * 
     * @param cutoffTime 截止时间
     * @return 删除的数量
     */
    @Transactional
    public int deleteAggregatedExceptionEvents(LocalDateTime cutoffTime) {
        log.info("删除截止时间 {} 之前已聚合的异常事件", cutoffTime);
        return exceptionEventRepository.deleteAggregatedEventsBefore(cutoffTime);
    }

    /**
     * 将实体转换为 DTO
     */
    private ExceptionEventDTO convertToDTO(ExceptionEventEntity entity) {
        return ExceptionEventDTO.builder()
                .eventId(entity.getEventId())
                .exceptionType(entity.getExceptionType())
                .exceptionMessage(entity.getExceptionMessage())
                .sanitizedMessage(entity.getSanitizedMessage())
                .operation(entity.getOperation())
                .errorCode(entity.getErrorCode())
                .errorCategory(entity.getErrorCategory())
                .httpStatus(entity.getHttpStatus())
                .traceId(entity.getTraceId())
                .userId(entity.getUserId())
                .apiKeyId(entity.getApiKeyId())
                .clientIp(entity.getClientIp())
                .serviceName(entity.getServiceName())
                .serviceType(entity.getServiceType())
                .modelName(entity.getModelName())
                .provider(entity.getProvider())
                .instanceName(entity.getInstanceName())
                .responseTimeMs(entity.getResponseTimeMs())
                .occurrenceCount(entity.getOccurrenceCount())
                .firstOccurrence(entity.getFirstOccurrence())
                .lastOccurrence(entity.getLastOccurrence())
                .occurredAt(entity.getOccurredAt())
                .isAggregated(entity.getIsAggregated())
                .build();
    }

    /**
     * 从 Map 中安全获取 Long 值
     */
    private Long getLongValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}
