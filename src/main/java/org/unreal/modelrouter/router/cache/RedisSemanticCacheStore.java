package org.unreal.modelrouter.router.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.unreal.modelrouter.monitor.callhistory.crypto.RecordContentCipher;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语义缓存的 Redis 共享后端（issue #1002）。
 *
 * <p>多副本部署时开启 {@code jairouter.semantic-cache.redis.enabled}，L2/L3 的候选集存入
 * Redis，命中率不再按副本数摊薄。判定（SimHash 相似度）仍在 {@link SemanticCacheService}
 * 的内存中进行 —— 候选集经 {@link #candidates} 拉回后逐条比对。</p>
 *
 * <p><b>键面</b>：{@code jairouter:semantic-cache:<scope>}（hash，field=条目 UUID，value=条目 JSON，
 * 可选经 {@link RecordContentCipher} 密文层）。<b>禁用 KEYS</b>：按租户清除与全清走
 * {@code SCAN}（每批 {@code scanCount} 条）。</p>
 *
 * <p><b>线程模型</b>（照 {@code RedisSlidingWindowRateLimiter} 的既有模式）：接口是同步签名，
 * 内部以 {@code blockFirst(timeout)} 桥接 Reactive 模板，调用链须在
 * {@code Schedulers.boundedElastic()} 上。</p>
 *
 * <p><b>失败语义</b>：任何 Redis 异常都降级不阻断 —— 查找按未命中、写入按丢弃、清除按空操作，
 * 并打 warn 日志。</p>
 *
 * @since v3.8.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "jairouter.semantic-cache.redis.enabled", havingValue = "true")
public final class RedisSemanticCacheStore implements SemanticCacheStore {

    private static final String KEY_PREFIX = "jairouter:semantic-cache:";

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final SemanticCacheProperties properties;
    private final RecordContentCipher cipher;


    public RedisSemanticCacheStore(
            final ReactiveRedisTemplate<String, String> redisTemplate,
            final ObjectMapper objectMapper,
            final SemanticCacheProperties properties,
            @Autowired(required = false) final RecordContentCipher cipher) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.cipher = cipher != null ? cipher : new RecordContentCipher();
    }

    @Override
    public List<SemanticCacheEntry> candidates(final String scope) {
        try {
            final List<Map.Entry<Object, Object>> raw = redisTemplate.opsForHash()
                    .entries(buildKey(scope))
                    .collectList()
                    .block(timeout());
            if (raw == null || raw.isEmpty()) {
                return List.of();
            }
            final long now = System.currentTimeMillis();
            final List<SemanticCacheEntry> result = new ArrayList<>(raw.size());
            for (final Map.Entry<Object, Object> item : raw) {
                final SemanticCacheEntry entry = decode(String.valueOf(item.getValue()));
                if (entry != null && entry.expireAt() > now) {
                    result.add(entry);
                }
            }
            result.sort(Comparator.comparingLong(SemanticCacheEntry::expireAt));
            return result;
        } catch (RuntimeException ex) {
            log.warn("语义缓存 Redis 候选拉取失败，按未命中处理: {}", ex.toString());
            return List.of();
        }
    }

    @Override
    public void put(final String scope, final SemanticCacheEntry entry) {
        try {
            final String key = buildKey(scope);
            final String field = UUID.randomUUID().toString();
            redisTemplate.opsForHash().put(key, field, encode(entry)).block(timeout());
            // 整键 TTL 兜底（条目级过期仍以 expireAt 为准）
            redisTemplate.expire(key, ttlSeconds()).block(timeout());
            evictOverCapacity(scope, key);
        } catch (RuntimeException ex) {
            log.warn("语义缓存 Redis 写入失败，丢弃本条: {}", ex.toString());
        }
    }

    @Override
    public int evict(final String scope) {
        try {
            if (scope == null) {
                int removed = 0;
                for (final String key : scanKeys(KEY_PREFIX + "*")) {
                    removed += countAndDelete(key);
                }
                return removed;
            }
            final String key = buildKey(scope);
            return countAndDelete(key);
        } catch (RuntimeException ex) {
            log.warn("语义缓存 Redis 清除失败，按空操作处理: {}", ex.toString());
            return 0;
        }
    }

    @Override
    public int evictTenant(final String apiKeyId) {
        try {
            int removed = 0;
            // SCAN 而非 KEYS（issue #1002 明确禁止 KEYS）
            final String pattern = KEY_PREFIX + SemanticCacheScope.TENANT_PREFIX + apiKeyId + "|*";
            for (final String key : scanKeys(pattern)) {
                removed += countAndDelete(key);
            }
            return removed;
        } catch (RuntimeException ex) {
            log.warn("语义缓存 Redis 租户清除失败，按空操作处理: {}", ex.toString());
            return 0;
        }
    }

    @Override
    public int size() {
        try {
            int total = 0;
            for (final String key : scanKeys(KEY_PREFIX + "*")) {
                final Long len = redisTemplate.opsForHash().size(key).block(timeout());
                if (len != null) {
                    total += len.intValue(); // 显式窄化，避免复合赋值隐式截断（CodeQL 告警）
                }
            }
            return total;
        } catch (RuntimeException ex) {
            log.warn("语义缓存 Redis 计数失败: {}", ex.toString());
            return 0;
        }
    }

    private void evictOverCapacity(final String scope, final String key) {
        final int limit = Math.max(1, properties.getMaxEntriesPerModel());
        final List<Map.Entry<Object, Object>> raw = redisTemplate.opsForHash().entries(key)
                .collectList().block(timeout());
        if (raw == null || raw.size() <= limit) {
            return;
        }
        // 按条目自身 expireAt 升序淘汰最旧（expireAt=写入时刻+固定 TTL ⇒ 越小越旧；
        // 判据在条目自身、跨进程一致，不依赖任何进程内状态），相同时按 field 字典序稳定定序
        final List<Map.Entry<Object, Object>> sorted = new ArrayList<>(raw);
        sorted.sort((a, b) -> {
            final long ta = expireOf(a.getValue());
            final long tb = expireOf(b.getValue());
            final int byTime = Long.compare(ta, tb);
            return byTime != 0 ? byTime : String.valueOf(a.getKey()).compareTo(String.valueOf(b.getKey()));
        });
        int overflow = raw.size() - limit;
        final List<Object> fields = new ArrayList<>(overflow);
        for (int i = 0; i < overflow; i++) {
            fields.add(sorted.get(i).getKey());
        }
        redisTemplate.opsForHash().remove(key, fields.toArray()).block(timeout());
    }

    /** 解出条目的 expireAt 供淘汰排序；解不出按 0（最旧，优先淘汰）。 */
    private long expireOf(final Object encoded) {
        try {
            final SemanticCacheEntry entry = decode(String.valueOf(encoded));
            return entry == null ? 0L : entry.expireAt();
        } catch (RuntimeException ex) {
            return 0L;
        }
    }

    private int countAndDelete(final String key) {
        final Long len = redisTemplate.opsForHash().size(key).block(timeout());
        redisTemplate.delete(key).block(timeout());
        return len == null ? 0 : Math.toIntExact(Math.min(len, Integer.MAX_VALUE));
    }

    private List<String> scanKeys(final String pattern) {
        // 禁用 KEYS：ReactiveRedisTemplate 的 scan（cursor 批量）
        final int count = Math.max(1, properties.getRedis().getScanCount());
        final List<String> keys = new ArrayList<>();
        redisTemplate.scan(
                        org.springframework.data.redis.core.ScanOptions.scanOptions()
                                .match(pattern).count(count).build())
                .doOnNext(keys::add)
                .blockLast(timeout());
        return keys;
    }

    private String encode(final SemanticCacheEntry entry) {
        final String json = objectMapper.writeValueAsString(toDto(entry));
        try {
            return cipher.encrypt(json);
        } catch (RuntimeException ex) {
            // 密钥不可用时降级明文（decode 侧兼容两种形态），不丢条目
            log.warn("语义缓存密文层不可用，条目以明文写入: {}", ex.toString());
            return json;
        }
    }

    private SemanticCacheEntry decode(final String raw) {
        try {
            String json = raw;
            if (cipher != null) {
                try {
                    json = cipher.decrypt(raw);
                } catch (RuntimeException notCiphertext) {
                    // 兼容明文形态（密文层未启用时写入的条目）
                    json = raw;
                }
            }
            final EntryDto dto = objectMapper.readValue(json, EntryDto.class);
            final Object response = "c".equals(dto.rt())
                    ? objectMapper.readValue(dto.r(), CachedStreamingResponse.class)
                    : dto.r();
            return new SemanticCacheEntry(dto.f(), dto.t(), response, dto.e());
        } catch (RuntimeException ex) {
            log.warn("语义缓存条目解码失败，跳过该条: {}", ex.toString());
            return null;
        }
    }

    private Map<String, Object> toDto(final SemanticCacheEntry entry) {
        final boolean streaming = entry.response() instanceof CachedStreamingResponse;
        final String responseBody = streaming
                ? objectMapper.writeValueAsString(entry.response())
                : String.valueOf(entry.response());
        return Map.of(
                "f", entry.fingerprint(),
                "t", entry.tokens(),
                "e", entry.expireAt(),
                "rt", streaming ? "c" : "s",
                "r", responseBody);
    }

    private String buildKey(final String scope) {
        return KEY_PREFIX + scope;
    }

    private Duration timeout() {
        final Duration configured = properties.getRedis().getTimeout();
        return configured == null || configured.isNegative() || configured.isZero()
                ? Duration.ofMillis(200) : configured;
    }

    private Duration ttlSeconds() {
        final Duration ttl = properties.getTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(10) : ttl;
    }

    /** 条目 JSON 的中间形状（f/t/e=指纹/token 数/过期时刻，rt=响应类型，r=响应体）。 */
    record EntryDto(long f, int t, long e, String rt, String r) {
    }
}
