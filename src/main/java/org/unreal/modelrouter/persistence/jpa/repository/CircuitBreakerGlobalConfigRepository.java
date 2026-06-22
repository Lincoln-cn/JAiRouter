package org.unreal.modelrouter.persistence.jpa.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.unreal.modelrouter.persistence.jpa.entity.CircuitBreakerGlobalConfigEntity;

/**
 * 熔断器全局配置仓库
 * 
 * v2.6.13: 新增
 */
@Repository
public interface CircuitBreakerGlobalConfigRepository extends JpaRepository<CircuitBreakerGlobalConfigEntity, Long> {

    /**
     * 获取全局配置（单例）
     * 如果不存在，返回默认配置
     * 
     * @return 全局配置
     */
    default CircuitBreakerGlobalConfigEntity getOrCreate() {
        return findById(1L).orElseGet(() -> {
            CircuitBreakerGlobalConfigEntity config = CircuitBreakerGlobalConfigEntity.builder()
                .id(1L)
                .adaptiveThresholdEnabled(false)
                .stateSyncIntervalMinutes(5)
                .cleanupIntervalMinutes(30)
                .historyRetentionDays(30)
                .defaultFailureThreshold(5)
                .defaultSuccessThreshold(2)
                .defaultTimeoutMs(60000L)
                .build();
            return save(config);
        });
    }
}
