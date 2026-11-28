package org.unreal.modelrouter.router.model;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.common.util.ApplicationContextProvider;
import org.unreal.modelrouter.router.http.WebClientPool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@link WebClientCacheManager} 的流式 / 非流式分流守卫。
 *
 * <p>背景（P1 审计发现）：追踪工厂不可用时的回退分支原先一律取默认变体的 WebClient，
 * 于是 SSE 流式请求的传输层超时从 600s 掉回 60s —— 长回答在中途被掐断，且这条降级
 * 没有任何日志。修复后回退路径按 {@code streaming} 选变体，缓存条目也随之分开。</p>
 *
 * <p>本测试守住「分开缓存」这一半：流式与非流式必须拿到不同的实例，否则谁先创建
 * 谁就决定了另一方的超时。</p>
 *
 * @author JAiRouter Team
 */
@DisplayName("WebClientCacheManager 的流式变体分流")
class WebClientCacheManagerStreamingTest {

    private static final String BASE_URL = "http://localhost:8080";

    private ApplicationContextProvider contextProvider;
    private WebClientCacheManager manager;

    @BeforeEach
    void setUp() {
        // 极简上下文：只注册回退路径需要的 WebClientPool，追踪工厂刻意缺席，
        // 从而走「追踪不可用」的降级分支（即本次修复的那条路径）
        final GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(WebClientPool.class, WebClientPool::new);
        context.refresh();
        contextProvider = new ApplicationContextProvider();
        contextProvider.setApplicationContext(context);
        manager = new WebClientCacheManager();
    }

    @AfterEach
    void tearDown() {
        contextProvider.setApplicationContext(null);
        manager.clear();
    }

    @Test
    @DisplayName("流式与非流式持有不同的缓存条目（超时互不覆盖）")
    void streamingAndNonStreamingAreCachedSeparately() {
        final WebClient plain = manager.getOrCreate(BASE_URL, false);
        final WebClient streaming = manager.getOrCreate(BASE_URL, true);

        assertNotSame(plain, streaming, "流式与非流式必须用不同的 WebClient，否则传输超时会互相覆盖");
        assertEquals(2, manager.size(), "两种变体各占一个缓存条目");
        assertSame(plain, manager.getOrCreate(BASE_URL, false), "同变体应命中缓存");
        assertSame(streaming, manager.getOrCreate(BASE_URL, true), "同变体应命中缓存");
    }

    @Test
    @DisplayName("单参 getOrCreate 等价于非流式（既有调用方行为不变）")
    void singleArgOverloadIsNonStreaming() {
        final WebClient plain = manager.getOrCreate(BASE_URL, false);
        assertSame(plain, manager.getOrCreate(BASE_URL),
                "既有调用方（非流式）必须复用同一实例，否则每次请求都会新建连接池");
        assertEquals(1, manager.size());
    }
}
