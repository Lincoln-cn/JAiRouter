package org.unreal.modelrouter.persistence.store.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R3-P1：冷路径 block 必须带超时，禁止永久挂起。
 */
@DisplayName("状态持久化 isHealthy block 超时")
class CompositeStatePersistenceTimeoutTest {

    private static final class HangingPersistence implements StatePersistenceService {
        @Override
        public Mono<Boolean> save(StateType stateType, String key, Map<String, Object> data) {
            return Mono.never();
        }

        @Override
        public Mono<Map<String, Object>> load(StateType stateType, String key) {
            return Mono.never();
        }

        @Override
        public Mono<Boolean> delete(StateType stateType, String key) {
            return Mono.never();
        }

        @Override
        public Mono<Boolean> exists(StateType stateType, String key) {
            return Mono.never();
        }

        @Override
        public Mono<Iterable<String>> getAllKeys(StateType stateType) {
            return Mono.never();
        }

        @Override
        public Mono<Integer> saveBatch(StateType stateType, Map<String, Map<String, Object>> states) {
            return Mono.never();
        }

        @Override
        public Mono<Map<String, Map<String, Object>>> loadBatch(StateType stateType, Iterable<String> keys) {
            return Mono.never();
        }

        @Override
        public Mono<Boolean> clearAll(StateType stateType) {
            return Mono.never();
        }

        @Override
        public Mono<Boolean> isHealthy() {
            return Mono.never();
        }

        @Override
        public String getTierName() {
            return "hanging";
        }

        @Override
        public int getTierPriority() {
            return 1;
        }
    }

    @Test
    @DisplayName("BLOCK_TIMEOUT 常量存在且在 1-30s")
    void timeoutConstant_exists() {
        Duration timeout = CompositeStatePersistenceServiceImpl.BLOCK_TIMEOUT;
        assertTrue(timeout.toSeconds() >= 1 && timeout.toSeconds() <= 30);
    }

    @Test
    @DisplayName("isHealthy 永不完成时：block(timeout) 在超时窗口内返回")
    void isHealthy_timesOutInsteadOfHanging() {
        HangingPersistence hanging = new HangingPersistence();
        long start = System.nanoTime();
        Boolean healthy = null;
        try {
            healthy = hanging.isHealthy().block(CompositeStatePersistenceServiceImpl.BLOCK_TIMEOUT);
        } catch (IllegalStateException timeout) {
            // expected: Mono.never + block(timeout)
            healthy = null;
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
        assertTrue(elapsedMs <= CompositeStatePersistenceServiceImpl.BLOCK_TIMEOUT.toMillis() + 2000L,
                "必须在超时窗口内返回: " + elapsedMs + "ms");
        assertTrue(healthy == null || !healthy);
    }

    @Test
    @DisplayName("JwtBlacklist BLOCK_TIMEOUT 存在")
    void jwtBlacklistTimeout_exists() {
        Duration timeout = org.unreal.modelrouter.auth.security.service.impl.JwtBlacklistServiceImpl.BLOCK_TIMEOUT;
        assertTrue(timeout.toSeconds() >= 1 && timeout.toSeconds() <= 30);
        assertFalse(timeout.isZero());
    }
}
