package org.unreal.modelrouter.router.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.unreal.modelrouter.monitor.callhistory.crypto.RecordContentCipher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 语义缓存 Redis 共享后端测试（issue #1002）。
 *
 * <p>用一个共享的内存 Map 模拟 Redis（两个 store 实例指向同一份），
 * 验证「两进程同 Redis：A 写 B 读命中」与「跨租户仍不命中」两条核心口径。
 * 真实两进程 + 真实 Redis 的端到端验证在部署侧做（PR 正文如实申报）。</p>
 */
@DisplayName("语义缓存 Redis 共享后端（#1002）")
class RedisSemanticCacheStoreTest {

    private static final String SCOPE_A = "t|tenantA|user1|qwen3:4b";
    private static final String SCOPE_B = "t|tenantB|user1|qwen3:4b";
    private static final String SCOPE_PUBLIC = "p|qwen3:4b";

    /** 模拟 Redis：key → hash（field → value）。 */
    private Map<String, Map<Object, Object>> redis;

    private SemanticCacheProperties properties;
    private RedisSemanticCacheStore storeA;
    private RedisSemanticCacheStore storeB;

    /** glob（MATCH）转正则：保护 `*`、转义其余正则元字符（`|`、`.` 等在 glob 里是普通字符）。 */
    private static String globToRegex(final String glob) {
        final String marker = "\u0000";
        final String escaped = glob.replace("*", marker)
                .replaceAll("([\\\\.\\[\\]{}()|^$+?])", "\\\\$1");
        return escaped.replace(marker, ".*");
    }

    @BeforeEach
    void setUp() {
        redis = new ConcurrentHashMap<>();
        properties = new SemanticCacheProperties();
        properties.getRedis().setTimeout(Duration.ofSeconds(2));

        // 统一用「默认答案分发」而不是逐方法 stub：varargs（remove(K, Object...)）在
        // 逐方法 stub 的参数匹配下容易漏配（返回 mock 默认 null、静默失效），默认答案形态没有这个坑。
        @SuppressWarnings("unchecked")
        final ReactiveHashOperations<String, Object, Object> hashOps =
                Mockito.mock(ReactiveHashOperations.class, (Answer<Object>) inv -> {
                    final String method = inv.getMethod().getName();
                    final Object[] args = inv.getArguments();
                    final String key = args[0] == null ? null : String.valueOf(args[0]);
                    return switch (method) {
                        case "entries" -> Flux.fromIterable(
                                new ArrayList<>(redis.getOrDefault(key, Map.of()).entrySet()));
                        case "put" -> Mono.fromRunnable(() -> redis
                                .computeIfAbsent(key, k -> new ConcurrentHashMap<>())
                                .put(args[1], args[2]));
                        case "size" -> Mono.just((long) redis.getOrDefault(key, Map.of()).size());
                        case "remove" -> {
                            final Object[] fields;
                            if (args.length == 2 && args[1] instanceof Object[] arr) {
                                fields = arr;
                            } else {
                                fields = new Object[args.length - 1];
                                System.arraycopy(args, 1, fields, 0, fields.length);
                            }
                            final Map<Object, Object> hash = redis.getOrDefault(key, Map.of());
                            long removed = 0;
                            for (final Object field : fields) {
                                if (hash.remove(field) != null) {
                                    removed++;
                                }
                            }
                            yield Mono.just(removed);
                        }
                        default -> throw new IllegalStateException("unexpected hash op: " + method);
                    };
                });

        @SuppressWarnings("unchecked")
        final ReactiveRedisTemplate<String, String> template =
                Mockito.mock(ReactiveRedisTemplate.class, (Answer<Object>) inv -> {
                    final String method = inv.getMethod().getName();
                    final Object[] args = inv.getArguments();
                    return switch (method) {
                        case "opsForHash" -> hashOps;
                        case "delete" -> Mono.just(redis.remove(String.valueOf(args[0])) != null);
                        case "expire" -> Mono.just(true);
                        case "scan" -> {
                            final ScanOptions options = args[0] == null ? null : (ScanOptions) args[0];
                            final String pattern = options == null ? null : options.getPattern();
                            if (pattern == null) {
                                yield Flux.fromIterable(List.copyOf(redis.keySet()));
                            }
                            final String regex = globToRegex(pattern);
                            yield Flux.fromIterable(redis.keySet().stream()
                                    .filter(k -> k.matches(regex))
                                    .toList());
                        }
                        default -> throw new IllegalStateException("unexpected template op: " + method);
                    };
                });

        final RecordContentCipher cipher = new RecordContentCipher(new SecretKeySpec(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES"));
        storeA = new RedisSemanticCacheStore(template, new tools.jackson.databind.ObjectMapper(), properties, cipher);
        storeB = new RedisSemanticCacheStore(template, new tools.jackson.databind.ObjectMapper(), properties, cipher);
    }

    private static SemanticCacheEntry entry(final String response) {
        return new SemanticCacheEntry(42L, 10, response, System.currentTimeMillis() + 60_000);
    }

    @Test
    @DisplayName("DSC-100 两进程同 Redis：A 写 B 读命中")
    void aWritesBReads() {
        storeA.put(SCOPE_A, entry("hello world"));

        final List<SemanticCacheEntry> candidates = storeB.candidates(SCOPE_A);
        assertEquals(1, candidates.size());
        assertEquals("hello world", candidates.get(0).response());
    }

    @Test
    @DisplayName("DSC-101 流式响应形态（CachedStreamingResponse）序列化往返")
    void streamingResponseRoundTrip() {
        final CachedStreamingResponse streaming = new CachedStreamingResponse(
                List.of("data: a", "data: b"), "qwen3:4b", 1L, 2L, 3L, "stop", true);
        storeA.put(SCOPE_A, new SemanticCacheEntry(7L, 5, streaming, System.currentTimeMillis() + 60_000));

        final List<SemanticCacheEntry> candidates = storeB.candidates(SCOPE_A);
        assertEquals(1, candidates.size());
        final Object response = candidates.get(0).response();
        assertNotNull(response);
        assertTrue(response instanceof CachedStreamingResponse, "应还原为 CachedStreamingResponse，实际: " + response.getClass());
        assertEquals(List.of("data: a", "data: b"), ((CachedStreamingResponse) response).chunks());
    }

    @Test
    @DisplayName("DSC-102 跨租户不命中（最重要：别破租户隔离）")
    void crossTenantIsolation() {
        storeA.put(SCOPE_A, entry("tenant A secret"));
        storeA.put(SCOPE_B, entry("tenant B secret"));
        storeA.put(SCOPE_PUBLIC, entry("public answer"));

        // B 的 scope 拉不到 A 的条目
        assertTrue(storeB.candidates(SCOPE_B).stream()
                .noneMatch(e -> "tenant A secret".equals(e.response())));

        // 清 tenantA 只删 A 的 L2，不动 tenantB 与公共池
        final int removed = storeB.evictTenant("tenantA");
        assertEquals(1, removed);
        assertTrue(storeA.candidates(SCOPE_A).isEmpty(), "tenantA 的条目应已清除");
        assertEquals(1, storeA.candidates(SCOPE_B).size(), "tenantB 不受波及");
        assertEquals(1, storeA.candidates(SCOPE_PUBLIC).size(), "公共池不受波及");
    }

    @Test
    @DisplayName("DSC-103 过期条目不进入候选")
    void expiredEntriesFiltered() {
        storeA.put(SCOPE_A, new SemanticCacheEntry(1L, 1, "old", System.currentTimeMillis() - 1));
        storeA.put(SCOPE_A, entry("fresh"));

        final List<SemanticCacheEntry> candidates = storeB.candidates(SCOPE_A);
        assertEquals(1, candidates.size());
        assertEquals("fresh", candidates.get(0).response());
    }

    @Test
    @DisplayName("DSC-104 每 scope 容量上限淘汰最旧")
    void capacityEviction() {
        properties.setMaxEntriesPerModel(2);
        // expireAt 递增造出确定的「最旧」序（写入时刻+固定 TTL ⇒ expireAt 越小越旧）
        final long t = System.currentTimeMillis() + 60_000;
        storeA.put(SCOPE_A, new SemanticCacheEntry(1L, 1, "first", t));
        storeA.put(SCOPE_A, new SemanticCacheEntry(2L, 1, "second", t + 1));
        storeA.put(SCOPE_A, new SemanticCacheEntry(3L, 1, "third", t + 2));

        final List<String> responses = storeB.candidates(SCOPE_A).stream()
                .map(e -> String.valueOf(e.response())).toList();
        assertEquals(2, responses.size());
        assertFalse(responses.contains("first"), "最旧的应被淘汰: " + responses);
    }

    @Test
    @DisplayName("DSC-105 后端异常降级不阻断：查按未命中、写按丢弃、清按空操作")
    void backendFailureDegrades() {
        final ReactiveRedisTemplate<String, String> brokenTemplate = Mockito.mock(
                ReactiveRedisTemplate.class, invocation -> {
                    throw new IllegalStateException("redis down");
                });
        final RedisSemanticCacheStore broken = new RedisSemanticCacheStore(
                brokenTemplate, new tools.jackson.databind.ObjectMapper(), properties, new RecordContentCipher(
                        new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES")));

        assertTrue(broken.candidates(SCOPE_A).isEmpty());
        broken.put(SCOPE_A, entry("x"));
        assertEquals(0, broken.evictTenant("tenantA"));
        assertEquals(0, broken.size());
    }

    @Test
    @DisplayName("DSC-106 语义缓存服务级互联：A 进程写、B 进程 find 命中")
    void serviceLevelSharedHit() {
        final SemanticCacheService serviceA = new SemanticCacheService(properties, null, storeA);
        final SemanticCacheService serviceB = new SemanticCacheService(properties, null, storeB);

        serviceA.put(SCOPE_A, "介绍一下北京的著名景点", "北京有故宫、天坛、颐和园……");

        final java.util.Optional<Object> hit = serviceB.find(SCOPE_A, "介绍一下北京的著名景点");
        assertTrue(hit.isPresent(), "B 进程应命中 A 进程写的语义缓存");
        assertEquals("北京有故宫、天坛、颐和园……", hit.get());
    }
}
