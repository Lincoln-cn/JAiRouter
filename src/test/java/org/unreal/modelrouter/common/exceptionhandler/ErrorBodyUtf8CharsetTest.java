package org.unreal.modelrouter.common.exceptionhandler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.auth.filter.SpringSecurityAuthenticationFilter;
import org.unreal.modelrouter.auth.security.config.properties.ApiKeyConfig;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.ratelimit.AdminApiRateLimiter;
import org.unreal.modelrouter.common.exception.SecurityException;
import org.unreal.modelrouter.monitor.monitoring.error.ErrorTracker;
import org.unreal.modelrouter.monitor.tracing.logger.StructuredLogger;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * v3.1 PR-4d.1：错误体写出点的字符集回归.
 *
 * <p>此前三个错误体写出点用平台默认字符集（{@code String#getBytes()}）把 JSON 写进
 * {@code DataBuffer}：{@link ReactiveGlobalExceptionHandler}（全局异常处理器）、
 * {@code AdminApiRateLimiter}（管理接口 429）与 {@code SpringSecurityAuthenticationFilter}（401）。
 * 这些错误消息含中文（「认证失败」「数据处理失败」「请求过于频繁…」），在默认字符集非 UTF-8 的
 * 机器上会乱码。现在一律显式 {@link StandardCharsets#UTF_8}。</p>
 *
 * <p>断言直接比较<b>原始字节</b>（不经任何解码），因此既能证明「按 UTF-8 解码可读」，也能证明
 * 「字节与 UTF-8 一致」：把本类放在 {@code -Dfile.encoding=GBK} 等非 UTF-8 默认字符集下运行，
 * 未修复的实现会因写出 GBK 字节而失败，修复后仍全绿。</p>
 *
 * @author JAiRouter Team
 * @since v3.1
 */
@DisplayName("v3.1 PR-4d.1: 错误体 UTF-8 字符集")
class ErrorBodyUtf8CharsetTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String API_PATH = "/api/v1/chat/completions";

    private static final String CHINESE_MESSAGE = "认证失败：密钥无效";

    @Test
    @DisplayName("ReactiveGlobalExceptionHandler：中文错误消息以 UTF-8 字节写出（/api 契约与状态码不变）")
    void reactiveGlobalExceptionHandler_shouldWriteUtf8Bytes() {
        final ReactiveGlobalExceptionHandler handler =
                new ReactiveGlobalExceptionHandler(new ErrorTracker(mock(StructuredLogger.class)));
        final MockServerWebExchange exchange = exchangeOf(API_PATH);
        final SecurityException error =
                new SecurityException(CHINESE_MESSAGE, "AUTH_001", HttpStatus.UNAUTHORIZED);

        handler.handle(exchange, error).block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        final byte[] bytes = bodyBytes(exchange);
        final String body = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(body.contains(CHINESE_MESSAGE), "按 UTF-8 解码后应为可读中文: " + body);
        assertContainsUtf8(bytes, CHINESE_MESSAGE);
        assertNoReplacementCharacter(body);
    }

    @Test
    @DisplayName("AdminApiRateLimiter：429 中文提示以 UTF-8 字节写出（形状与状态码不变）")
    void adminApiRateLimiter_shouldWriteUtf8Bytes() {
        final AdminApiRateLimiter limiter = new AdminApiRateLimiter();
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        MockServerWebExchange exceeded = null;
        for (int i = 0; i < 11; i++) {
            final MockServerWebExchange exchange = exchangeOf("/api/auth/api-keys");
            limiter.filter(exchange, chain).block(TIMEOUT);
            if (HttpStatus.TOO_MANY_REQUESTS.equals(exchange.getResponse().getStatusCode())) {
                exceeded = exchange;
                break;
            }
        }

        assertNotNull(exceeded, "创建额度（每小时 10 次）应在第 11 次请求上触发 429");
        final byte[] bytes = bodyBytes(exceeded);
        final String body = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(body.contains("创建操作过于频繁，请稍后再试"), "按 UTF-8 解码后应为可读中文: " + body);
        assertContainsUtf8(bytes, "创建操作过于频繁，请稍后再试");
        assertNoReplacementCharacter(body);
    }

    @Test
    @DisplayName("SpringSecurityAuthenticationFilter：401 中文提示以 UTF-8 字节写出")
    void springSecurityAuthenticationFilter_shouldWriteUtf8Bytes() {
        final SecurityProperties securityProperties = mock(SecurityProperties.class);
        final ApiKeyConfig apiKeyConfig = mock(ApiKeyConfig.class);
        final JwtConfig jwtConfig = mock(JwtConfig.class);
        when(securityProperties.getApiKey()).thenReturn(apiKeyConfig);
        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(apiKeyConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.isEnabled()).thenReturn(true);

        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.empty());
        final SpringSecurityAuthenticationFilter filter = new SpringSecurityAuthenticationFilter(
                securityProperties, converter, mock(ReactiveAuthenticationManager.class));

        final MockServerWebExchange exchange = exchangeOf(API_PATH);
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());

        filter.filter(exchange, chain).block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        final byte[] bytes = bodyBytes(exchange);
        final String body = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(body.contains("请求缺少认证信息，请提供API Key或JWT Token"),
                "按 UTF-8 解码后应为可读中文: " + body);
        assertContainsUtf8(bytes, "请求缺少认证信息，请提供API Key或JWT Token");
        assertNoReplacementCharacter(body);
    }

    /**
     * 构建真实 WebFlux 交换对象.
     *
     * @param path 请求路径
     * @return 交换对象
     */
    private static MockServerWebExchange exchangeOf(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    /**
     * 读取响应体的原始字节（不经任何字符集解码）.
     *
     * @param exchange 交换对象
     * @return 已写出的字节
     */
    private static byte[] bodyBytes(final MockServerWebExchange exchange) {
        final DataBuffer buffer = exchange.getResponse().getBody().blockLast(TIMEOUT);
        assertNotNull(buffer, "响应体不应为空");
        final byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }

    /**
     * 断言原始字节中包含 UTF-8 编码的期望片段（字节级一致，不依赖平台默认字符集）.
     *
     * @param bytes    响应体原始字节（只能读取一次，故由调用方传入）
     * @param expected 期望出现的中文片段
     */
    private static void assertContainsUtf8(final byte[] bytes, final String expected) {
        final byte[] needle = expected.getBytes(StandardCharsets.UTF_8);
        assertTrue(indexOf(bytes, needle) >= 0,
                "响应字节必须包含「" + expected + "」的 UTF-8 字节序列（实际 " + bytes.length + " 字节）");
    }

    /**
     * 断言按 UTF-8 解码后不出现替换字符（乱码标志）.
     *
     * @param body 已按 UTF-8 解码的文本
     */
    private static void assertNoReplacementCharacter(final String body) {
        assertTrue(body.indexOf('\uFFFD') < 0, "按 UTF-8 解码不应出现乱码替换字符: " + body);
    }

    /**
     * 子字节序列查找（避免引入额外依赖）.
     *
     * @param haystack 被查找的字节
     * @param needle   目标字节序列
     * @return 首次出现的下标，未找到为 -1
     */
    private static int indexOf(final byte[] haystack, final byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
