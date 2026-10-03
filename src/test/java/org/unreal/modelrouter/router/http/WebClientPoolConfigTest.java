package org.unreal.modelrouter.router.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link WebClientPool} 连接参数外部化的单元测试（#182）。
 *
 * <p>要点是「不改配置时行为不变」：无参构造器的取值必须与外部化之前的硬编码值一致，
 * 否则默认行为会悄悄改变。</p>
 *
 * @author JAiRouter Team
 * @since 3.2.8
 */
@DisplayName("WebClientPool 连接参数外部化")
class WebClientPoolConfigTest {

    private static Object field(final WebClientPool pool, final String name) throws Exception {
        final Field f = WebClientPool.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(pool);
    }

    @Test
    @DisplayName("无参构造器使用与外部化前一致的默认值")
    void defaultConstructorKeepsPreExternalizationValues() throws Exception {
        final WebClientPool pool = new WebClientPool();

        assertEquals(WebClientPool.DEFAULT_CONNECT_TIMEOUT_MS, field(pool, "connectTimeoutMs"),
                "连接超时默认值不得因外部化而改变");
        assertEquals(WebClientPool.DEFAULT_RESPONSE_TIMEOUT_SECONDS, field(pool, "responseTimeoutSeconds"),
                "响应超时默认值不得因外部化而改变");
        assertEquals(10_000, WebClientPool.DEFAULT_CONNECT_TIMEOUT_MS);
        assertEquals(60, WebClientPool.DEFAULT_RESPONSE_TIMEOUT_SECONDS);
    }

    @Test
    @DisplayName("显式传入的参数被真正采纳")
    void explicitValuesAreHonoured() throws Exception {
        final WebClientPool pool = new WebClientPool(1234, 7, 5, 4321L, 9);

        assertEquals(1234, field(pool, "connectTimeoutMs"));
        assertEquals(7, field(pool, "responseTimeoutSeconds"));
        assertNotNull(field(pool, "connectionProvider"), "应构建显式连接池而不是依赖默认");
    }

    @Test
    @DisplayName("显式连接池下仍能正常创建并缓存 WebClient")
    void createsAndCachesClientWithExplicitPool() {
        final WebClientPool pool = new WebClientPool(2_000, 30, 8, 5_000L, 30);

        assertNotNull(pool.getOrCreate("https://example.com"));
        assertNotNull(pool.getOrCreate("https://example.com"), "第二次应命中缓存");
        assertEquals(1, pool.getStats().size(), "相同 baseUrl 不应重复建池");
    }

    @Test
    @DisplayName("dispose 幂等：重复调用不抛异常，并清空缓存")
    void disposeIsIdempotentAndClearsCache() {
        final WebClientPool pool = new WebClientPool();
        pool.getOrCreate("https://example.com");

        pool.dispose();
        pool.dispose();

        assertEquals(0, pool.getStats().size(), "释放后缓存应已清空");
    }
}
