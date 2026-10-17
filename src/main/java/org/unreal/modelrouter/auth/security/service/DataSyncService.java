package org.unreal.modelrouter.auth.security.service;

import reactor.core.publisher.Mono;

/**
 * 数据恢复服务接口
 *
 * <p>只负责一个方向：<b>StoreManager（持久层）→ Redis（缓存）</b>。
 * Redis 侧的数据是持久层的派生副本，进程重启/换副本后从持久层预热即可，因此这里没有
 * 「Redis → StoreManager」的反向同步，也没有一致性检查与修复。</p>
 *
 * <p><b>为什么不做反向同步</b>：三种 JWT 持久化实现都会把写入落到 StoreManager
 * （{@code JwtTokenPersistenceServiceImpl} 只写 StoreManager；{@code RedisJwtTokenPersistenceServiceImpl}
 * 在 Redis 失败时回退写入；{@code CompositeJwtPersistenceServiceImpl} 在 Redis 健康时两边都写）。
 * 需要「两边都写」的场景由 {@code composite.enabled=true} 覆盖。而一个不带删除墓碑的
 * Redis → StoreManager 同步会把<b>已吊销但仍留在 StoreManager 里的令牌复活</b>，属安全风险，
 * 故整个反向同步 + 一致性修复链路已移除（见 issue #456 的决策记录）。</p>
 */
public interface DataSyncService {

    /**
     * 从StoreManager恢复数据到Redis
     * @return 恢复操作结果
     */
    Mono<SyncResult> recoverFromStoreManagerToRedis();

    /**
     * 启动时自动恢复数据
     * @return 恢复操作结果
     */
    Mono<SyncResult> performStartupRecovery();

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

        public SyncResult(final boolean success, final long processedCount, final long successCount,
                         final long failureCount, final String message, final long durationMs) {
            this.success = success;
            this.processedCount = processedCount;
            this.successCount = successCount;
            this.failureCount = failureCount;
            this.message = message;
            this.durationMs = durationMs;
        }

        // Getters
        public boolean isSuccess() {
            return success;
        }

        public long getProcessedCount() {
            return processedCount;
        }

        public long getSuccessCount() {
            return successCount;
        }

        public long getFailureCount() {
            return failureCount;
        }

        public String getMessage() {
            return message;
        }

        public long getDurationMs() {
            return durationMs;
        }

        @Override
        public String toString() {
            return String.format("SyncResult{success=%s, processed=%d, "
                            + "success=%d, failure=%d, duration=%dms, message='%s'}",
                success, processedCount, successCount, failureCount,
                durationMs, message);
        }
    }
}
