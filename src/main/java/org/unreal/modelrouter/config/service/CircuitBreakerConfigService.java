package org.unreal.modelrouter.config.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.unreal.modelrouter.common.dto.CircuitBreakerGlobalConfigDTO;
import org.unreal.modelrouter.persistence.jpa.entity.CircuitBreakerGlobalConfigEntity;
import org.unreal.modelrouter.persistence.jpa.entity.CircuitBreakerStateHistoryEntity;
import org.unreal.modelrouter.persistence.jpa.repository.CircuitBreakerGlobalConfigRepository;
import org.unreal.modelrouter.persistence.jpa.repository.CircuitBreakerStateHistoryRepository;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreaker;
import org.unreal.modelrouter.router.circuitbreaker.CircuitBreakerManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 熔断器配置服务
 * 
 * 提供全局配置和历史记录管理
 * 
 * v2.6.13: 新增
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerConfigService {

    private final CircuitBreakerGlobalConfigRepository globalConfigRepository;
    private final CircuitBreakerStateHistoryRepository historyRepository;
    private final CircuitBreakerManager circuitBreakerManager;

    // ==================== 全局配置管理 ====================

    /**
     * 获取全局配置
     */
    public CircuitBreakerGlobalConfigDTO getGlobalConfig() {
        CircuitBreakerGlobalConfigEntity entity = globalConfigRepository.getOrCreate();
        return toDTO(entity);
    }

    /**
     * 保存全局配置
     */
    @Transactional
    public CircuitBreakerGlobalConfigDTO saveGlobalConfig(final CircuitBreakerGlobalConfigDTO config) {
        CircuitBreakerGlobalConfigEntity entity = globalConfigRepository.getOrCreate();
        
        // 更新配置
        if (config.getAdaptiveThresholdEnabled() != null) {
            entity.setAdaptiveThresholdEnabled(config.getAdaptiveThresholdEnabled());
        }
        if (config.getStateSyncIntervalMinutes() != null) {
            entity.setStateSyncIntervalMinutes(config.getStateSyncIntervalMinutes());
        }
        if (config.getCleanupIntervalMinutes() != null) {
            entity.setCleanupIntervalMinutes(config.getCleanupIntervalMinutes());
        }
        if (config.getHistoryRetentionDays() != null) {
            entity.setHistoryRetentionDays(config.getHistoryRetentionDays());
        }
        if (config.getDefaultFailureThreshold() != null) {
            entity.setDefaultFailureThreshold(config.getDefaultFailureThreshold());
        }
        if (config.getDefaultSuccessThreshold() != null) {
            entity.setDefaultSuccessThreshold(config.getDefaultSuccessThreshold());
        }
        if (config.getDefaultTimeoutMs() != null) {
            entity.setDefaultTimeoutMs(config.getDefaultTimeoutMs());
        }
        
        CircuitBreakerGlobalConfigEntity saved = globalConfigRepository.save(entity);
        log.info("Circuit breaker global config saved: id={}", saved.getId());
        
        return toDTO(saved);
    }

    /**
     * 重置全局配置为默认值
     */
    @Transactional
    public CircuitBreakerGlobalConfigDTO resetGlobalConfig() {
        CircuitBreakerGlobalConfigEntity entity = CircuitBreakerGlobalConfigEntity.builder()
                .id(1L)
                .adaptiveThresholdEnabled(false)
                .stateSyncIntervalMinutes(5)
                .cleanupIntervalMinutes(30)
                .historyRetentionDays(30)
                .defaultFailureThreshold(5)
                .defaultSuccessThreshold(2)
                .defaultTimeoutMs(60000L)
                .build();
        
        CircuitBreakerGlobalConfigEntity saved = globalConfigRepository.save(entity);
        log.info("Circuit breaker global config reset to defaults");
        
        return toDTO(saved);
    }

    // ==================== 历史记录管理 ====================

    /**
     * 记录熔断器状态变化
     */
    @Transactional
    public void recordStateChange(
            final String instanceId,
            final String instanceName,
            final String serviceType,
            final CircuitBreaker.State previousState,
            final CircuitBreaker.State currentState,
            final String triggerReason,
            final Integer failureCount,
            final Integer successCount) {
        
        CircuitBreakerStateHistoryEntity entity = CircuitBreakerStateHistoryEntity.builder()
                .instanceId(instanceId)
                .instanceName(instanceName)
                .serviceType(serviceType)
                .previousState(previousState)
                .currentState(currentState)
                .triggerReason(triggerReason)
                .failureCount(failureCount)
                .successCount(successCount)
                .build();
        
        historyRepository.save(entity);
        log.debug("Recorded circuit breaker state change: instance={}, {} -> {}, reason={}",
                instanceId, previousState, currentState, triggerReason);
    }

    /**
     * 获取历史记录（分页）
     */
    public Page<CircuitBreakerStateHistoryEntity> getHistory(final PageRequest pageRequest) {
        return historyRepository.findAllByOrderByChangedAtDesc(pageRequest);
    }

    /**
     * 按实例 ID 获取历史记录
     */
    public List<CircuitBreakerStateHistoryEntity> getHistoryByInstance(
            final String instanceId, final int limit) {
        return historyRepository.findTop100ByInstanceIdOrderByChangedAtDesc(instanceId);
    }

    /**
     * 按服务类型获取历史记录
     */
    public Page<CircuitBreakerStateHistoryEntity> getHistoryByServiceType(
            final String serviceType, final PageRequest pageRequest) {
        return historyRepository.findByServiceTypeOrderByChangedAtDesc(serviceType, pageRequest);
    }

    /**
     * 按时间范围获取历史记录
     */
    public List<CircuitBreakerStateHistoryEntity> getHistoryByTimeRange(
            final LocalDateTime startTime, final LocalDateTime endTime) {
        return historyRepository.findByTimeRange(startTime, endTime);
    }

    /**
     * 清理过期历史记录
     */
    @Transactional
    public int cleanupExpiredHistory() {
        CircuitBreakerGlobalConfigEntity config = globalConfigRepository.getOrCreate();
        int retentionDays = config.getHistoryRetentionDays();
        
        LocalDateTime beforeTime = LocalDateTime.now().minusDays(retentionDays);
        int deleted = historyRepository.deleteByChangedAtBefore(beforeTime);
        
        if (deleted > 0) {
            log.info("Cleaned up {} expired circuit breaker history records (retention: {} days)", 
                    deleted, retentionDays);
        }
        
        return deleted;
    }

    /**
     * 获取历史记录统计信息
     */
    public CircuitBreakerHistoryStats getHistoryStats() {
        long totalCount = historyRepository.count();
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime todayStart = now.toLocalDate().atStartOfDay();
        LocalDateTime weekAgo = now.minusDays(7);
        LocalDateTime monthAgo = now.minusDays(30);
        
        List<CircuitBreakerStateHistoryEntity> todayRecords = 
                historyRepository.findByTimeRange(todayStart, now);
        List<CircuitBreakerStateHistoryEntity> weekRecords = 
                historyRepository.findByTimeRange(weekAgo, now);
        List<CircuitBreakerStateHistoryEntity> monthRecords = 
                historyRepository.findByTimeRange(monthAgo, now);
        
        return CircuitBreakerHistoryStats.builder()
                .totalCount(totalCount)
                .todayCount(todayRecords.size())
                .weekCount(weekRecords.size())
                .monthCount(monthRecords.size())
                .build();
    }

    // ==================== 辅助方法 ====================

    /**
     * 将实体转换为 DTO
     */
    private CircuitBreakerGlobalConfigDTO toDTO(final CircuitBreakerGlobalConfigEntity entity) {
        return CircuitBreakerGlobalConfigDTO.builder()
                .adaptiveThresholdEnabled(entity.getAdaptiveThresholdEnabled())
                .stateSyncIntervalMinutes(entity.getStateSyncIntervalMinutes())
                .cleanupIntervalMinutes(entity.getCleanupIntervalMinutes())
                .historyRetentionDays(entity.getHistoryRetentionDays())
                .defaultFailureThreshold(entity.getDefaultFailureThreshold())
                .defaultSuccessThreshold(entity.getDefaultSuccessThreshold())
                .defaultTimeoutMs(entity.getDefaultTimeoutMs())
                .build();
    }

    /**
     * 历史记录统计信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CircuitBreakerHistoryStats {
        private long totalCount;
        private long todayCount;
        private long weekCount;
        private long monthCount;
    }
}
