package org.unreal.modelrouter.router.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 语义响应缓存（近似请求复用，按 scope 隔离）.
 *
 * <p>与 {@link ResponseCacheService} 的分工：后者按<b>精确键</b>（租户 + 服务 + 模型 + 请求体
 * 的 SHA-256）命中，只复用「同一个请求」；本类在精确键之外提供一层<b>文本相似</b>命中，
 * 复用「措辞不同、意图相同」的请求。</p>
 *
 * <p><b>隔离由 scope 决定，不由模型名决定</b>（见 {@link SemanticCacheScope}）：L2 的桶是
 * {@code t|<apiKeyId>|<user>|<model>}，L3 是 {@code p|<model>}。早先的版本只按模型名分桶，
 * 导致不同 API Key 的相似提问互相命中 —— 那是真实的数据越权，本版已修。</p>
 *
 * <p><b>实现选择：SimHash 而非向量 embedding</b>。本仓已配置 embedding 模型，但走 embedding
 * 意味着每个请求多一次上游调用（延迟与成本翻倍），而语义缓存的收益本就来自「省掉一次上游调用」。
 * 因此这里用纯本地的 64 位 SimHash 指纹 + 汉明距离做近似判定：零外部调用、零额外依赖、结果可解释。
 * 代价是只能识别「词汇高度重叠」的近似，换述识别不到。</p>
 *
 * <p><b>失败语义 = 静默旁路</b>：本类的任何异常都不影响主链路（写入/查找失败按未命中处理）。</p>
 *
 * @author JAiRouter Team
 * @since 3.7.1
 */
@Service
public class SemanticCacheService {

    private static final Logger logger = LoggerFactory.getLogger(SemanticCacheService.class);

    /** 命中计数指标名。 */
    static final String METRIC_HITS = "jairouter.semantic.cache.hits";
    /** 未命中计数指标名。 */
    static final String METRIC_MISSES = "jairouter.semantic.cache.misses";
    /** 拒绝写入计数指标名。 */
    static final String METRIC_REJECTED = "jairouter.semantic.cache.rejected";

    /** SimHash 的位数。 */
    private static final int HASH_BITS = 64;

    private final SemanticCacheProperties properties;

    /** scope → 条目队列（新条目在尾部，队首最旧，便于淘汰）。 */
    private final ConcurrentHashMap<String, Deque<Entry>> buckets = new ConcurrentHashMap<>();

    private final AtomicInteger totalEntries = new AtomicInteger();

    private final Counter hits;
    private final Counter misses;
    private final Counter rejectedPii;

    /**
     * 构造器.
     *
     * @param properties    配置面
     * @param meterRegistry 指标注册表（可为 {@code null}）
     */
    public SemanticCacheService(
            final SemanticCacheProperties properties,
            @Autowired(required = false) final MeterRegistry meterRegistry) {
        this.properties = properties;
        this.hits = counter(meterRegistry, METRIC_HITS, "语义缓存命中次数");
        this.misses = counter(meterRegistry, METRIC_MISSES, "语义缓存未命中次数");
        this.rejectedPii = counter(meterRegistry, METRIC_REJECTED, "因含敏感内容而拒绝写入的次数");
    }

    /**
     * 语义缓存（L2）是否启用.
     *
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return properties.isEnabled() && properties.isTenantScopeEnabled();
    }

    /**
     * 查找一个足够相似的缓存条目.
     *
     * @param scope 缓存 scope（见 {@link SemanticCacheScope}）
     * @param text  用于比对的请求文本
     * @return 命中的缓存值；未启用、文本过短、无相似条目时返回 {@link Optional#empty()}
     */
    public Optional<Object> find(final String scope, final String text) {
        if (!isEnabled() || scope == null || scope.isBlank()) {
            return Optional.empty();
        }
        final String normalized = normalize(text);
        if (normalized.length() < Math.max(1, properties.getMinTextLength())) {
            return Optional.empty();
        }
        try {
            final long fingerprint = simHash(normalized);
            final int tokens = tokenCount(normalized);
            final long now = System.currentTimeMillis();
            final Deque<Entry> bucket = buckets.get(scope);
            if (bucket == null) {
                countMiss();
                return Optional.empty();
            }
            synchronized (bucket) {
                purgeExpired(bucket, now);
                for (final Entry entry : bucket) {
                    if (isSimilar(entry, fingerprint, tokens)) {
                        countHit();
                        return Optional.of(entry.response());
                    }
                }
            }
            countMiss();
            return Optional.empty();
        } catch (RuntimeException ex) {
            logger.warn("语义缓存查找失败，按未命中处理: {}", ex.toString());
            return Optional.empty();
        }
    }

    /**
     * 写入一个缓存条目.
     *
     * @param scope    scope
     * @param text     请求文本
     * @param response 待复用的响应值
     */
    public void put(final String scope, final String text, final Object response) {
        if (!isEnabled() || scope == null || scope.isBlank() || response == null) {
            return;
        }
        final String normalized = normalize(text);
        if (normalized.length() < Math.max(1, properties.getMinTextLength())) {
            return;
        }
        try {
            final Entry entry = new Entry(simHash(normalized), tokenCount(normalized), response,
                    System.currentTimeMillis() + ttlMillis());
            final Deque<Entry> bucket = buckets.computeIfAbsent(scope, key -> new ArrayDeque<>());
            synchronized (bucket) {
                purgeExpired(bucket, System.currentTimeMillis());
                bucket.addLast(entry);
                totalEntries.incrementAndGet();
                while (bucket.size() > Math.max(1, properties.getMaxEntriesPerModel())) {
                    bucket.removeFirst();
                    totalEntries.decrementAndGet();
                }
            }
            evictIfOverGlobalLimit();
        } catch (RuntimeException ex) {
            logger.warn("语义缓存写入失败，忽略本条: {}", ex.toString());
        }
    }

    /**
     * 记录一次「因含敏感内容而拒绝写入」.
     *
     * <p>单独暴露计数而不在 {@link #put} 里判断，是因为敏感判定需要响应正文 —— 那在调用方手里。</p>
     */
    public void countRejectedForPii() {
        if (rejectedPii != null) {
            rejectedPii.increment();
        }
    }

    /**
     * 按 scope 清除（L2 支持按租户清，L3 只支持全清）.
     *
     * @param scope scope；传 {@code null} 表示全清
     * @return 清除的条目数
     */
    public int evict(final String scope) {
        if (scope == null) {
            final int removed = size();
            buckets.clear();
            totalEntries.set(0);
            return removed;
        }
        final Deque<Entry> bucket = buckets.remove(scope);
        if (bucket == null) {
            return 0;
        }
        synchronized (bucket) {
            final int removed = bucket.size();
            totalEntries.addAndGet(-removed);
            return removed;
        }
    }

    /**
     * 按租户清除全部 L2 条目.
     *
     * @param apiKeyId 租户标识
     * @return 清除的条目数
     */
    public int evictTenant(final String apiKeyId) {
        final Iterator<Map.Entry<String, Deque<Entry>>> it = buckets.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            final Map.Entry<String, Deque<Entry>> entry = it.next();
            if (!SemanticCacheScope.belongsToTenant(entry.getKey(), apiKeyId)) {
                continue;
            }
            synchronized (entry.getValue()) {
                removed += entry.getValue().size();
                totalEntries.addAndGet(-entry.getValue().size());
            }
            it.remove();
        }
        return removed;
    }

    /**
     * 清空全部条目.
     */
    public void clear() {
        buckets.clear();
        totalEntries.set(0);
    }

    /**
     * 当前条目总数（供测试与运维观测）.
     *
     * @return 条目数
     */
    public int size() {
        return Math.max(0, totalEntries.get());
    }

    private void evictIfOverGlobalLimit() {
        final int limit = Math.max(1, properties.getGlobalMaxEntries());
        while (totalEntries.get() > limit) {
            Deque<Entry> oldestBucket = null;
            String oldestScope = null;
            for (final Map.Entry<String, Deque<Entry>> entry : buckets.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    oldestBucket = entry.getValue();
                    oldestScope = entry.getKey();
                    break;
                }
            }
            if (oldestBucket == null) {
                totalEntries.set(0);
                return;
            }
            synchronized (oldestBucket) {
                if (oldestBucket.isEmpty()) {
                    buckets.remove(oldestScope, oldestBucket);
                    continue;
                }
                oldestBucket.removeFirst();
                totalEntries.decrementAndGet();
                if (oldestBucket.isEmpty()) {
                    buckets.remove(oldestScope, oldestBucket);
                }
            }
        }
    }

    private Counter counter(final MeterRegistry registry, final String name, final String description) {
        return registry == null ? null : Counter.builder(name).description(description).register(registry);
    }

    private boolean isSimilar(final Entry entry, final long fingerprint, final int tokens) {
        if (Long.bitCount(entry.fingerprint() ^ fingerprint) > properties.getMaxHammingDistance()) {
            return false;
        }
        final int other = entry.tokens();
        if (other == 0 || tokens == 0) {
            return other == tokens;
        }
        final int diff = Math.abs(other - tokens);
        return diff * 5 <= Math.max(other, tokens);
    }

    private void purgeExpired(final Deque<Entry> bucket, final long now) {
        while (!bucket.isEmpty() && bucket.peekFirst().expireAt() <= now) {
            bucket.removeFirst();
            totalEntries.decrementAndGet();
        }
    }

    private long ttlMillis() {
        final Duration ttl = properties.getTtl();
        return ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofMinutes(10).toMillis() : ttl.toMillis();
    }

    private void countHit() {
        if (hits != null) {
            hits.increment();
        }
    }

    private void countMiss() {
        if (misses != null) {
            misses.increment();
        }
    }

    /**
     * 归一化：小写、把任意非字母数字字符折叠成单个空格、去首尾空白.
     *
     * @param text 原始文本
     * @return 归一化后的文本（可能为空串）
     */
    public static String normalize(final String text) {
        if (text == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(text.length());
        boolean lastSpace = true;
        for (int i = 0; i < text.length(); i++) {
            final char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch)) {
                sb.append(Character.toLowerCase(ch));
                lastSpace = false;
            } else if (!lastSpace) {
                sb.append(' ');
                lastSpace = true;
            }
        }
        return sb.toString().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 计算 64 位 SimHash 指纹.
     *
     * @param normalized 归一化文本
     * @return 指纹
     */
    static long simHash(final String normalized) {
        if (normalized.isEmpty()) {
            return 0L;
        }
        final int[] weights = new int[HASH_BITS];
        final int gram = 3;
        for (int i = 0; i + gram <= normalized.length(); i++) {
            final long hash = hash64(normalized.substring(i, i + gram));
            for (int bit = 0; bit < HASH_BITS; bit++) {
                weights[bit] += ((hash >>> bit) & 1L) == 1L ? 1 : -1;
            }
        }
        long fingerprint = 0L;
        for (int bit = 0; bit < HASH_BITS; bit++) {
            if (weights[bit] > 0) {
                fingerprint |= 1L << bit;
            }
        }
        return fingerprint;
    }

    /**
     * 计算词（3-gram）数量.
     *
     * @param normalized 归一化文本
     * @return 3-gram 数量
     */
    static int tokenCount(final String normalized) {
        return normalized.isEmpty() ? 0 : Math.max(1, normalized.length() - 2);
    }

    private static long hash64(final String value) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            long hash = 0L;
            for (int i = 0; i < 8; i++) {
                hash = (hash << 8) | (bytes[i] & 0xFFL);
            }
            return hash;
        } catch (NoSuchAlgorithmException ex) {
            return value.hashCode();
        }
    }

    /**
     * 一条缓存记录.
     *
     * @param fingerprint SimHash 指纹
     * @param tokens      3-gram 数量
     * @param response    缓存值
     * @param expireAt    过期时刻（epoch millis）
     */
    record Entry(long fingerprint, int tokens, Object response, long expireAt) {
    }
}
