package org.unreal.modelrouter.auth.security.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.unreal.modelrouter.auth.security.cache.JwtBlacklistCacheManager;
import org.unreal.modelrouter.auth.security.config.RedisJwtCacheConfiguration;
import reactor.core.publisher.Mono;

/**
 * 健康检查器在异常 message 为 null 时的行为。
 *
 * <p>背景：{@code Map.of} 与 {@code Health.Builder#withDetail} 都拒绝 null 值，
 * 而 Redis 超时、连接被重置、{@code block()} 被取消这些异常的 message 常常是 null。
 * 修复前 {@code checkBlacklistCache} 的兜底分支正是把 {@code e.getMessage()} 直接塞进
 * {@code Map.of}，于是一个本来只想「降级上报」的异常变成了新的 NPE，
 * 被 {@code /actuator/health} 渲染成 400「参数验证失败：'value' must not be null」
 * —— 多副本部署打开 JWT Redis 开关后可以稳定复现（issue #1133）。</p>
 */
@DisplayName("JWT Redis 健康检查：异常 message 为 null 时不得变成 NPE")
class JwtRedisHealthMonitorHealthDetailTest {

    @SuppressWarnings("unchecked")
    private ReactiveRedisTemplate<String, String> template() {
        ReactiveRedisTemplate<String, String> template = mock(ReactiveRedisTemplate.class);
        ReactiveValueOperations<String, String> ops = mock(ReactiveValueOperations.class);
        lenient().when(template.opsForValue()).thenReturn(ops);
        lenient().when(ops.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));
        lenient().when(ops.get(anyString())).thenReturn(Mono.empty());
        lenient().when(template.delete(anyString())).thenReturn(Mono.just(1L));
        return template;
    }

    private JwtRedisHealthMonitor monitorWith(Throwable cacheFailure) {
        JwtBlacklistCacheManager cacheManager = mock(JwtBlacklistCacheManager.class);
        when(cacheManager.getBlacklistCacheStats()).thenReturn(Mono.error(cacheFailure));

        RedisJwtCacheConfiguration.RedisJwtHealthChecker checker =
                mock(RedisJwtCacheConfiguration.RedisJwtHealthChecker.class);
        when(checker.isHealthy()).thenReturn(true);

        return new JwtRedisHealthMonitor(template(), cacheManager, checker);
    }

    @Test
    @DisplayName("黑名单缓存统计抛无消息异常：health() 不抛异常，error 兜底为异常类名")
    void nullExceptionMessage_shouldNotThrowFromHealthCheck() {
        JwtRedisHealthMonitor monitor = monitorWith(new RuntimeException());

        Health health = monitor.health();

        assertNotNull(health, "health() 必须返回结果，而不是抛出");
        assertEquals("DEGRADED", health.getStatus().getCode(),
                "黑名单缓存拿不到统计时应降级而不是整体 DOWN");

        Object blacklist = health.getDetails().get("blacklist-cache");
        assertNotNull(blacklist, "应带上 blacklist-cache 明细");
        Object error = ((Map<?, ?>) blacklist).get("error");
        assertNotNull(error, "error 明细不得是 null（Map.of 会因此抛 NPE）");
        assertEquals("RuntimeException", error, "没有 message 时应回落成异常简单类名");
    }

    @Test
    @DisplayName("异常带消息时保留原始消息，不被兜底覆盖")
    void exceptionMessage_shouldBePreserved() {
        JwtRedisHealthMonitor monitor = monitorWith(new IllegalStateException("redis connection reset"));

        Health health = monitor.health();

        Object blacklist = health.getDetails().get("blacklist-cache");
        Object error = ((Map<?, ?>) blacklist).get("error");
        assertEquals("redis connection reset", error);
        assertTrue(!"UP".equals(health.getStatus().getCode()), "统计失败时不应报 UP");
    }

    @Test
    @DisplayName("Redis 连接不可用：整体 DOWN，detail 不出现 null")
    void redisDown_shouldReportDownWithNonNullDetail() {
        RedisJwtCacheConfiguration.RedisJwtHealthChecker checker =
                mock(RedisJwtCacheConfiguration.RedisJwtHealthChecker.class);
        when(checker.isHealthy()).thenReturn(false);

        JwtRedisHealthMonitor monitor = new JwtRedisHealthMonitor(
                template(), mock(JwtBlacklistCacheManager.class), checker);

        Health health = monitor.health();

        assertEquals(Status.DOWN.getCode(), health.getStatus().getCode());
        assertEquals("Connection failed", health.getDetails().get("redis"));
    }
}
