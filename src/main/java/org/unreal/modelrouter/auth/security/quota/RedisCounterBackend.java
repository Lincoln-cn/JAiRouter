package org.unreal.modelrouter.auth.security.quota;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Redis 分布式计数后端（v3.1 PR-3）。
 *
 * <p>多实例共享额度要求计数在实例之外，因此把 PR-1 的内存账本计数替换为 Redis Hash 计数：
 * 每个 {@link QuotaCounterKey} 对应一个 Hash，字段 {@code req} / {@code tok} 分别累计请求数与
 * token 数，累加与 TTL 设置通过 <b>单个 Lua 脚本原子完成</b>（避免 {@code HINCRBY} 与
 * {@code EXPIRE} 之间被中断导致计数没有过期时间）。</p>
 *
 * <p><b>Key 结构</b>：{@code {prefix}:{tenant}:{apiKey}:{user}:{service}:{model}:{WINDOW}:{windowStart}}
 * （{@link #keyOf(QuotaCounterKey)}）。{@code prefix} 由
 * {@code jairouter.quota.distributed.key-prefix} 配置；各维度片段中的非安全字符统一替换为
 * {@code '_'}（片段内不允许出现 {@code ':'}，否则不同维度可能拼出同一个 key），空片段写为
 * {@code '_'}；{@code windowStart} 采用 {@code yyyyMMddHHmmss} 定长格式，便于
 * {@link #deleteExpired(QuotaWindow, LocalDateTime)} 从 key 反解窗口起点。</p>
 *
 * <p><b>TTL</b>：{@link #ttlSeconds(QuotaWindow)} = 窗口长度 × 该窗口保留期（保留期换算为上取整的
 * 窗口长度倍数，例如 MINUTE 窗口保留期 {@code 1d} → 1440 分钟 = 86400 秒；MONTH 窗口按 30 天
 * 近似日历月）。TTL 只在桶首次写入时设置（脚本内 {@code TTL == -1} 判断），因此桶必定活过窗口
 * 结束 + 保留期，同时不会因为持续访问被反复续期而无限增长。</p>
 *
 * <p><b>维护路径</b>：{@link #reset(String)} 与 {@link #deleteExpired(QuotaWindow, LocalDateTime)}
 * 用 {@code SCAN}（批量 {@value #SCAN_BATCH}）+ 批量 {@code DEL} 实现，不阻塞式遍历整个 keyspace，
 * 且会按 key 段二次校验，避免误删（例如用户 ID 恰好等于 API Key ID 的 key）。</p>
 *
 * <p><b>异常</b>：本类不吞异常——连接失败 / 超时 / 脚本错误都会以 {@link Mono#error} 传播，
 * 由 {@link QuotaLedgerService} 按 {@code degrade-to-local} 与 {@code fail-open} 策略统一处理。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@Slf4j
public final class RedisCounterBackend implements QuotaCounterBackend {

    /** 后端名称 */
    public static final String NAME = "redis";

    /** 窗口起点在 key 中的格式（定长、无分隔符，便于反解） */
    private static final DateTimeFormatter WINDOW_START_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** SCAN 单批数量 */
    private static final int SCAN_BATCH = 256;

    /** key 段数量：tenant / apiKey / user / service / model / WINDOW / windowStart */
    private static final int KEY_SEGMENTS = 7;

    /** 空维度片段占位符 */
    private static final String EMPTY_SEGMENT = "_";

    /** Hash 请求数字段名 */
    private static final String FIELD_REQUESTS = "req";

    /** Hash token 字段名 */
    private static final String FIELD_TOKENS = "tok";

    /** 累加脚本：HINCRBY 两个字段 + 首次写入时设置 TTL，返回 {@code "req:tok"} */
    static final String INCREMENT_SCRIPT = String.join("\n",
        "local requests = redis.call('HINCRBY', KEYS[1], '" + FIELD_REQUESTS + "', ARGV[1])",
        "local tokens = redis.call('HINCRBY', KEYS[1], '" + FIELD_TOKENS + "', ARGV[2])",
        "if requests < 0 then redis.call('HSET', KEYS[1], '" + FIELD_REQUESTS + "', 0) requests = 0 end",
        "if tokens < 0 then redis.call('HSET', KEYS[1], '" + FIELD_TOKENS + "', 0) tokens = 0 end",
        "if redis.call('TTL', KEYS[1]) == -1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end",
        "return requests .. ':' .. tokens");

    /** 累加脚本（仅在桶已存在时生效），用于结算冲正语义 */
    static final String INCREMENT_IF_PRESENT_SCRIPT = String.join("\n",
        "if redis.call('EXISTS', KEYS[1]) == 0 then return '' end",
        INCREMENT_SCRIPT);

    /** 读数脚本：返回 {@code "req:tok"}，桶不存在时返回空串 */
    static final String READ_SCRIPT = String.join("\n",
        "local requests = redis.call('HGET', KEYS[1], '" + FIELD_REQUESTS + "')",
        "if not requests then return '' end",
        "local tokens = redis.call('HGET', KEYS[1], '" + FIELD_TOKENS + "')",
        "if not tokens then tokens = '0' end",
        "return requests .. ':' .. tokens");

    private static final RedisScript<String> INCREMENT =
        RedisScript.of(INCREMENT_SCRIPT, String.class);

    private static final RedisScript<String> INCREMENT_IF_PRESENT =
        RedisScript.of(INCREMENT_IF_PRESENT_SCRIPT, String.class);

    private static final RedisScript<String> READ =
        RedisScript.of(READ_SCRIPT, String.class);

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final QuotaProperties properties;

    /**
     * 构造 Redis 计数后端。
     *
     * @param redisTemplate String 序列化的 Reactive Redis 模板，不能为 {@code null}
     * @param properties    账本配置（读取 key 前缀与保留期）
     */
    public RedisCounterBackend(final ReactiveRedisTemplate<String, String> redisTemplate,
                               final QuotaProperties properties) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate 不能为 null");
        this.properties = Objects.requireNonNull(properties, "properties 不能为 null");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Mono<long[]> increment(final QuotaCounterKey key, final long requests, final long tokens) {
        final String redisKey = keyOf(key);
        final List<String> args = List.of(
            String.valueOf(requests), String.valueOf(tokens), String.valueOf(ttlSeconds(key.window())));
        return redisTemplate.execute(INCREMENT, List.of(redisKey), args)
            .next()
            .flatMap(payload -> toValues(payload)
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new IllegalStateException("Redis 计数脚本返回非法结果: " + payload))));
    }

    @Override
    public Mono<long[]> incrementIfPresent(final QuotaCounterKey key, final long requests, final long tokens) {
        final String redisKey = keyOf(key);
        final List<String> args = List.of(
            String.valueOf(requests), String.valueOf(tokens), String.valueOf(ttlSeconds(key.window())));
        return redisTemplate.execute(INCREMENT_IF_PRESENT, List.of(redisKey), args)
            .next()
            .flatMap(payload -> toValues(payload).<Mono<long[]>>map(Mono::just).orElseGet(Mono::empty));
    }

    @Override
    public Mono<Optional<long[]>> read(final QuotaCounterKey key) {
        return redisTemplate.execute(READ, List.of(keyOf(key)), List.of())
            .next()
            .map(RedisCounterBackend::toValues)
            .defaultIfEmpty(Optional.empty());
    }

    @Override
    public Mono<Long> reset(final String apiKeyId) {
        if (apiKeyId == null || apiKeyId.isEmpty()) {
            return Mono.just(0L);
        }
        final String apiKeySegment = sanitize(apiKeyId);
        final String pattern = properties.distributedKeyPrefix() + ":*:" + apiKeySegment + ":*";
        return deleteMatching(pattern, key -> {
            final String[] segments = segmentsOf(key);
            return segments != null && apiKeySegment.equals(segments[1]);
        });
    }

    @Override
    public Mono<Long> deleteExpired(final QuotaWindow window, final LocalDateTime cutoff) {
        if (window == null || cutoff == null) {
            return Mono.just(0L);
        }
        final String pattern = properties.distributedKeyPrefix() + ":*:" + window.name() + ":*";
        return deleteMatching(pattern, key -> {
            final String[] segments = segmentsOf(key);
            if (segments == null || !window.name().equals(segments[5])) {
                return false;
            }
            final LocalDateTime windowStart = parseWindowStart(segments[6]);
            return windowStart != null && windowStart.isBefore(cutoff);
        });
    }

    /**
     * 构造某个计数键对应的 Redis key。
     *
     * @param key 计数键
     * @return Redis key，形如 {@code {prefix}:{tenant}:{apiKey}:{user}:{service}:{model}:{WINDOW}:{windowStart}}
     */
    public String keyOf(final QuotaCounterKey key) {
        final QuotaDimension dimension = key.dimension();
        return properties.distributedKeyPrefix()
            + ':' + sanitize(dimension.tenantId())
            + ':' + sanitize(dimension.apiKeyId())
            + ':' + sanitize(dimension.userId())
            + ':' + sanitize(dimension.serviceType())
            + ':' + sanitize(dimension.model())
            + ':' + key.window().name()
            + ':' + key.windowStart().format(WINDOW_START_FORMAT);
    }

    /**
     * 计算某个窗口计数的 TTL 秒数：窗口长度 × 该窗口保留期（保留期换算为上取整的窗口长度倍数，
     * 即至少 1 个窗口长度）。MONTH 窗口以 30 天近似日历月（Redis TTL 只接受秒数）。
     *
     * @param window 窗口类型，不能为 {@code null}
     * @return TTL 秒数（不小于该窗口长度）
     */
    public long ttlSeconds(final QuotaWindow window) {
        Objects.requireNonNull(window, "window 不能为 null");
        final long windowSeconds = windowSeconds(window);
        final long retentionSeconds = parseRetentionSeconds(properties.retentionSpec(window));
        final long windowCount = Math.max(1L, (retentionSeconds + windowSeconds - 1L) / windowSeconds);
        return windowCount * windowSeconds;
    }

    /**
     * 按模式扫描并删除符合条件的 key（SCAN 分批，不阻塞 keyspace）。
     *
     * @param pattern  SCAN 匹配模式
     * @param verifier 二次校验（按 key 段精确确认，避免 glob 误匹配）
     * @return 删除的 key 数量
     */
    private Mono<Long> deleteMatching(final String pattern, final KeyVerifier verifier) {
        final ScanOptions options = ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH).build();
        final Flux<String> matched = redisTemplate.scan(options)
            .filter(key -> key != null && verifier.matches(key));
        return matched.buffer(SCAN_BATCH)
            .flatMap(batch -> redisTemplate.delete(Flux.fromIterable(batch)))
            .reduce(0L, Long::sum);
    }

    /**
     * 拆分 key 为固定段数（{@code tenant/apiKey/user/service/model/WINDOW/windowStart}）。
     *
     * @param key Redis key
     * @return 段数组；前缀不匹配或段数不符时返回 {@code null}
     */
    private String[] segmentsOf(final String key) {
        final String prefix = properties.distributedKeyPrefix() + ':';
        if (key == null || !key.startsWith(prefix)) {
            return null;
        }
        final String[] segments = key.substring(prefix.length()).split(":", -1);
        return segments.length == KEY_SEGMENTS ? segments : null;
    }

    /**
     * 解析窗口起点片段。
     *
     * @param segment {@code yyyyMMddHHmmss} 片段
     * @return 窗口起点；格式不符时返回 {@code null}（跨版本残留 key，跳过不删）
     */
    private static LocalDateTime parseWindowStart(final String segment) {
        try {
            return LocalDateTime.parse(segment, WINDOW_START_FORMAT);
        } catch (RuntimeException e) {
            log.debug("跳过无法解析窗口起点的配额计数 key 片段: {}", segment);
            return null;
        }
    }

    /**
     * 把脚本返回值 {@code "req:tok"} 解析为二元数组（非负钳制）。
     *
     * @param payload 脚本返回值，可为 {@code null} 或空串（表示桶不存在）
     * @return 计数值；空串 / 非法格式时返回 {@link Optional#empty()}
     */
    private static Optional<long[]> toValues(final String payload) {
        if (payload == null || payload.isEmpty()) {
            return Optional.empty();
        }
        final int separator = payload.indexOf(':');
        if (separator < 0) {
            log.debug("忽略非法配额计数返回值: {}", payload);
            return Optional.empty();
        }
        return Optional.of(new long[]{
            Math.max(0L, parseLong(payload.substring(0, separator))),
            Math.max(0L, parseLong(payload.substring(separator + 1)))});
    }

    /**
     * 宽松解析计数（脏值按 0 处理，避免单条脏数据把配额链路打成故障）。
     *
     * @param text 数字文本
     * @return 解析结果，非法时返回 0
     */
    private static long parseLong(final String text) {
        try {
            return Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * key 片段安全化：非 {@code [A-Za-z0-9._-]} 字符替换为 {@code '_'}，空值写占位符。
     *
     * @param value 原始维度值
     * @return 安全片段
     */
    private static String sanitize(final String value) {
        if (value == null || value.isEmpty()) {
            return EMPTY_SEGMENT;
        }
        final StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            builder.append(isSafe(current) ? current : '_');
        }
        return builder.toString();
    }

    /**
     * 字符是否可直接放入 key 片段。
     *
     * @param value 字符
     * @return 是字母 / 数字 / {@code .} / {@code -} / {@code _} 时返回 {@code true}
     */
    private static boolean isSafe(final char value) {
        return Character.isLetterOrDigit(value) || value == '.' || value == '-' || value == '_';
    }

    /**
     * 窗口的近似秒数（MONTH 按 30 天近似）。
     *
     * @param window 窗口类型
     * @return 秒数
     */
    private static long windowSeconds(final QuotaWindow window) {
        switch (window) {
            case MINUTE:
                return 60L;
            case HOUR:
                return 3600L;
            case DAY:
                return 86400L;
            default:
                return 30L * 86400L;
        }
    }

    /**
     * 解析保留期原文为秒数（单位语义与 {@link QuotaProperties} 保持一致：
     * {@code mo}=月 / {@code d}=天 / {@code h}=小时 / {@code m}=分钟 / {@code s}=秒，
     * 无单位或非法值按天兜底）。
     *
     * @param spec 保留期原文，可为 {@code null}
     * @return 秒数（不小于 1 秒）
     */
    private static long parseRetentionSeconds(final String spec) {
        final String value = spec == null ? "" : spec.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) {
            return 86400L;
        }
        if (value.endsWith("mo")) {
            return parseAmount(value.substring(0, value.length() - 2)) * 30L * 86400L;
        }
        final char unit = value.charAt(value.length() - 1);
        final long amount = parseAmount(value.substring(0, value.length() - 1));
        switch (unit) {
            case 's':
                return amount;
            case 'm':
                return amount * 60L;
            case 'h':
                return amount * 3600L;
            default:
                return amount * 86400L;
        }
    }

    /**
     * 解析数量部分（非法值兜底 1，与 {@link QuotaProperties} 一致）。
     *
     * @param text 数量文本
     * @return 不小于 1 的数量
     */
    private static long parseAmount(final String text) {
        try {
            return Math.max(1L, Long.parseLong(text.trim()));
        } catch (NumberFormatException e) {
            return 1L;
        }
    }

    /**
     * 删除前的 key 二次校验。
     */
    @FunctionalInterface
    private interface KeyVerifier {

        /**
         * 判断 key 是否应删除。
         *
         * @param key Redis key
         * @return 应删除时返回 {@code true}
         */
        boolean matches(String key);
    }
}
