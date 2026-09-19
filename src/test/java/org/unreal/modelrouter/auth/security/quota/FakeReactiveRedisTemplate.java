package org.unreal.modelrouter.auth.security.quota;

import org.reactivestreams.Publisher;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 测试用假 {@link ReactiveRedisTemplate}（v3.1 PR-3 测试夹具）。
 *
 * <p>这是手写的真实实现而不是 Mockito mock：内部用两个 {@link LinkedHashMap} 模拟 Redis Hash
 * 与过期时间，并按 {@link RedisCounterBackend} 的 Lua 脚本语义（{@code HINCRBY} 双字段 +
 * 首次写入设置 TTL + 非负钳制）执行脚本，从而让
 * {@link RedisCounterBackend} 的关键行为（key 结构、脚本调用形态、TTL、双字段累加、批量删除）
 * 在无 Redis 环境下也能被完整验证。</p>
 *
 * <p>故障注入：{@link #setFailing(boolean)} 让所有命令以
 * {@link RedisConnectionFailureException} 失败（模拟断连）；{@link #setNeverCompleting(boolean)}
 * 让所有命令永不完成（模拟超时）。</p>
 *
 * <p>只覆盖 {@code execute(script, keys, args)} / {@code scan(options)} /
 * {@code delete(Publisher)} 三个入口——它们是 {@link RedisCounterBackend} 唯一的 Redis 触点；
 * 其它命令一旦被调用说明实现越界，会直接抛出 {@link IllegalStateException} 暴露出来
 * （底层 {@link LettuceConnectionFactory} 不指向任何真实实例）。</p>
 */
public final class FakeReactiveRedisTemplate extends ReactiveRedisTemplate<String, String> {

    /** 请求数字段名（与 Lua 脚本一致） */
    private static final String FIELD_REQUESTS = "req";

    /** token 字段名（与 Lua 脚本一致） */
    private static final String FIELD_TOKENS = "tok";

    /** Hash 数据：key → (field → value) */
    private final Map<String, Map<String, Long>> hashes = new LinkedHashMap<>();

    /** TTL 数据：key → 首次写入时生效的过期秒数 */
    private final Map<String, Long> expirations = new LinkedHashMap<>();

    /** 脚本调用记录（并发安全） */
    private final List<Invocation> invocations = new CopyOnWriteArrayList<>();

    /** 命令调用次数（含 scan / delete） */
    private final AtomicInteger commands = new AtomicInteger();

    private volatile boolean failing;

    private volatile boolean neverCompleting;

    /**
     * 构造假模板。
     */
    public FakeReactiveRedisTemplate() {
        super(new LettuceConnectionFactory(), RedisSerializationContext.string());
    }

    /**
     * 一次脚本调用的记录。
     *
     * @param script 脚本正文（用于确认调用的是哪个 Lua 脚本）
     * @param keys   KEYS 参数
     * @param args   ARGV 参数（请求数 / token 数 / TTL 秒数）
     */
    public record Invocation(String script, List<String> keys, List<String> args) {
    }

    /**
     * 注入“Redis 不可用”故障（所有命令失败）。
     *
     * @param value true = 失败
     */
    public void setFailing(final boolean value) {
        this.failing = value;
    }

    /**
     * 注入“Redis 无响应”故障（所有命令永不完成，用于验证超时降级）。
     *
     * @param value true = 永不完成
     */
    public void setNeverCompleting(final boolean value) {
        this.neverCompleting = value;
    }

    /**
     * 命令调用总次数。
     *
     * @return 次数
     */
    public int commandCount() {
        return commands.get();
    }

    /**
     * 脚本调用记录。
     *
     * @return 记录列表（按调用顺序）
     */
    public List<Invocation> invocations() {
        return List.copyOf(invocations);
    }

    /**
     * 最后一次脚本调用记录。
     *
     * @return 记录；未调用过时返回 {@code null}
     */
    public Invocation lastInvocation() {
        return invocations.isEmpty() ? null : invocations.get(invocations.size() - 1);
    }

    /**
     * 直接写入一个计数桶（模拟其它实例已产生的计数）。
     *
     * @param key      Redis key
     * @param requests 请求数
     * @param tokens   token 数
     * @param ttlSec   TTL 秒数
     */
    public void seed(final String key, final long requests, final long tokens, final long ttlSec) {
        final Map<String, Long> hash = new LinkedHashMap<>();
        hash.put(FIELD_REQUESTS, requests);
        hash.put(FIELD_TOKENS, tokens);
        hashes.put(key, hash);
        expirations.put(key, ttlSec);
    }

    /**
     * 读取一个计数桶。
     *
     * @param key Redis key
     * @return {@code [requests, tokens]}；不存在时返回 {@code null}
     */
    public long[] values(final String key) {
        final Map<String, Long> hash = hashes.get(key);
        if (hash == null) {
            return null;
        }
        return new long[]{hash.getOrDefault(FIELD_REQUESTS, 0L), hash.getOrDefault(FIELD_TOKENS, 0L)};
    }

    /**
     * 读取一个计数桶的 TTL。
     *
     * @param key Redis key
     * @return 过期秒数；不存在时返回 {@code null}
     */
    public Long ttlSeconds(final String key) {
        return expirations.get(key);
    }

    /**
     * 当前全部计数桶 key。
     *
     * @return key 集合
     */
    public Set<String> keys() {
        return Set.copyOf(hashes.keySet());
    }

    /**
     * 清空全部数据与调用记录。
     */
    public void clear() {
        hashes.clear();
        expirations.clear();
        invocations.clear();
        commands.set(0);
        failing = false;
        neverCompleting = false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> Flux<T> execute(final RedisScript<T> script, final List<String> keys, final List<?> args) {
        final List<String> argText = args == null ? List.of() : args.stream().map(String::valueOf).toList();
        invocations.add(new Invocation(script.getScriptAsString(), List.copyOf(keys), argText));
        commands.incrementAndGet();
        if (neverCompleting) {
            return Flux.never();
        }
        if (failing) {
            return Flux.error(unavailable());
        }
        return (Flux<T>) Flux.just(applyScript(script.getScriptAsString(), keys.get(0), argText));
    }

    @Override
    public Flux<String> scan(final ScanOptions options) {
        commands.incrementAndGet();
        if (neverCompleting) {
            return Flux.never();
        }
        if (failing) {
            return Flux.error(unavailable());
        }
        final String pattern = options == null ? null : options.getPattern();
        return Flux.fromIterable(hashes.keySet().stream().filter(key -> matches(pattern, key)).toList());
    }

    @Override
    public Mono<Long> delete(final Publisher<String> keys) {
        commands.incrementAndGet();
        if (neverCompleting) {
            return Mono.never();
        }
        if (failing) {
            return Mono.error(unavailable());
        }
        return Flux.from(keys)
            .doOnNext(key -> {
                hashes.remove(key);
                expirations.remove(key);
            })
            .count();
    }

    /**
     * 按 Lua 脚本语义执行脚本。
     *
     * @param scriptText 脚本正文
     * @param key        KEYS[1]
     * @param args       ARGV
     * @return 脚本返回值（{@code "req:tok"}，桶不存在时空串）
     */
    private synchronized String applyScript(final String scriptText, final String key, final List<String> args) {
        if (RedisCounterBackend.READ_SCRIPT.equals(scriptText)) {
            final Map<String, Long> hash = hashes.get(key);
            if (hash == null) {
                return "";
            }
            return hash.getOrDefault(FIELD_REQUESTS, 0L) + ":" + hash.getOrDefault(FIELD_TOKENS, 0L);
        }
        final boolean onlyIfPresent = RedisCounterBackend.INCREMENT_IF_PRESENT_SCRIPT.equals(scriptText);
        final boolean withLimit = RedisCounterBackend.INCREMENT_WITH_LIMIT_SCRIPT.equals(scriptText);
        if (!onlyIfPresent && !withLimit && !RedisCounterBackend.INCREMENT_SCRIPT.equals(scriptText)) {
            throw new IllegalStateException("假 Redis 未覆盖的脚本: " + scriptText);
        }
        if (onlyIfPresent && !hashes.containsKey(key)) {
            return "";
        }
        final Map<String, Long> hash = hashes.computeIfAbsent(key, ignored -> new LinkedHashMap<>());
        final long prevReq = hash.getOrDefault(FIELD_REQUESTS, 0L);
        final long prevTok = hash.getOrDefault(FIELD_TOKENS, 0L);
        final long requests = Math.max(0L, prevReq + Long.parseLong(args.get(0)));
        final long tokens = Math.max(0L, prevTok + Long.parseLong(args.get(1)));
        if (withLimit) {
            final long maxReq = args.size() > 3 ? Long.parseLong(args.get(3)) : 0L;
            final long maxTok = args.size() > 4 ? Long.parseLong(args.get(4)) : 0L;
            if ((maxReq > 0L && requests > maxReq) || (maxTok > 0L && tokens > maxTok)) {
                // 超限：回滚（不更新 TTL 以外的状态——桶可能已存在）
                if (!hashes.containsKey(key) || (prevReq == 0L && prevTok == 0L && hash.isEmpty())) {
                    // keep empty bucket without values if just created
                }
                hash.put(FIELD_REQUESTS, prevReq);
                hash.put(FIELD_TOKENS, prevTok);
                expirations.putIfAbsent(key, Long.parseLong(args.get(2)));
                return "OVER";
            }
        }
        hash.put(FIELD_REQUESTS, requests);
        hash.put(FIELD_TOKENS, tokens);
        expirations.putIfAbsent(key, Long.parseLong(args.get(2)));
        return requests + ":" + tokens;
    }

    /**
     * Redis 断连异常。
     *
     * @return 异常实例
     */
    private static RuntimeException unavailable() {
        return new RedisConnectionFailureException("模拟 Redis 不可用");
    }

    /**
     * 简化版 glob 匹配（只支持 {@code *} 与 {@code ?}，用于模拟 SCAN MATCH）。
     *
     * @param pattern 匹配模式，{@code null} 表示匹配全部
     * @param key     Redis key
     * @return 是否匹配
     */
    private static boolean matches(final String pattern, final String key) {
        if (pattern == null) {
            return true;
        }
        final StringBuilder regex = new StringBuilder();
        for (int index = 0; index < pattern.length(); index++) {
            final char current = pattern.charAt(index);
            if (current == '*') {
                regex.append(".*");
            } else if (current == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(current)));
            }
        }
        return Pattern.compile(regex.toString()).matcher(key).matches();
    }
}
