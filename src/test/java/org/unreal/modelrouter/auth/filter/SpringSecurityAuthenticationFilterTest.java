package org.unreal.modelrouter.auth.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.auth.security.config.properties.ApiKeyConfig;
import org.unreal.modelrouter.auth.security.config.properties.JwtConfig;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.common.exception.AuthenticationException;
import org.unreal.modelrouter.common.exception.SecurityAuthenticationException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SpringSecurityAuthenticationFilter 单元测试
 *
 * @author JAiRouter Team
 * @since 2.0.0
 */
@DisplayName("SpringSecurityAuthenticationFilter 测试")
class SpringSecurityAuthenticationFilterTest {

    private SecurityProperties securityProperties;
    private ApiKeyConfig apiKeyConfig;
    private JwtConfig jwtConfig;
    private ServerAuthenticationConverter authenticationConverter;
    private ReactiveAuthenticationManager authenticationManager;
    private SpringSecurityAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        securityProperties = mock(SecurityProperties.class);
        apiKeyConfig = mock(ApiKeyConfig.class);
        jwtConfig = mock(JwtConfig.class);
        authenticationConverter = mock(ServerAuthenticationConverter.class);
        authenticationManager = mock(ReactiveAuthenticationManager.class);

        // 默认启用 API Key 和 JWT
        when(securityProperties.getApiKey()).thenReturn(apiKeyConfig);
        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(apiKeyConfig.isEnabled()).thenReturn(true);
        when(jwtConfig.isEnabled()).thenReturn(true);

        filter = new SpringSecurityAuthenticationFilter(
                securityProperties, authenticationConverter, authenticationManager);
    }

    @Nested
    @DisplayName("排除路径测试")
    class ExcludedPathTests {

        @Test
        @DisplayName("FILT-008: 排除路径 - actuator路径跳过认证")
        void testActuatorPathExcluded() {
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/actuator/health")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any());
            verifyNoInteractions(authenticationConverter);
        }

        @Test
        @DisplayName("FILT-009: 排除路径 - 登录路径跳过认证")
        void testLoginPathExcluded() {
            // 使用实际配置中的登录路径
            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/auth/jwt/login")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any());
            verifyNoInteractions(authenticationConverter);
        }

        @Test
        @DisplayName("FILT-010: R3-P1 默认 swagger 不免认证（docs-public=false）")
        void testSwaggerPathRequiresAuthByDefault() {
            org.unreal.modelrouter.auth.security.config.ExcludedPathsConfig.setApiDocsAuthExcluded(false);
            when(authenticationConverter.convert(any())).thenReturn(Mono.empty());
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/swagger-ui/index.html")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            // 默认需认证：会走 converter（缺凭证则 401，此处 converter 返回 empty）
            verify(authenticationConverter, atLeastOnce()).convert(any());
            org.unreal.modelrouter.auth.security.config.ExcludedPathsConfig.setApiDocsAuthExcluded(false);
        }

        @Test
        @DisplayName("FILT-010b: docs-public=true 时 swagger 免认证")
        void testSwaggerPathExcludedWhenDocsPublic() {
            org.unreal.modelrouter.auth.security.config.ExcludedPathsConfig.setApiDocsAuthExcluded(true);
            MockServerHttpRequest request = MockServerHttpRequest
                    .get("/swagger-ui/index.html")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any());
            verifyNoInteractions(authenticationConverter);
            org.unreal.modelrouter.auth.security.config.ExcludedPathsConfig.setApiDocsAuthExcluded(false);
        }
    }

    @Nested
    @DisplayName("认证禁用测试")
    class AuthenticationDisabledTests {

        @Test
        @DisplayName("FILT-011: 认证禁用 - API Key和JWT都禁用时跳过认证")
        void testAuthenticationDisabled() {
            when(apiKeyConfig.isEnabled()).thenReturn(false);
            when(jwtConfig.isEnabled()).thenReturn(false);

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any());
            verifyNoInteractions(authenticationConverter);
        }
    }

    @Nested
    @DisplayName("认证流程测试")
    class AuthenticationFlowTests {

        @Test
        @DisplayName("FILT-012: 认证流程 - 认证成功")
        void testAuthenticationSuccess() {
            Authentication authentication = new UsernamePasswordAuthenticationToken("test-user", null);
            when(authenticationConverter.convert(any())).thenReturn(Mono.just(authentication));
            when(authenticationManager.authenticate(any())).thenReturn(Mono.just(authentication));

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .header("X-API-Key", "test-key")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(authenticationConverter).convert(any());
            verify(authenticationManager).authenticate(any());
        }

        @Test
        @DisplayName("FILT-013: 认证流程 - 缺少认证信息返回401")
        void testMissingAuthentication() {
            when(authenticationConverter.convert(any())).thenReturn(Mono.empty());

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }

        @Test
        @DisplayName("FILT-014: 认证流程 - 认证失败返回401")
        void testAuthenticationFailure() {
            Authentication authentication = new UsernamePasswordAuthenticationToken("user", "creds");
            when(authenticationConverter.convert(any())).thenReturn(Mono.just(authentication));
            when(authenticationManager.authenticate(any()))
                    .thenReturn(Mono.error(new AuthenticationException("认证失败", "AUTH_FAILED")));

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .header("X-API-Key", "invalid-key")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }

        @Test
        @DisplayName("FILT-014b: 认证流程 - SecurityAuthenticationException处理")
        void testSecurityAuthenticationException() {
            Authentication authentication = new UsernamePasswordAuthenticationToken("user", "creds");
            when(authenticationConverter.convert(any())).thenReturn(Mono.just(authentication));
            when(authenticationManager.authenticate(any()))
                    .thenReturn(Mono.error(new SecurityAuthenticationException("Token expired", "TOKEN_EXPIRED")));

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/chat")
                    .header("X-API-Key", "expired-key")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }
    }

    @Nested
    @DisplayName("Multipart请求测试")
    class MultipartRequestTests {

        @Test
        @DisplayName("FILT-015: Multipart请求 - 正常处理")
        void testMultipartRequestHandling() {
            Authentication authentication = new UsernamePasswordAuthenticationToken("test-user", null);
            when(authenticationConverter.convert(any())).thenReturn(Mono.just(authentication));
            when(authenticationManager.authenticate(any())).thenReturn(Mono.just(authentication));

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("X-API-Key", "test-key")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(authenticationConverter).convert(any());
            verify(authenticationManager).authenticate(any());
        }

        @Test
        @DisplayName("FILT-015b: Multipart请求 - 缺少认证返回401")
        void testMultipartMissingAuth() {
            when(authenticationConverter.convert(any())).thenReturn(Mono.empty());

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        }
    }

    @Nested
    @DisplayName("认证成功不得写出伪 401（issue #74 回归）")
    class NoSpurious401OnSuccessTests {

        /**
         * issue #74 根因回归：{@code switchIfEmpty} 曾挂在包含 {@code chain.filter(exchange)}
         * 的外层 Mono 上，而 chain 返回的 {@code Mono<Void>} 只 complete empty、永不 emit 值，
         * 故认证成功的请求也会命中"缺少认证信息"分支写出 401。原有用例只断言"链路被调用"，
         * 未断言响应状态，因此该缺陷长期未被测出。
         */
        @Test
        @DisplayName("FILT-016: /v1 认证成功 → 响应不得是 401，且链路必须放行")
        void testSuccessDoesNotWrite401OnV1Path() {
            Authentication authentication = new UsernamePasswordAuthenticationToken("admin", null);
            when(authenticationConverter.convert(any())).thenReturn(Mono.just(authentication));
            when(authenticationManager.authenticate(any())).thenReturn(Mono.just(authentication));

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/v1/chat/completions")
                    .header("Jairouter_Token", "valid-jwt")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            // 模拟真实链路：Mono<Void> 只 complete empty、永不 emit 值
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any());
            assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode(),
                    "认证成功的请求不得被伪 401 覆盖");
            // MockServerHttpResponse 在"未写入任何内容"时 getBodyAsString() 抛 IllegalStateException，
            // 据此断言认证成功路径没有写出认证错误响应体。
            assertThrows(IllegalStateException.class,
                    () -> exchange.getResponse().getBodyAsString().block(),
                    "认证成功不得写出认证错误响应体");
        }

        @Test
        @DisplayName("FILT-017: multipart 认证成功 → 响应不得是 401")
        void testSuccessDoesNotWrite401OnMultipart() {
            Authentication authentication = new UsernamePasswordAuthenticationToken("admin", null);
            when(authenticationConverter.convert(any())).thenReturn(Mono.just(authentication));
            when(authenticationManager.authenticate(any())).thenReturn(Mono.just(authentication));

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/api/v1/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Jairouter_Token", "valid-jwt")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);
            when(chain.filter(any())).thenReturn(Mono.empty());

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            verify(chain).filter(any());
            assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode(),
                    "multipart 认证成功不得被伪 401 覆盖");
        }

        @Test
        @DisplayName("FILT-018: 凭据缺失仍必须返回 401（负向路径不可回归）")
        void testMissingCredentialsStillReturns401() {
            when(authenticationConverter.convert(any())).thenReturn(Mono.empty());

            MockServerHttpRequest request = MockServerHttpRequest
                    .post("/v1/chat/completions")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            WebFilterChain chain = mock(WebFilterChain.class);

            Mono<Void> result = filter.filter(exchange, chain);

            StepVerifier.create(result).verifyComplete();
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
            verifyNoInteractions(chain);
        }
    }
}
