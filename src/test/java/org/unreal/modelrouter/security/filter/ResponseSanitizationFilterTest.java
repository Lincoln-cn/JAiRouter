package org.unreal.modelrouter.security.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.exception.SanitizationException;
import org.unreal.modelrouter.filter.ResponseSanitizationFilter;
import org.unreal.modelrouter.sanitization.SanitizationService;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ResponseSanitizationFilter 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ResponseSanitizationFilterTest {
    
    @Mock
    private SanitizationService sanitizationService;
    
    @Mock
    private SecurityAuditService auditService;
    
    @Mock
    private WebFilterChain filterChain;
    
    private SecurityProperties securityProperties;
    private ResponseSanitizationFilter filter;
    private DataBufferFactory bufferFactory;
    
    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getSanitization().getResponse().setEnabled(true);
        securityProperties.getSanitization().getResponse().setLogSanitization(true);
        securityProperties.getSanitization().getResponse().setFailOnError(false);
        
        filter = new ResponseSanitizationFilter(sanitizationService, auditService, securityProperties);
        bufferFactory = new DefaultDataBufferFactory();
        
        // Mock audit service with lenient stubbing
        lenient().when(auditService.recordEvent(any(SecurityAuditEvent.class))).thenReturn(Mono.empty());
    }
    
    @Test
    void testFilter_ExcludedPath() {
        // 创建健康检查请求
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(filterChain).filter(exchange);
        verifyNoInteractions(sanitizationService);
    }
    
    @Test
    void testFilter_SuccessfulSanitization() {
        String originalResponse = "{\"message\":\"Your password is secret123\",\"phone\":\"13812345678\"}";
        String sanitizedResponse = "{\"message\":\"Your password is *********\",\"phone\":\"[PHONE]\"}";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/chat")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟响应体
        DataBuffer responseBuffer = bufferFactory.wrap(originalResponse.getBytes(StandardCharsets.UTF_8));
        
        when(sanitizationService.sanitizeResponse(originalResponse, "application/json"))
                .thenReturn(Mono.just(sanitizedResponse));
        
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            // 模拟设置响应内容类型和写入响应
            modifiedExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return modifiedExchange.getResponse().writeWith(Mono.just(responseBuffer));
        });
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeResponse(originalResponse, "application/json");
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testFilter_UnsupportedContentType() {
        String responseContent = "binary-image-data";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/image")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟响应体
        DataBuffer responseBuffer = bufferFactory.wrap(responseContent.getBytes(StandardCharsets.UTF_8));
        
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            // 设置为不支持的内容类型
            modifiedExchange.getResponse().getHeaders().setContentType(MediaType.IMAGE_JPEG);
            return modifiedExchange.getResponse().writeWith(Mono.just(responseBuffer));
        });
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 不应该调用脱敏服务
        verifyNoInteractions(sanitizationService);
    }
    
    @Test
    void testFilter_SanitizationFailure_ContinueOnError() {
        String responseContent = "{\"message\":\"Your password is secret123\"}";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/chat")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 配置为失败时继续处理
        securityProperties.getSanitization().getResponse().setFailOnError(false);
        
        // 模拟响应体
        DataBuffer responseBuffer = bufferFactory.wrap(responseContent.getBytes(StandardCharsets.UTF_8));
        
        when(sanitizationService.sanitizeResponse(responseContent, "application/json"))
                .thenReturn(Mono.error(new SanitizationException("脱敏失败", SanitizationException.SANITIZATION_FAILED)));
        
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            modifiedExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return modifiedExchange.getResponse().writeWith(Mono.just(responseBuffer));
        });
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeResponse(responseContent, "application/json");
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testFilter_SanitizationFailure_FailOnError() {
        String responseContent = "{\"message\":\"Your password is secret123\"}";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/chat")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 配置为失败时中断处理
        securityProperties.getSanitization().getResponse().setFailOnError(true);
        
        // 模拟响应体
        DataBuffer responseBuffer = bufferFactory.wrap(responseContent.getBytes(StandardCharsets.UTF_8));
        
        when(sanitizationService.sanitizeResponse(responseContent, "application/json"))
                .thenReturn(Mono.error(new SanitizationException("脱敏失败", SanitizationException.SANITIZATION_FAILED)));
        
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            modifiedExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return modifiedExchange.getResponse().writeWith(Mono.just(responseBuffer));
        });
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeResponse(responseContent, "application/json");
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
        
        // 验证响应状态码被设置为错误状态
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.getResponse().getStatusCode());
    }
    
    @Test
    void testFilter_EmptyResponse() {
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            modifiedExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            // 返回空的响应体
            return modifiedExchange.getResponse().writeWith(Flux.empty());
        });
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 空响应不应该调用脱敏服务
        verifyNoInteractions(sanitizationService);
    }
    
    @Test
    void testFilter_LoggingDisabled() {
        String originalResponse = "{\"message\":\"Your password is secret123\"}";
        String sanitizedResponse = "{\"message\":\"Your password is *********\"}";
        
        // 禁用日志记录
        securityProperties.getSanitization().getResponse().setLogSanitization(false);
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/chat")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        // 模拟响应体
        DataBuffer responseBuffer = bufferFactory.wrap(originalResponse.getBytes(StandardCharsets.UTF_8));
        
        when(sanitizationService.sanitizeResponse(originalResponse, "application/json"))
                .thenReturn(Mono.just(sanitizedResponse));
        
        when(filterChain.filter(any(ServerWebExchange.class))).thenAnswer(invocation -> {
            ServerWebExchange modifiedExchange = invocation.getArgument(0);
            modifiedExchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            return modifiedExchange.getResponse().writeWith(Mono.just(responseBuffer));
        });
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeResponse(originalResponse, "application/json");
        // 不应该记录审计事件
        verify(auditService, never()).recordEvent(any(SecurityAuditEvent.class));
    }
}