package org.unreal.modelrouter.security.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.filter.filter.SpringSecurityApiKeyAuthenticationFilter;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.model.ApiKeyAuthentication;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SpringSecurityApiKeyAuthenticationFilter测试类
 */
@ExtendWith(MockitoExtension.class)
class SpringSecurityApiKeyAuthenticationFilterTest {
    
    @Mock
    private SecurityProperties securityProperties;
    
    @Mock
    private SecurityProperties.ApiKeyConfig apiKeyConfig;
    
    @Mock
    private SecurityProperties.JwtConfig jwtConfig;
    
    @Mock
    private ServerAuthenticationConverter authenticationConverter;
    
    @Mock
    private ServerAuthenticationFailureHandler failureHandler;
    
    @Mock
    private WebFilterChain filterChain;

    @Mock
    private SpringSecurityApiKeyAuthenticationFilter filter;
    
    @BeforeEach
    void setUp() {
        when(securityProperties.getApiKey()).thenReturn(apiKeyConfig);
        when(securityProperties.getJwt()).thenReturn(jwtConfig);
        when(filterChain.filter(any())).thenReturn(Mono.empty());
    }
    
    @Test
    void testFilterExcludedPath() {
        // Given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").build()
        );
        
        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证直接调用了过滤器链，没有进行认证转换
        verify(filterChain).filter(exchange);
        verify(authenticationConverter, never()).convert(any());
    }
    
    @Test
    void testFilterWithNoAuthentication() {
        // Given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/chat/completions").build()
        );
        
        when(authenticationConverter.convert(exchange)).thenReturn(Mono.empty());
        
        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 验证调用了过滤器链
        verify(filterChain).filter(exchange);
    }
    
    @Test
    void testFilterWithApiKeyAuthentication() {
        // Given
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/chat/completions").build()
        );
        
        ApiKeyAuthentication authentication = new ApiKeyAuthentication("test-api-key");
        when(authenticationConverter.convert(exchange)).thenReturn(Mono.just(authentication));
        
        // When & Then
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(authenticationConverter).convert(exchange);
    }
    
    @Test
    void testDefaultAuthenticationConverter_ExtractApiKey() {
        // Given
        when(apiKeyConfig.getHeaderName()).thenReturn("X-API-Key");
        when(jwtConfig.isEnabled()).thenReturn(false);
        
        SpringSecurityApiKeyAuthenticationFilter.DefaultAuthenticationConverter converter = 
                new SpringSecurityApiKeyAuthenticationFilter.DefaultAuthenticationConverter(securityProperties);
        
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/chat/completions")
                        .header("X-API-Key", "test-api-key")
                        .build()
        );
        
        // When & Then
        StepVerifier.create(converter.convert(exchange))
                .assertNext(auth -> {
                    assertTrue(auth instanceof ApiKeyAuthentication);
                    assertEquals("test-api-key", auth.getCredentials());
                })
                .verifyComplete();
    }
    
    @Test
    void testDefaultAuthenticationConverter_ExtractJwtToken() {
        // Given
        when(apiKeyConfig.getHeaderName()).thenReturn("X-API-Key");
        when(jwtConfig.isEnabled()).thenReturn(true);
        
        SpringSecurityApiKeyAuthenticationFilter.DefaultAuthenticationConverter converter = 
                new SpringSecurityApiKeyAuthenticationFilter.DefaultAuthenticationConverter(securityProperties);
        
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/chat/completions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jwt-token-123")
                        .build()
        );
        
        // When & Then
        StepVerifier.create(converter.convert(exchange))
                .assertNext(auth -> {
                    assertTrue(auth instanceof JwtAuthentication);
                    assertEquals("jwt-token-123", auth.getCredentials());
                })
                .verifyComplete();
    }
    
    @Test
    void testDefaultAuthenticationConverter_NoAuthentication() {
        // Given
        when(apiKeyConfig.getHeaderName()).thenReturn("X-API-Key");
        when(jwtConfig.isEnabled()).thenReturn(false);
        
        SpringSecurityApiKeyAuthenticationFilter.DefaultAuthenticationConverter converter = 
                new SpringSecurityApiKeyAuthenticationFilter.DefaultAuthenticationConverter(securityProperties);
        
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/chat/completions").build()
        );
        
        // When & Then
        StepVerifier.create(converter.convert(exchange))
                .verifyComplete();
    }
    
    @Test
    void testDefaultAuthenticationConverter_ApiKeyPriority() {
        // Given - API Key应该优先于JWT
        when(apiKeyConfig.getHeaderName()).thenReturn("X-API-Key");
        when(jwtConfig.isEnabled()).thenReturn(true);
        
        SpringSecurityApiKeyAuthenticationFilter.DefaultAuthenticationConverter converter = 
                new SpringSecurityApiKeyAuthenticationFilter.DefaultAuthenticationConverter(securityProperties);
        
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/v1/chat/completions")
                        .header("X-API-Key", "test-api-key")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer jwt-token-123")
                        .build()
        );
        
        // When & Then
        StepVerifier.create(converter.convert(exchange))
                .assertNext(auth -> {
                    assertTrue(auth instanceof ApiKeyAuthentication);
                    assertEquals("test-api-key", auth.getCredentials());
                })
                .verifyComplete();
    }
}