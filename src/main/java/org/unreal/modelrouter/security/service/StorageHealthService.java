package org.unreal.modelrouter.security.service;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 存储系统健康检查服务接口
 * 提供对不同存储后端的健康状态监控和故障检测功能
 */
public interface StorageHealthService {
    
    /**
     * 检查Redis存储是否健康
     * @return Redis健康状态
     */
    Mono<Boolean> isRedisHealthy();
    
    /**
     * 检查StoreManager存储是否健康
     * @return StoreManager健康状态
     */
    Mono<Boolean> isStoreManagerHealthy();
    
    /**
     * 获取所有存储系统的健康状态
     * @return 存储系统健康状态映射
     */
    Mono<Map<String, Boolean>> getAllStorageHealth();
    
    /**
     * 获取存储系统的详细健康信息
     * @return 详细健康信息
     */
    Mono<Map<String, Object>> getDetailedHealthInfo();
    
    /**
     * 强制刷新健康状态缓存
     * @return 刷新操作结果
     */
    Mono<Void> refreshHealthStatus();
    
    /**
     * 检查是否应该降级到备用存储
     * @return 是否需要降级
     */
    Mono<Boolean> shouldFallbackToSecondary();
    
    /**
     * 获取当前推荐的主存储类型
     * @return 存储类型 ("redis", "storemanager")
     */
    Mono<String> getRecommendedPrimaryStorage();
    
    /**
     * 记录存储操作失败
     * @param storageType 存储类型
     * @param operation 操作类型
     * @param error 错误信息
     * @return 记录操作结果
     */
    Mono<Void> recordStorageFailure(String storageType, String operation, Throwable error);
    
    /**
     * 记录存储操作成功
     * @param storageType 存储类型
     * @param operation 操作类型
     * @return 记录操作结果
     */
    Mono<Void> recordStorageSuccess(String storageType, String operation);
    
    /**
     * 获取存储操作统计信息
     * @return 统计信息
     */
    Mono<Map<String, Object>> getStorageStats();
}