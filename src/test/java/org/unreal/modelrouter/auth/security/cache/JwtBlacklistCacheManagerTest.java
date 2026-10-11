package org.unreal.modelrouter.auth.security.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.unreal.modelrouter.auth.security.service.JwtBlacklistService;
import org.unreal.modelrouter.common.dto.TokenBlacklistEntry;
import reactor.core.publisher.Mono;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JwtBlacklistCacheManager 本地缓存同步测试（issue #258）.
 *
 * <p>覆盖同步任务重构后的三步语义：本地过期清理（不访问 Redis）→ 批量核对 Redis（锁外）
 * → 按核对结果删除本地条目。失败语义：核对异常或返回数量不匹配时，不删除任何条目、
 * 不推进同步水位（下个周期重试）。</p>
 *
 * <p>「网络 IO 已移出缓存锁之外」是结构性改动，由代码本身保证（角色在锁内、网络调用在
 * 锁外、删除在锁内），不在此处断言。</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("JwtBlacklistCacheManager 本地缓存同步（issue #258）")
class JwtBlacklistCacheManagerTest {

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private JwtBlacklistService blacklistService;

    private JwtBlacklistCacheManager manager;

    private Map<String, TokenBlacklistEntry> localCache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        manager = new JwtBlacklistCacheManager(redisTemplate, blacklistService);
        localCache = (Map<String, TokenBlacklistEntry>) readField(manager, "localCache");
        // 让 5 分钟同步间隔门禁放行
        writeField(manager, "lastCacheSync", 0L);
    }

    @Test
    @DisplayName("Redis 中已不存在的条目被清出本地缓存，仍存在的保留")
    void syncEvictsOnlyMissingKeys() {
        localCache.put("hash-a", liveEntry("hash-a"));
        localCache.put("hash-b", liveEntry("hash-b"));

        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return Mono.just(key.endsWith("hash-a"));
        });

        manager.syncLocalCacheWithRedis();

        assertTrue(localCache.containsKey("hash-a"), "Redis 中仍存在的条目应保留");
        assertFalse(localCache.containsKey("hash-b"), "Redis 中已消失的条目应被清出");
        verify(redisTemplate, times(2)).hasKey(anyString());
    }

    @Test
    @DisplayName("批量核对失败时不删除任何条目，也不推进同步水位")
    void syncRetainsEntriesWhenRedisFails() throws Exception {
        localCache.put("hash-a", liveEntry("hash-a"));
        localCache.put("hash-b", liveEntry("hash-b"));

        when(redisTemplate.hasKey(anyString()))
                .thenReturn(Mono.error(new IllegalStateException("redis down")));

        manager.syncLocalCacheWithRedis();

        assertTrue(localCache.containsKey("hash-a"), "Redis 异常时应保留本地条目");
        assertTrue(localCache.containsKey("hash-b"), "Redis 异常时应保留本地条目");
        assertEquals(0L, ((Number) readField(manager, "lastCacheSync")).longValue(),
                "核对失败不应推进同步水位，下个周期应重试");
    }

    @Test
    @DisplayName("未到同步间隔时直接返回，不访问 Redis")
    void syncSkippedWithinInterval() throws Exception {
        localCache.put("hash-a", liveEntry("hash-a"));
        writeField(manager, "lastCacheSync", System.currentTimeMillis());

        manager.syncLocalCacheWithRedis();

        verify(redisTemplate, never()).hasKey(anyString());
        assertTrue(localCache.containsKey("hash-a"));
    }

    @Test
    @DisplayName("本地已过期条目直接清理，且不为它访问 Redis")
    void syncDropsExpiredEntriesWithoutRedisRoundTrip() {
        localCache.put("hash-expired", expiredEntry("hash-expired"));

        manager.syncLocalCacheWithRedis();

        assertFalse(localCache.containsKey("hash-expired"), "过期条目应在本地清理");
        verify(redisTemplate, never()).hasKey(anyString());
    }

    private static TokenBlacklistEntry liveEntry(String tokenHash) {
        return entry(tokenHash, LocalDateTime.now().plusHours(1));
    }

    private static TokenBlacklistEntry expiredEntry(String tokenHash) {
        return entry(tokenHash, LocalDateTime.now().minusMinutes(1));
    }

    private static TokenBlacklistEntry entry(String tokenHash, LocalDateTime expiresAt) {
        TokenBlacklistEntry entry = new TokenBlacklistEntry();
        entry.setTokenHash(tokenHash);
        entry.setReason("test");
        entry.setAddedBy("test");
        entry.setAddedAt(LocalDateTime.now());
        entry.setExpiresAt(expiresAt);
        return entry;
    }

    private static Object readField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void writeField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
