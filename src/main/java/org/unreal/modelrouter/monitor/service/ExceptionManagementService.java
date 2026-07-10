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
import org.unreal.modelrouter.monitor.monitoring.error.ExceptionPersistenceService;

import java.time.LocalDateTime;
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
     * 查询异常事件列表
     * 
     * @param request 查询请求
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public PagedResult<ExceptionEventDTO> queryExceptionEvents(ExceptionQueryRequest request) {
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
        Page<ExceptionEventEntity> entityPage = exceptionEventRepository.findByConditions(
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
                request.getModelName(),
                pageRequest
        );

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
    public ExceptionEventDTO getExceptionEventById(String eventId) {
        return exceptionEventRepository.findByEventId(eventId)
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
    public ExceptionStatisticsDTO getExceptionStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        // 设置默认时间范围
        LocalDateTime effectiveStartTime = startTime != null ? startTime : LocalDateTime.now().minusDays(7);
        LocalDateTime effectiveEndTime = endTime != null ? endTime : LocalDateTime.now();

        // 使用 PersistenceService 获取统计
        Map<String, Object> stats = exceptionPersistenceService.getExceptionStatistics(effectiveStartTime, effectiveEndTime);
        
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
        List<Object[]> byOperation = exceptionEventRepository.countByOperation(effectiveStartTime, effectiveEndTime, 10);
        Map<String, Long> operationStats = new HashMap<>();
        byOperation.forEach(row -> operationStats.put((String) row[0], (Long) row[1]));
        dto.setByOperation(operationStats);

        // 查询按 HTTP 状态统计
        List<Object[]> byHttpStatus = exceptionEventRepository.countByHttpStatus(effectiveStartTime, effectiveEndTime);
        Map<String, Long> httpStatusStats = new HashMap<>();
        byHttpStatus.forEach(row -> httpStatusStats.put((String) row[0], (Long) row[1]));
        dto.setByHttpStatus(httpStatusStats);

        // 查询 Top 客户端 IP
        List<Object[]> topClientIps = exceptionEventRepository.countByClientIp(effectiveStartTime, effectiveEndTime, 10);
        List<ExceptionStatisticsDTO.ClientIpStats> clientIpStats = new ArrayList<>();
        topClientIps.forEach(row -> clientIpStats.add(new ExceptionStatisticsDTO.ClientIpStats((String) row[0], (Long) row[1])));
        dto.setTopClientIps(clientIpStats);

        // 查询小时分布
        List<Object[]> hourlyStats = exceptionEventRepository.countByHour(effectiveStartTime, effectiveEndTime);
        List<ExceptionStatisticsDTO.HourlyStats> hourlyDistribution = new ArrayList<>();
        hourlyStats.forEach(row -> hourlyDistribution.add(new ExceptionStatisticsDTO.HourlyStats(String.valueOf(row[0]), (Long) row[1])));
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
    public List<ExceptionEventDTO> getRecentExceptionEvents(int limit) {
        return exceptionEventRepository.findRecentEvents(Math.min(limit, 100))
                .stream()
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
    public List<ExceptionEventDTO> getRecentExceptionEventsByType(String exceptionType, int limit) {
        return exceptionEventRepository.findRecentEventsByType(exceptionType, Math.min(limit, 100))
                .stream()
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
