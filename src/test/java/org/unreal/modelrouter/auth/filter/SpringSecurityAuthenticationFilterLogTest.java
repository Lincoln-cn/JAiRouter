package org.unreal.modelrouter.auth.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.auth.security.config.properties.ApiKeyConfig;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.common.constants.ErrorCode;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * @author JAiRouter Team
 * @since v3.3.5
 */
@DisplayName("issue #420: 认证过滤器日志口径（限流不记成认证失败）")
class SpringSecurityAuthenticationFilterLogTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private static final String PATH = "/v1/chat/completions";

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

    // ==================== 辅助方法 ====================

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
}
