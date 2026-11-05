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
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语义响应缓存（近似请求复用）.
 *
 * <p>与 {@link ResponseCacheService} 的分工：后者按<b>精确键</b>（租户 + 服务 + 模型 + 请求体
 * 的 SHA-256）命中，只复用「同一个请求」；本类在精确键之外提供一层<b>文本相似</b>命中，
 * 复用「措辞不同、意图相同」的请求。</p>
 *
 * <p><b>实现选择：SimHash 而非向量 embedding</b>。本仓已配置 embedding 模型，但走 embedding
 * 意味着每个请求多一次上游调用（延迟与成本翻倍），而语义缓存的收益本就来自「省掉一次上游调用」。
 * 因此这里用纯本地的 64 位 SimHash 指纹 + 汉明距离做近似判定：零外部调用、零额外依赖、
 * 结果可解释。代价是只能识别「词汇高度重叠」的近似，换述（同义词替换、语序大改）识别不到。</p>
 *
 * <p><b>失败语义 = 静默旁路</b>：本类的任何异常都不影响主链路（写入/查找失败按未命中处理），
 * 与响应缓存「缓存是旁路能力」的既有约定一致。</p>
 *
 * <p><b>默认关闭</b>，见 {@link SemanticCacheProperties#isEnabled()}。关闭时 {@link #find} 恒空、
 * {@link #put} 无操作。</p>
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

    /** SimHash 的位数。 */
    private static final int HASH_BITS = 64;

    private final SemanticCacheProperties properties;

    /** 模型名 → 条目队列（新条目在尾部，队首最旧，便于淘汰）。 */
    private final ConcurrentHashMap<String, Deque<Entry>> buckets = new ConcurrentHashMap<>();

    private final Counter hits;
    private final Counter misses;

    /**
     * 构造器.
     *
     * @param properties   配置面
     * @param meterRegistry 指标注册表（可为 {@code null}，为 null 时不记指标）
     */
    public SemanticCacheService(
            final SemanticCacheProperties properties,
            @Autowired(required = false) final MeterRegistry meterRegistry) {
        this.properties = properties;
        this.hits = meterRegistry == null ? null : Counter.builder(METRIC_HITS)
                .description("语义缓存命中次数")
                .register(meterRegistry);
        this.misses = meterRegistry == null ? null : Counter.builder(METRIC_MISSES)
                .description("语义缓存未命中次数")
                .register(meterRegistry);
    }

    /**
     * 语义缓存是否启用.
     *
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 查找一个足够相似的缓存条目.
     *
     * @param modelName 模型名（不同模型的答案不可互换，故按模型分桶）
     * @param text      用于比对的请求文本（通常是 messages 的拼接）
     * @return 命中的缓存值；未启用、文本过短、无相似条目时返回 {@link Optional#empty()}
     */
    public Optional<Object> find(final String modelName, final String text) {
        if (!isEnabled() || modelName == null || modelName.isBlank()) {
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
            final Deque<Entry> bucket = buckets.get(modelName);
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
     * @param modelName 模型名
     * @param text      请求文本
     * @param response  待复用的响应值
     */
    public void put(final String modelName, final String text, final Object response) {
        if (!isEnabled() || modelName == null || modelName.isBlank() || response == null) {
            return;
        }
        final String normalized = normalize(text);
        if (normalized.length() < Math.max(1, properties.getMinTextLength())) {
            return;
        }
        try {
            final Entry entry = new Entry(simHash(normalized), tokenCount(normalized), response,
                    System.currentTimeMillis() + ttlMillis());
            final Deque<Entry> bucket = buckets.computeIfAbsent(modelName, key -> new ArrayDeque<>());
            synchronized (bucket) {
                purgeExpired(bucket, System.currentTimeMillis());
                bucket.addLast(entry);
                while (bucket.size() > Math.max(1, properties.getMaxEntriesPerModel())) {
                    bucket.removeFirst();
                }
            }
        } catch (RuntimeException ex) {
            logger.warn("语义缓存写入失败，忽略本条: {}", ex.toString());
        }
    }

    /**
     * 清空全部条目（供管理面/测试使用）.
     */
    public void clear() {
        buckets.clear();
    }

    /**
     * 当前条目总数（供测试与运维观测）.
     *
     * @return 条目数
     */
    public int size() {
        int total = 0;
        for (final Deque<Entry> bucket : buckets.values()) {
            synchronized (bucket) {
                total += bucket.size();
            }
        }
        return total;
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
     * <p>中文没有词边界，这里不做分词，只做字符级归一化；SimHash 的权重按字符 3-gram 统计。</p>
     *
     * @param text 原始文本
     * @return 归一化后的文本（可能为空串）
     */
    static String normalize(final String text) {
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
     * 计算词（3-gram）数量，用于「长度差太大就不算相似」的粗过滤.
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
            // SHA-256 是 JDK 强制要求提供的算法，这里不可达
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
