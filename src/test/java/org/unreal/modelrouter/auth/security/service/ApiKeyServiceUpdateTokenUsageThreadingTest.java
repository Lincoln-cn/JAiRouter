package org.unreal.modelrouter.auth.security.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.model.UsageStatistics;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * API Key Token 用量更新的线程语义回归测试（issue #115）。
 *
 * <p>缺陷背景：{@code updateTokenUsage} 由流式 {@code doOnComplete} / 非流式 token 落库点调用，
 * 位于 Reactor 非阻塞线程（Netty 事件循环）上，而其内部
 * {@code apiKeyPersistenceService.saveApiKeysToStore(apiKeyCache)} 是“全量序列化 + 文件/Redis 写入”
 * 的阻塞存储操作，会把 IO 线程钉死。修复后在非阻塞线程上把工作整体交给
 * {@code boundedElastic}（fire-and-forget），在普通线程上仍内联执行（返回即已写入）。</p>
 *
 * <p>被测对象是真实 {@link ApiKeyService} + 手写存根 {@link CapturingPersistenceService}
 * （仅捕获执行线程），不使用 Mockito；异步补写使用有界 {@link CountDownLatch} 等待。</p>
 *
 * @author JAiRouter Team
 * @since 3.1.0
 */
@DisplayName("ApiKeyService.updateTokenUsage 存储写入线程语义（issue #115）")
class ApiKeyServiceUpdateTokenUsageThreadingTest {

    /** 异步存储写入的等待上限（毫秒） */
    private static final long AWAIT_TIMEOUT_MILLIS = 5000L;

    private static final String KEY_ID = "key-1";
    private static final String KEY_HASH = "hash-1";

    /**
     * 手写存根：捕获 {@code saveApiKeysToStore} 的执行线程与是否处于 Reactor 非阻塞线程。
     */
    private static final class CapturingPersistenceService extends ApiKeyPersistenceService {

        private final AtomicReference<String> threadName = new AtomicReference<>();
        private final AtomicBoolean nonBlocking = new AtomicBoolean();
        private final CountDownLatch latch = new CountDownLatch(1);

        CapturingPersistenceService() {
            super(null, null);
        }

        @Override
        public void saveApiKeysToStore(final Map<String, ApiKey> apiKeyCache) {
            threadName.set(Thread.currentThread().getName());
            nonBlocking.set(Schedulers.isInNonBlockingThread());
            latch.countDown();
        }
    }

    @Test
    @DisplayName("非阻塞线程上调用 updateTokenUsage：存储写入不得在该线程上执行")
    void updateTokenUsageOnNonBlockingThread_shouldSaveOffEventLoop() throws Exception {
        final ApiKeyService apiKeyService = newApiKeyService(new CapturingPersistenceService());

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicBoolean callerNonBlocking = new AtomicBoolean();
        final CountDownLatch returned = new CountDownLatch(1);
        Mono.fromRunnable(() -> {
            callerNonBlocking.set(Schedulers.isInNonBlockingThread());
            try {
                apiKeyService.updateTokenUsage(KEY_ID, 42L);
            } catch (Throwable t) {
                error.set(t);
            } finally {
                returned.countDown();
            }
        }).subscribeOn(Schedulers.parallel()).subscribe();

        assertTrue(returned.await(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "updateTokenUsage 在非阻塞线程上必须立即返回（不得同步等待存储写入）");
        assertTrue(callerNonBlocking.get(), "前置条件：parallel 调度器线程属 Reactor 非阻塞线程");
        assertNull(error.get());

        final CapturingPersistenceService persistence =
                (CapturingPersistenceService) ReflectionTestUtils.getField(apiKeyService, "apiKeyPersistenceService");
        assertTrue(persistence.latch.await(AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS),
                "存储写入必须最终在 boundedElastic 上完成");
        assertFalse(persistence.nonBlocking.get(),
                "saveApiKeysToStore 不得在 Reactor 非阻塞线程（事件循环）上执行");
        assertTrue(persistence.threadName.get().contains("boundedElastic"),
                "存储写入应在 boundedElastic 线程上执行，实际: " + persistence.threadName.get());
    }

    @Test
    @DisplayName("普通线程上调用 updateTokenUsage：同步完成，返回时已写入存储")
    void updateTokenUsageOnPlainThread_shouldSaveInline() throws Exception {
        final CapturingPersistenceService persistence = new CapturingPersistenceService();
        final ApiKeyService apiKeyService = newApiKeyService(persistence);

        final AtomicReference<Throwable> error = new AtomicReference<>();
        final AtomicBoolean nonBlocking = new AtomicBoolean(true);
        final AtomicReference<String> callerThread = new AtomicReference<>();
        final Thread worker = new Thread(() -> {
            callerThread.set(Thread.currentThread().getName());
            nonBlocking.set(Schedulers.isInNonBlockingThread());
            try {
                apiKeyService.updateTokenUsage(KEY_ID, 42L);
            } catch (Throwable t) {
                error.set(t);
            }
        }, "api-key-usage-plain-thread");
        worker.start();
        worker.join(AWAIT_TIMEOUT_MILLIS);

        assertFalse(worker.isAlive(), "普通线程上的 updateTokenUsage 必须同步完成");
        assertFalse(nonBlocking.get(), "前置条件：普通线程不是 Reactor 非阻塞线程");
        assertNull(error.get());
        assertEquals(0L, persistence.latch.getCount(),
                "内联路径：方法返回即已写入存储（既有测试依赖该确定性）");
        assertEquals(callerThread.get(), persistence.threadName.get(),
                "内联路径：存储写入应在调用线程上执行");
    }

    // ==================== 辅助 ====================

    /**
     * 构造带缓存条目的 {@link ApiKeyService}，并注入存储存根.
     *
     * @param persistence 存储存根
     * @return API Key 服务
     */
    private static ApiKeyService newApiKeyService(final CapturingPersistenceService persistence) {
        final ApiKeyService apiKeyService = new ApiKeyService(null, null, null);
        ReflectionTestUtils.setField(apiKeyService, "apiKeyPersistenceService", persistence);

        final ApiKey apiKey = ApiKey.builder()
                .keyId(KEY_ID)
                .usage(UsageStatistics.builder().dailyTokenUsage(new HashMap<>()).build())
                .build();
        @SuppressWarnings("unchecked")
        final Map<String, ApiKey> apiKeyCache =
                (Map<String, ApiKey>) ReflectionTestUtils.getField(apiKeyService, "apiKeyCache");
        apiKeyCache.put(KEY_HASH, apiKey);
        @SuppressWarnings("unchecked")
        final Map<String, String> keyIdIndex =
                (Map<String, String>) ReflectionTestUtils.getField(apiKeyService, "keyIdIndex");
        keyIdIndex.put(KEY_ID, KEY_HASH);
        return apiKeyService;
    }
}
