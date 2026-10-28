package org.unreal.modelrouter.router.adapter.test;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.unreal.modelrouter.router.adapter.AdapterRegistry;
import org.unreal.modelrouter.router.http.WebClientPool;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * #702：适配器测试接口必须经 {@link WebClientPool} 发出请求。
 *
 * <p>连接期 SSRF 守卫（{@code SsrfGuardedAddressResolverGroup}）只装在池里。以前这里自己
 * {@code webClientBuilder.baseUrl(...).build()}，只有建连前的一次性解析校验，DNS 重绑定可以绕过。</p>
 */
@DisplayName("#702 适配器测试走连接期 SSRF 守卫")
class AdapterTestServiceSsrfGuardTest {

    private static final String BASE_URL = "https://api.example.com";

    private record Fixture(AdapterTestService service, WebClientPool pool) {
    }

    private Fixture newFixture() {
        final WebClientPool pool = mock(WebClientPool.class);
        final WebClient client = mock(WebClient.class);
        final WebClient.RequestHeadersUriSpec uriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        final WebClient.RequestHeadersSpec headersSpec = mock(WebClient.RequestHeadersSpec.class);
        final WebClient.ResponseSpec responseSpec = mock(WebClient.ResponseSpec.class);

        when(pool.getOrCreate(eq(BASE_URL), eq(AdapterTestService.TEST_CLIENT_VARIANT), isNull()))
                .thenReturn(client);
        when(client.get()).thenReturn(uriSpec);
        when(uriSpec.uri(anyString())).thenReturn(headersSpec);
        when(headersSpec.header(anyString(), anyString())).thenReturn(headersSpec);
        when(headersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.error(new RuntimeException("测试环境无网络")));

        return new Fixture(new AdapterTestService(pool, mock(AdapterRegistry.class)), pool);
    }

    @Test
    @DisplayName("SSRF-001: PING 测试必须用池里的测试变体客户端")
    void pingGoesThroughPoolVariant() {
        final Fixture fixture = newFixture();

        StepVerifier.create(fixture.service().testPing(BASE_URL, "Authorization", "Bearer sk-test"))
                .assertNext(result -> assertNotNull(result))
                .verifyComplete();

        verify(fixture.pool()).getOrCreate(eq(BASE_URL), eq(AdapterTestService.TEST_CLIENT_VARIANT), isNull());
    }

    @Test
    @DisplayName("SSRF-002: 源码里不得再自建 WebClient（守住回归）")
    void sourceDoesNotBuildItsOwnClient() throws Exception {
        final Path source = Path.of(
                "src/main/java/org/unreal/modelrouter/router/adapter/test/AdapterTestService.java");
        assertTrue(Files.exists(source), "源码文件必须存在：" + source.toAbsolutePath());

        final String text = Files.readString(source, StandardCharsets.UTF_8);
        assertFalse(text.contains("webClientBuilder"),
                "不得再用 WebClient.Builder 自建客户端");
        assertFalse(text.contains("lgtm[java/ssrf]"),
                "lgtm 抑制注释说明这里曾绕过了 SSRF 守卫，修好后应删掉");
        assertTrue(text.contains("webClientPool.getOrCreate("),
                "必须经 WebClientPool 取客户端");
    }
}
