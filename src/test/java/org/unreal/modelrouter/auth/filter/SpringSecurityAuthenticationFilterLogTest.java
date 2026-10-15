package org.unreal.modelrouter.auth.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.auth.security.config.properties.ApiKeyConfig;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * issue #420 ②：认证过滤器的**日志口径** —— 限流拒绝（429）不得被记成「认证失败」+ 栈。
 *
 * <p><b>改前</b>：{@code handleAuthenticationError} 第一行恒为
 * {@code log.warn("认证失败: {}", path, throwable)} ⇒ 限流拒绝与真正的认证失败同文案、同级别、同栈。
 * 危害是**口径**而非响应：按「认证失败」文案做的告警/统计把限流计入认证失败，且高频限流按「每次一条栈」
 * 放大日志量。</p>
 *
 * <p><b>本类断言面</b>（响应断言留在 {@link SpringSecurityAuthenticationFilterRateLimitTest}，两者互补）：</p>
 * <ul>
 *   <li>LOG-001：429 ⇒ 无任何「认证失败」事件，且限流事件**不带栈**（{@code getThrowableProxy() == null}），
 *       文案点明 429 与码；</li>
 *   <li>LOG-002：401（{@code INVALID_API_KEY}）⇒ 仍是「认证失败」WARN 且**带栈** —— 既有口径一字不动；</li>
 *   <li>LOG-003：码是限流码但状态在构造时掉了（半边异常）⇒ 同样不记成「认证失败」（判据含码，不只含状态）。</li>
 * </ul>
 *
 * <p><b>issue #431 增补（multipart 分支的外层 {@code onErrorResume}）</b>：multipart 请求有内层/外层
 * 两层 {@code onErrorResume}，外层只在「{@code convert()} 以 {@code Mono.error} 返回认证异常」或
 * 「内层映射函数同步抛出」时才会拿到 429（内层包在 {@code flatMap} 映射函数内部，拿不到同步抛出）。
 * 外层此前自带旧文案 {@code "Multipart请求认证过程中发生认证异常"} + 栈 ⇒ 同样要并入口径：</p>
 * <ul>
 *   <li>MLOG-001：内层映射函数**同步抛出** 429 ⇒ 外层兜底，不得打成「认证异常/认证失败」、不得带栈，
 *       且响应仍是 429 + 原 errorCode；</li>
 *   <li>MLOG-002：{@code convert()} 以 {@code Mono.error} 返回 429 ⇒ 同一条外层的另一条触发路径，断言同上；</li>
 *   <li>MLOG-003：同一外层路径上的 401 ⇒ 必须仍有一条带栈的「认证失败」WARN（可诊断性不得无谓丢失）。</li>
 * </ul>
 *
 * @author JAiRouter Team
 * @since v3.3.5
 */
@DisplayName("issue #420 / #431: 认证过滤器日志口径（限流不记成认证失败）")
class SpringSecurityAuthenticationFilterLogTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String PATH = "/v1/chat/completions";

    private static final String RATE_LIMITED_MESSAGE = "请求频率超过限制，请稍后再试";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Logger logger;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        logger = (Logger) LoggerFactory.getLogger(SpringSecurityAuthenticationFilter.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    @DisplayName("LOG-001: 429 ⇒ 不打出「认证失败」，限流 WARN 不带栈且点明状态与码")
    void rateLimited_shouldNotBeLoggedAsAuthenticationFailure() {
        final AuthenticationException rateLimited = new AuthenticationException(
                "请求频率超过限制，请稍后再试",
                ErrorCode.RATE_LIMITED_API_KEY.code(),
                HttpStatus.TOO_MANY_REQUESTS);

        final MockServerWebExchange exchange = exchangeOf(PATH);
        filterWith(rateLimited).filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode(),
                "响应状态不受日志口径变更影响");
        assertTrue(warnEvents().stream().noneMatch(e -> e.getFormattedMessage().contains("认证失败")),
                "限流拒绝不得被记成「认证失败」：" + messages());
        assertTrue(warnEvents().stream().anyMatch(e -> e.getFormattedMessage().contains("限流")
                        && e.getFormattedMessage().contains("429")
                        && e.getFormattedMessage().contains(ErrorCode.RATE_LIMITED_API_KEY.code())),
                "限流必须仍有可检索的日志，且点明状态与码：" + messages());
        assertTrue(warnEvents().stream().allMatch(e -> e.getThrowableProxy() == null),
                "限流日志不得带栈（高频限流会放大日志量）：" + messages());
    }

    @Test
    @DisplayName("LOG-002: 401 ⇒ 仍是「认证失败」WARN 且带栈（既有口径不变）")
    void unauthorized_shouldKeepTheLegacyLogWithStack() {
        final AuthenticationException unauthorized =
                new AuthenticationException("无效的API Key", ErrorCode.INVALID_API_KEY.code());

        filterWith(unauthorized).filter(exchangeOf(PATH), mockChain()).block(TIMEOUT);

        final List<ILoggingEvent> failures = warnEvents().stream()
                .filter(e -> e.getFormattedMessage().contains("认证失败"))
                .toList();
        assertEquals(1, failures.size(), "401 必须仍打一条「认证失败」WARN：" + messages());
        final ILoggingEvent event = failures.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains(PATH));
        assertNotNull(event.getThrowableProxy(), "真正的认证失败仍须带栈（排障需要）：" + messages());
    }

    @Test
    @DisplayName("LOG-003: 码是限流码但状态丢了（默认 401）⇒ 也不记成「认证失败」")
    void rateLimitCodeWithoutStatus_shouldNotBeLoggedAsAuthenticationFailure() {
        // 判据必须含码：否则「码对、状态在构造时掉了」这类半边异常又会被记成认证失败
        final AuthenticationException halfBroken =
                new AuthenticationException("请求频率超过限制", ErrorCode.RATE_LIMITED_API_KEY.code());

        filterWith(halfBroken).filter(exchangeOf(PATH), mockChain()).block(TIMEOUT);

        assertTrue(warnEvents().stream().noneMatch(e -> e.getFormattedMessage().contains("认证失败")),
                "限流码即便没带 429 状态也不得记成「认证失败」：" + messages());
    }

    // ==================== issue #431：multipart 分支的外层 onErrorResume ====================

    @Test
    @DisplayName("MLOG-001: multipart 外层兜底（内层映射函数同步抛出）的 429 ⇒ 不记成认证异常、不带栈、响应仍 429")
    void multipartOuterFallback_synchronousThrow429_shouldNotBeLoggedAsAuthenticationFailure() throws Exception {
        // 触发手法：authenticate() 不是返回 Mono.error 而是**同步抛出** —— 内层 onErrorResume 包在
        // flatMap 的映射函数内部，同步抛出时它根本没被装配进错误链，异常只能落到外层兜底。
        // 这正是 #431 的触发面（#420 只改了 handleAuthenticationError 的入口日志）。
        final MockServerWebExchange exchange = multipartExchangeOf(PATH);

        filterThrowingOnAuthenticate(rateLimitedException()).filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode(),
                "响应状态不受日志口径变更影响");
        final JsonNode error = objectMapper.readTree(bodyText(exchange)).path("error");
        assertEquals(ErrorCode.RATE_LIMITED_API_KEY.code(), error.path("code").asText(),
                "errorCode 不得变: " + error);
        assertEquals(RATE_LIMITED_MESSAGE, error.path("message").asText(), "错误消息不得变: " + error);
        assertNoAuthenticationFailureWording();
        assertSingleRateLimitEventWithoutStack();
    }

    @Test
    @DisplayName("MLOG-002: multipart 外层兜底（convert() 返回 Mono.error）的 429 ⇒ 同样不记成认证异常、不带栈")
    void multipartOuterFallback_converterError429_shouldNotBeLoggedAsAuthenticationFailure() throws Exception {
        // 外层的另一条触发路径：convert() 自身以 Mono.error 返回认证异常（内层只管 authenticate 之后）。
        final MockServerWebExchange exchange = multipartExchangeOf(PATH);

        filterWithConverterError(rateLimitedException()).filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
        final JsonNode error = objectMapper.readTree(bodyText(exchange)).path("error");
        assertEquals(ErrorCode.RATE_LIMITED_API_KEY.code(), error.path("code").asText(), "errorCode 不得变: " + error);
        assertNoAuthenticationFailureWording();
        assertSingleRateLimitEventWithoutStack();
    }

    @Test
    @DisplayName("MLOG-003: 同一外层兜底路径上的 401 ⇒ 仍有一条带栈的「认证失败」WARN（可诊断性不丢）")
    void multipartOuterFallback_unauthorized_shouldStayDiagnosable() {
        final AuthenticationException unauthorized =
                new AuthenticationException("无效的API Key", ErrorCode.INVALID_API_KEY.code());
        final MockServerWebExchange exchange = multipartExchangeOf(PATH);

        filterThrowingOnAuthenticate(unauthorized).filter(exchange, mockChain()).block(TIMEOUT);

        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        final List<ILoggingEvent> failures = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("认证失败"))
                .toList();
        assertEquals(1, failures.size(), "外层兜底的非限流认证失败必须仍打一条「认证失败」WARN（且只一条）："
                + messages());
        final ILoggingEvent event = failures.get(0);
        assertEquals(Level.WARN, event.getLevel());
        assertTrue(event.getFormattedMessage().contains(PATH), ": " + messages());
        assertNotNull(event.getThrowableProxy(), "真正的认证失败仍须带栈（排障需要）：" + messages());
    }

    @Test
    @DisplayName("MLOG-004: multipart 外层的**非认证类**异常 ⇒ 文案不含「认证异常」子串，仍保留 ERROR + 栈")
    void multipartOuterFallback_nonAuthException_shouldNotUseAuthenticationWording() {
        // issue #435：外层还有一条「非认证异常」分支，其文案原先含「认证异常」子串 —— 按子串匹配的
        // 告警/统计会把非本仓异常（下例即下游抛出的 429）误计成认证异常。
        final RuntimeException downstream =
                new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "下游限流");
        final MockServerWebExchange exchange = multipartExchangeOf(PATH);

        try {
            // 非认证类异常沿用原语义：向上抛（本用例只关心日志口径）
            filterThrowingOnAuthenticate(downstream).filter(exchange, mockChain()).block(TIMEOUT);
        } catch (RuntimeException expected) {
            // 预期路径
        }

        assertNoAuthenticationFailureWording();
        final List<ILoggingEvent> errors = appender.list.stream()
                .filter(e -> e.getLevel() == Level.ERROR)
                .toList();
        assertEquals(1, errors.size(), "非认证类异常仍必须恰好一条 ERROR（可诊断性不丢）：" + messages());
        final ILoggingEvent event = errors.get(0);
        assertTrue(event.getFormattedMessage().contains("非认证类异常"), ": " + messages());
        assertNotNull(event.getThrowableProxy(), "非认证类异常仍须带栈（排障需要）：" + messages());
    }

    // ==================== 辅助方法 ====================

    private AuthenticationException rateLimitedException() {
        return new AuthenticationException(RATE_LIMITED_MESSAGE,
                ErrorCode.RATE_LIMITED_API_KEY.code(), HttpStatus.TOO_MANY_REQUESTS);
    }

    /** 外层兜底路径的 429 不得落在旧文案上（#420 教训：子串匹配的告警会命中「非认证失败」这类写法）。 */
    private void assertNoAuthenticationFailureWording() {
        assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("认证失败")),
                "外层兜底的 429 不得落在「认证失败」文案里：" + messages());
        assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("认证异常")),
                "外层兜底的 429 不得落在「认证异常」文案里：" + messages());
    }

    private void assertSingleRateLimitEventWithoutStack() {
        final List<ILoggingEvent> limited = appender.list.stream()
                .filter(e -> e.getFormattedMessage().contains("限流"))
                .toList();
        assertEquals(1, limited.size(), "外层兜底的限流仍必须恰好有一条可检索日志：" + messages());
        final ILoggingEvent event = limited.get(0);
        assertEquals(Level.WARN, event.getLevel(), "限流是可行动信号，不得降级：" + messages());
        assertTrue(event.getFormattedMessage().contains("429"), ": " + messages());
        assertTrue(event.getFormattedMessage().contains(ErrorCode.RATE_LIMITED_API_KEY.code()), ": " + messages());
        assertNull(event.getThrowableProxy(), "限流日志不得带栈（高频限流会放大日志量）：" + messages());
    }

    private List<ILoggingEvent> warnEvents() {
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    private List<String> messages() {
        return appender.list.stream()
                .map(e -> e.getLevel() + " " + e.getFormattedMessage()
                        + (e.getThrowableProxy() == null ? "" : " [+stack]"))
                .toList();
    }

    private SpringSecurityAuthenticationFilter filterWith(final AuthenticationException error) {
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        final ReactiveAuthenticationManager manager = mock(ReactiveAuthenticationManager.class);
        when(converter.convert(any()))
                .thenReturn(Mono.just(new UsernamePasswordAuthenticationToken("user", "creds")));
        when(manager.authenticate(any())).thenReturn(Mono.error(error));
        return filterOf(converter, manager);
    }

    /** 认证管理器**同步抛出**（而非返回 {@code Mono.error}）⇒ 只有 multipart 的外层 onErrorResume 能兜住。 */
    private SpringSecurityAuthenticationFilter filterThrowingOnAuthenticate(final AuthenticationException error) {
        return filterThrowingOnAuthenticate((RuntimeException) error);
    }

    /** 同上，但抛的是**非认证类**异常 ⇒ 走 multipart 外层的「非认证类异常」分支（issue #435）。 */
    private SpringSecurityAuthenticationFilter filterThrowingOnAuthenticate(final RuntimeException error) {
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        final ReactiveAuthenticationManager manager = mock(ReactiveAuthenticationManager.class);
        when(converter.convert(any()))
                .thenReturn(Mono.just(new UsernamePasswordAuthenticationToken("user", "creds")));
        when(manager.authenticate(any())).thenThrow(error);
        return filterOf(converter, manager);
    }

    /** 内层覆盖不到 convert() ⇒ 认证异常由外层兜底。 */
    private SpringSecurityAuthenticationFilter filterWithConverterError(final AuthenticationException error) {
        final ServerAuthenticationConverter converter = mock(ServerAuthenticationConverter.class);
        when(converter.convert(any())).thenReturn(Mono.error(error));
        return filterOf(converter, mock(ReactiveAuthenticationManager.class));
    }

    private SpringSecurityAuthenticationFilter filterOf(final ServerAuthenticationConverter converter,
                                                        final ReactiveAuthenticationManager manager) {
        final SecurityProperties props = mock(SecurityProperties.class);
        final ApiKeyConfig apiKeyConfig = mock(ApiKeyConfig.class);
        final JwtConfig jwtConfig = mock(JwtConfig.class);
        when(props.getApiKey()).thenReturn(apiKeyConfig);
        when(props.getJwt()).thenReturn(jwtConfig);
        when(apiKeyConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.isEnabled()).thenReturn(true);
        return new SpringSecurityAuthenticationFilter(props, converter, manager);
    }

    private WebFilterChain mockChain() {
        final WebFilterChain chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
        return chain;
    }

    private MockServerWebExchange exchangeOf(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path).build());
    }

    private MockServerWebExchange multipartExchangeOf(final String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.post(path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .build());
    }

    private String bodyText(final MockServerWebExchange exchange) {
        final DataBuffer buffer = exchange.getResponse().getBody().blockLast(TIMEOUT);
        assertNotNull(buffer, "响应体不应为空");
        final byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
