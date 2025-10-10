package org.unreal.modelrouter.security.service;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 数据同步服务接口
 * 提供Redis缓存和StoreManager之间的数据同步和恢复功能
 */
public interface DataSyncService {
    
    /**
     * 从StoreManager恢复数据到Redis
     * @return 恢复操作结果
     */
    Mono<SyncResult> recoverFromStoreManagerToRedis();
    
    /**
     * 从Redis同步数据到StoreManager
     * @return 同步操作结果
     */
    Mono<SyncResult> syncFromRedisToStoreManager();
    
    /**
     * 执行双向数据同步
     * @return 同步操作结果
     */
    Mono<SyncResult> performBidirectionalSync();
    
    /**
     * 检查数据一致性
     * @return 一致性检查结果
     */
    Mono<ConsistencyCheckResult> checkDataConsistency();
    
    /**
     * 修复数据不一致问题
     * @param checkResult 一致性检查结果
     * @return 修复操作结果
     */
    Mono<SyncResult> repairDataInconsistency(ConsistencyCheckResult checkResult);
    
    /**
     * 获取同步统计信息
     * @return 同步统计信息
     */
    Mono<Map<String, Object>> getSyncStats();
    
    /**
     * 启动时自动恢复数据
     * @return 恢复操作结果
     */
    Mono<SyncResult> performStartupRecovery();
    
    /**
     * 清理同步过程中的临时数据
     * @return 清理操作结果
     */
    Mono<Void> cleanupSyncData();
    
    /**
     * 同步结果类
     */
    class SyncResult {
        private final boolean success;
        private final long processedCount;
        private final long successCount;
        private final long failureCount;
        private final String message;
        private final long durationMs;
        
        public SyncResult(boolean success, long processedCount, long successCount, 
                         long failureCount, String message, long durationMs) {
            this.success = success;
            this.processedCount = processedCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.message = message;
            this.durationMs = durationMs;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public long getProcessedCount() { return processedCount; }
        public long getSuccessCount() { return successCount; }
        public long getFailureCount() { return failureCount; }
        public String getMessage() { return message; }
        public long getDurationMs() { return durationMs; }
        
        @Override
        public String toString() {
            return String.format("SyncResult{success=%s, processed=%d, success=%d, failure=%d, duration=%dms, message='%s'}", 
                success, processedCount, successCount, failureCount, durationMs, message);
        }
    }
    
    /**
     * 一致性检查结果类
     */
    class ConsistencyCheckResult {
        private final boolean consistent;
        private final long redisCount;
        private final long storeManagerCount;
        private final long missingInRedis;
        private final long missingInStoreManager;
        private final long conflictCount;
        private final String details;
        
        public ConsistencyCheckResult(boolean consistent, long redisCount, long storeManagerCount,
                                    long missingInRedis, long missingInStoreManager, 
                                    long conflictCount, String details) {
            this.consistent = consistent;
            this.redisCount = redisCount;
            this.storeManagerCount = storeManagerCount;
            this.missingInRedis = missingInRedis;
            this.missingInStoreManager = missingInStoreManager;
            this.conflictCount = conflictCount;
            this.details = details;
        }
        
        // Getters
        public boolean isConsistent() { return consistent; }
        public long getRedisCount() { return redisCount; }
        public long getStoreManagerCount() { return storeManagerCount; }
        public long getMissingInRedis() { return missingInRedis; }
        public long getMissingInStoreManager() { return missingInStoreManager; }
        public long getConflictCount() { return conflictCount; }
        public String getDetails() { return details; }
        
        @Override
        public String toString() {
            return String.format("ConsistencyCheckResult{consistent=%s, redis=%d, storeManager=%d, missingInRedis=%d, missingInStoreManager=%d, conflicts=%d, details='%s'}", 
                consistent, redisCount, storeManagerCount, missingInRedis, missingInStoreManager, conflictCount, details);
        }
    }
}