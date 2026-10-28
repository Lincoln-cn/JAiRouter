package org.unreal.modelrouter.auth.security.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.unreal.modelrouter.auth.filter.DefaultAuthenticationConverter;
import org.unreal.modelrouter.auth.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.auth.security.config.properties.ApiKey;
import org.unreal.modelrouter.auth.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.auth.security.service.ApiKeyService;
import org.unreal.modelrouter.auth.security.service.ApiKeyValidator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证链把真实客户端 IP 传给 API Key 校验（#693）。
 *
 * <p>修复前 {@code CustomReactiveAuthenticationManager} 调的是单参 {@code validateApiKey(key)}，
 * IP 恒为 {@code null}，配置了白名单的 Key 一律 401。本测试沿真实路径
 * （{@link DefaultAuthenticationConverter} → {@link CustomReactiveAuthenticationManager}）断言
 * 客户端 IP 与请求路径确实被带到校验入口；旧实现下这两个断言会失败。</p>
 *
 * @since 3.5.5
 */
@DisplayName("#693 认证链传递客户端 IP")
class ApiKeyAuthenticationIpWiringTest {

    private static final String API_KEY = "sk-abcdefghijklmnop";
    private static final String CLIENT_IP = "10.9.8.7";

    private ApiKeyService apiKeyService;
    private DefaultAuthenticationConverter converter;
    private CustomReactiveAuthenticationManager manager;

    @BeforeEach
    void setUp() {
        final SecurityProperties properties = new SecurityProperties();
        properties.getApiKey().setEnabled(true);
        properties.getApiKey().setHeaderName("X-API-Key");
        properties.getJwt().setEnabled(false);

        apiKeyService = mock(ApiKeyService.class);
        converter = new DefaultAuthenticationConverter(properties);
        manager = new CustomReactiveAuthenticationManager(
                apiKeyService, mock(JwtTokenValidator.class), properties);
    }

    @Test
    @DisplayName("校验入口收到的是真实客户端 IP 与请求路径")
    void clientIpAndPathReachValidation() {
        final ApiKey apiKey = ApiKey.builder()
                .keyId("key-ip")
                .permissions(List.of("read"))
                .build();
        when(apiKeyService.validateApiKey(anyString(), any(), any())).thenReturn(Mono.just(apiKey));

        StepVerifier.create(converter.convert(exchange()).flatMap(manager::authenticate))
                .assertNext(authentication -> assertTrue(authentication.isAuthenticated()))
                .verifyComplete();

        final ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiKeyService).validateApiKey(keyCaptor.capture(), endpointCaptor.capture(), ipCaptor.capture());

        assertEquals(API_KEY, keyCaptor.getValue());
        assertEquals("/v1/chat/completions", endpointCaptor.getValue());
        assertEquals(CLIENT_IP, ipCaptor.getValue());
    }

    @Test
    @DisplayName("白名单判定用同一份 IP：网段内放行、网段外拒绝")
    void whitelistUsesTheSameIp() {
        final ApiKeyValidator validator = new ApiKeyValidator();
        final ApiKey apiKey = ApiKey.builder()
                .keyId("key-ip")
                .keyValue(API_KEY)
                .enabled(true)
                .allowedIpAddresses(List.of("10.9.8.0/24"))
                .build();
        final Map<String, ApiKey> cache = Map.of("key-ip", apiKey);

        assertTrue(validator.validateFully(API_KEY, cache, CLIENT_IP).isSuccess());
        assertFalse(validator.validateFully(API_KEY, cache, "10.9.9.7").isSuccess());
        assertFalse(validator.validateFully(API_KEY, cache, null).isSuccess(),
                "白名单非空且拿不到来源时必须拒绝");
    }

    private static MockServerWebExchange exchange() {
        final MockServerHttpRequest request = MockServerHttpRequest.get("/v1/chat/completions")
                .header("X-API-Key", API_KEY)
                .remoteAddress(new InetSocketAddress(CLIENT_IP, 4321))
                .build();
        return MockServerWebExchange.from(request);
    }
}
