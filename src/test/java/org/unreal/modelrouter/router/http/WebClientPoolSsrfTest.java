package org.unreal.modelrouter.router.http;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * R3-P0：出站 WebClient 收敛点 SSRF 校验。
 * WebClientPool 是所有出站 WebClient 的唯一创建入口，此处必须拦截云元数据等危险目标。
 */
@DisplayName("WebClientPool SSRF 出站门闩")
class WebClientPoolSsrfTest {

    private WebClientPool webClientPool;

    @BeforeEach
    void setUp() {
        webClientPool = new WebClientPool();
    }

    @Test
    @DisplayName("云元数据 IP 169.254.169.254 → SecurityException")
    void getOrCreate_blocksMetadataIp() {
        assertThrows(SecurityException.class,
                () -> webClientPool.getOrCreate("http://169.254.169.254"));
    }

    @Test
    @DisplayName("云元数据主机 metadata.google.internal → SecurityException")
    void getOrCreate_blocksMetadataHost() {
        assertThrows(SecurityException.class,
                () -> webClientPool.getOrCreate("http://metadata.google.internal"));
    }

    @Test
    @DisplayName("回环 127.0.0.1 仍应放行（不过度封锁）")
    void getOrCreate_allowsLoopback() {
        WebClient client = webClientPool.getOrCreate("http://127.0.0.1:8080");
        assertNotNull(client, "回环地址应正常创建 WebClient");
    }

    @Test
    @DisplayName("内网 10.0.0.1 仍应放行（适配器需连通内网 AI 服务）")
    void getOrCreate_allowsPrivateRfc1918() {
        WebClient client = webClientPool.getOrCreate("http://10.0.0.1:8080");
        assertNotNull(client, "RFC1918 内网地址应正常创建 WebClient");
    }

    @Test
    @DisplayName("带/不带尾斜杠的等价 URL 应共享同一缓存项")
    void getOrCreate_trailingSlash_shouldShareCacheEntry() {
        WebClient withoutSlash = webClientPool.getOrCreate("http://127.0.0.1:8080");
        WebClient withSlash = webClientPool.getOrCreate("http://127.0.0.1:8080/");
        assertSame(withoutSlash, withSlash, "规范化后应命中同一缓存实例");
    }
}
