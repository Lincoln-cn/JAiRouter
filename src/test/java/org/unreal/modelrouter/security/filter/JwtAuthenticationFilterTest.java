package org.unreal.modelrouter.security.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.authentication.JwtTokenValidator;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.exception.AuthenticationException;
import org.unreal.modelrouter.security.model.JwtAuthentication;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * JwtAuthenticationFilter单元测试
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {
    
    @Mock
    private JwtTokenValidator jwtTokenValidator;
    
    @Mock
    private SecurityAuditService auditService;
    
    @Mock
    private WebFilterChain filterChain;
    
    private SecurityProperties securityProperties;
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @BeforeEach
    void setUp() {
        // 初始化安全配置
        securityProperties = new SecurityProperties();
        SecurityProperties.JwtConfig jwtConfig = new SecurityProperties.JwtConfig();
        jwtConfig.setEnabled(true);
        securityProperties.setJwt(jwtConfig);
        
        // 模拟审计服务
        when(auditService.recordEvent(any(SecurityAuditEvent.class))).thenReturn(Mono.empty());
        
        // 创建测试对象
        jwtAuthenticationFilter = new JwtAuthenticationFilter(
            jwtTokenValidator, securityProperties, auditService);
    }
    
    @Test
    void testFilter_ValidBearerToken_ShouldAuthenticate() {
        // 准备测试数据
        String token = "valid.jwt.token";
        String bearerToken = "Bearer " + token;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/test")
            .header(HttpHeaders.AUTHORIZATION, bearerToken)
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 创建模拟的认证对象
        JwtAuthentication authentication = new JwtAuthentication(
            "testuser", token, Arrays.asList("user", "admin"));
        authentication.setAuthenticated(true);
        
        // 模拟JWT验证成功
        when(jwtTokenValidator.validateToken(token)).thenReturn(Mono.just(authentication));
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).validateToken(token);
        verify(filterChain).filter(any());
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testFilter_ValidJwtHeader_ShouldAuthenticate() {
        // 准备测试数据
        String token = "valid.jwt.token";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/test")
            .header("X-JWT-Token", token)
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 创建模拟的认证对象
        JwtAuthentication authentication = new JwtAuthentication(
            "testuser", token, Arrays.asList("user"));
        authentication.setAuthenticated(true);
        
        // 模拟JWT验证成功
        when(jwtTokenValidator.validateToken(token)).thenReturn(Mono.just(authentication));
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).validateToken(token);
        verify(filterChain).filter(any());
    }
    
    @Test
    void testFilter_TokenFromQueryParameter_ShouldAuthenticate() {
        // 准备测试数据
        String token = "valid.jwt.token";
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/test")
            .queryParam("token", token)
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 创建模拟的认证对象
        JwtAuthentication authentication = new JwtAuthentication(
            "testuser", token, Arrays.asList("user"));
        authentication.setAuthenticated(true);
        
        // 模拟JWT验证成功
        when(jwtTokenValidator.validateToken(token)).thenReturn(Mono.just(authentication));
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).validateToken(token);
        verify(filterChain).filter(any());
    }
    
    @Test
    void testFilter_NoToken_ShouldContinueWithoutAuthentication() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/test")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟过滤器链继续处理
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }
    
    @Test
    void testFilter_InvalidToken_ShouldReturnUnauthorized() {
        // 准备测试数据
        String token = "invalid.jwt.token";
        String bearerToken = "Bearer " + token;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/test")
            .header(HttpHeaders.AUTHORIZATION, bearerToken)
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟JWT验证失败
        AuthenticationException authException = new AuthenticationException("令牌无效", "JWT_INVALID");
        when(jwtTokenValidator.validateToken(token)).thenReturn(Mono.error(authException));
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证响应状态码
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).validateToken(token);
        verify(filterChain, never()).filter(any());
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testFilter_ExpiredToken_ShouldReturnUnauthorized() {
        // 准备测试数据
        String token = "expired.jwt.token";
        String bearerToken = "Bearer " + token;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/test")
            .header(HttpHeaders.AUTHORIZATION, bearerToken)
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟JWT令牌过期
        AuthenticationException authException = new AuthenticationException("令牌已过期", "JWT_EXPIRED");
        when(jwtTokenValidator.validateToken(token)).thenReturn(Mono.error(authException));
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证响应状态码
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).validateToken(token);
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void testFilter_BlacklistedToken_ShouldReturnUnauthorized() {
        // 准备测试数据
        String token = "blacklisted.jwt.token";
        String bearerToken = "Bearer " + token;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/test")
            .header(HttpHeaders.AUTHORIZATION, bearerToken)
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟JWT令牌在黑名单中
        AuthenticationException authException = new AuthenticationException("令牌已被列入黑名单", "JWT_BLACKLISTED");
        when(jwtTokenValidator.validateToken(token)).thenReturn(Mono.error(authException));
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证响应状态码
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).validateToken(token);
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void testFilter_HealthCheckEndpoint_ShouldSkipAuthentication() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/actuator/health")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟过滤器链继续处理
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }
    
    @Test
    void testFilter_SwaggerEndpoint_ShouldSkipAuthentication() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/swagger-ui/index.html")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟过滤器链继续处理
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }
    
    @Test
    void testFilter_StaticResource_ShouldSkipAuthentication() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/static/css/style.css")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟过滤器链继续处理
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证JWT验证器未被调用
        verify(jwtTokenValidator, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }
    
    @Test
    void testFilter_UnexpectedError_ShouldReturnInternalServerError() {
        // 准备测试数据
        String token = "valid.jwt.token";
        String bearerToken = "Bearer " + token;
        
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/test")
            .header(HttpHeaders.AUTHORIZATION, bearerToken)
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟JWT验证过程中发生未预期的错误
        RuntimeException unexpectedException = new RuntimeException("Unexpected error");
        when(jwtTokenValidator.validateToken(token)).thenReturn(Mono.error(unexpectedException));
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证响应状态码
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        
        // 验证JWT验证器被调用
        verify(jwtTokenValidator).validateToken(token);
        verify(filterChain, never()).filter(any());
    }
    
    @Test
    void testExtractToken_MalformedBearerToken_ShouldReturnNull() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
            .get("/api/v1/test")
            .header(HttpHeaders.AUTHORIZATION, "InvalidBearer token")
            .build();
        
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟过滤器链继续处理
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        // 执行测试
        StepVerifier.create(jwtAuthenticationFilter.filter(exchange, filterChain))
            .verifyComplete();
        
        // 验证JWT验证器未被调用（因为无法提取有效令牌）
        verify(jwtTokenValidator, never()).validateToken(anyString());
        verify(filterChain).filter(exchange);
    }
}