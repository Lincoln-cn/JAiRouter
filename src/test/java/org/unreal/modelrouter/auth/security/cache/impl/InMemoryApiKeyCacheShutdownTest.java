package org.unreal.modelrouter.auth.security.cache.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * issue #1065：内存 API Key 缓存的清理线程要在关闭时停掉。
 *
 * <p>构造函数用 {@code Executors.newScheduledThreadPool(1)} 起了一条非守护线程，
 * 没有 {@code @PreDestroy} 时容器关闭不会打断它（测试/热重载下表现为线程泄漏）。</p>
 */
@DisplayName("InMemoryApiKeyCache 关闭行为（issue #1065）")
class InMemoryApiKeyCacheShutdownTest {

    @Test
    @DisplayName("shutdown() 之后清理线程池不再运行")
    void shutdownStopsScheduler() throws Exception {
        InMemoryApiKeyCache cache = new InMemoryApiKeyCache(null);

        cache.shutdown();

        Field field = InMemoryApiKeyCache.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        ScheduledExecutorService scheduler = (ScheduledExecutorService) field.get(cache);

        assertTrue(scheduler.isShutdown(), "关闭后清理线程池必须处于 shutdown 状态");
    }
}
