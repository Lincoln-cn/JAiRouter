package org.unreal.modelrouter.security.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.filter.filter.RequestSanitizationFilter;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.config.SecurityProperties;
import org.unreal.modelrouter.security.constants.SecurityConstants;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import org.unreal.modelrouter.sanitization.SanitizationService;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * RequestSanitizationFilter 单元测试
 */
@ExtendWith(MockitoExtension.class)
class RequestSanitizationFilterTest {
    
    @Mock
    private SanitizationService sanitizationService;
    
    @Mock
    private SecurityAuditService auditService;
    
    @Mock
    private WebFilterChain filterChain;
    
    private SecurityProperties securityProperties;
    private RequestSanitizationFilter filter;
    private DataBufferFactory bufferFactory;
    
    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getSanitization().getRequest().setEnabled(true);
        securityProperties.getSanitization().getRequest().setLogSanitization(true);
        securityProperties.getSanitization().getRequest().setFailOnError(false);
        
        filter = new RequestSanitizationFilter(sanitizationService, auditService, securityProperties);
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
    void testFilter_UnsupportedContentType() {
        // 创建图片上传请求
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/upload")
                .contentType(MediaType.IMAGE_JPEG)
                .body("binary-image-data");
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(filterChain).filter(exchange);
        verifyNoInteractions(sanitizationService);
    }
    
    @Test
    void testFilter_EmptyRequestBody() {
        // 创建空请求体的请求
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(0)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(filterChain).filter(exchange);
        verifyNoInteractions(sanitizationService);
    }
    
    @Test
    void testFilter_WhitelistedUser() {
        String requestBody = "{\"password\":\"secret123\"}";
        String userId = "admin-user";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(SecurityConstants.AUTHENTICATED_USER_ID, userId);
        
        when(sanitizationService.isUserWhitelisted(userId)).thenReturn(Mono.just(true));
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).isUserWhitelisted(userId);
        verify(filterChain).filter(exchange);
        verify(sanitizationService, never()).sanitizeRequest(anyString(), anyString(), anyString());
    }
    
    @Test
    void testFilter_SuccessfulSanitization() {
        String originalContent = "{\"password\":\"secret123\",\"phone\":\"13812345678\"}";
        String sanitizedContent = "{\"password\":\"*********\",\"phone\":\"[PHONE]\"}";
        String userId = "test-user";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .body(originalContent);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(SecurityConstants.AUTHENTICATED_USER_ID, userId);
        
        when(sanitizationService.isUserWhitelisted(userId)).thenReturn(Mono.just(false));
        when(sanitizationService.sanitizeRequest(originalContent, "application/json", userId))
                .thenReturn(Mono.just(sanitizedContent));
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).isUserWhitelisted(userId);
        verify(sanitizationService).sanitizeRequest(originalContent, "application/json", userId);
        verify(filterChain, atLeastOnce()).filter(any());
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testFilter_NoSanitizationNeeded() {
        String content = "{\"message\":\"hello world\"}";
        String userId = "test-user";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(SecurityConstants.AUTHENTICATED_USER_ID, userId);
        
        when(sanitizationService.isUserWhitelisted(userId)).thenReturn(Mono.just(false));
        when(sanitizationService.sanitizeRequest(content, "application/json", userId))
                .thenReturn(Mono.just(content)); // 返回相同内容，表示无需脱敏
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeRequest(content, "application/json", userId);
        verify(filterChain, atLeastOnce()).filter(any());
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testFilter_AnonymousUser() {
        String originalContent = "{\"password\":\"secret123\"}";
        String sanitizedContent = "{\"password\":\"*********\"}";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .body(originalContent);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        // 没有设置用户ID，模拟匿名用户
        
        when(sanitizationService.sanitizeRequest(originalContent, "application/json", null))
                .thenReturn(Mono.just(sanitizedContent));
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeRequest(originalContent, "application/json", null);
        verify(filterChain, atLeastOnce()).filter(any());
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testFilter_SanitizationFailure_ContinueOnError() {
        String content = "{\"password\":\"secret123\"}";
        String userId = "test-user";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(SecurityConstants.AUTHENTICATED_USER_ID, userId);
        
        // 配置为失败时继续处理
        securityProperties.getSanitization().getRequest().setFailOnError(false);
        
        when(sanitizationService.isUserWhitelisted(userId)).thenReturn(Mono.just(false));
        when(sanitizationService.sanitizeRequest(content, "application/json", userId))
                .thenReturn(Mono.error(new RuntimeException("脱敏失败")));
        when(filterChain.filter(exchange)).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeRequest(content, "application/json", userId);
        verify(filterChain, atLeastOnce()).filter(any()); // 应该继续处理原始请求
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testFilter_SanitizationFailure_FailOnError() {
        String content = "{\"password\":\"secret123\"}";
        String userId = "test-user";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .body(content);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(SecurityConstants.AUTHENTICATED_USER_ID, userId);
        
        // 配置为失败时中断处理
        securityProperties.getSanitization().getRequest().setFailOnError(true);
        
        when(sanitizationService.isUserWhitelisted(userId)).thenReturn(Mono.just(false));
        when(sanitizationService.sanitizeRequest(content, "application/json", userId))
                .thenReturn(Mono.error(new RuntimeException("脱敏失败")));
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeRequest(content, "application/json", userId);
        verify(filterChain, never()).filter(any()); // 不应该继续处理
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }
    
    @Test
    void testFilter_WhitelistCheckFailure() {
        String originalContent = "{\"password\":\"secret123\"}";
        String sanitizedContent = "{\"password\":\"*********\"}";
        String userId = "test-user";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .body(originalContent);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(SecurityConstants.AUTHENTICATED_USER_ID, userId);
        
        when(sanitizationService.isUserWhitelisted(userId))
                .thenReturn(Mono.error(new RuntimeException("白名单检查失败")));
        when(sanitizationService.sanitizeRequest(originalContent, "application/json", userId))
                .thenReturn(Mono.just(sanitizedContent));
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        // 白名单检查失败时应该继续执行脱敏
        verify(sanitizationService).isUserWhitelisted(userId);
        verify(sanitizationService).sanitizeRequest(originalContent, "application/json", userId);
        verify(filterChain, atLeastOnce()).filter(any());
    }
    
    @Test
    void testFilter_XmlContent() {
        String originalContent = "<user><password>secret123</password></user>";
        String sanitizedContent = "<user><password>*********</password></user>";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_XML)
                .body(originalContent);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(sanitizationService.sanitizeRequest(originalContent, "application/xml", null))
                .thenReturn(Mono.just(sanitizedContent));
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeRequest(originalContent, "application/xml", null);
        verify(filterChain, atLeastOnce()).filter(any());
    }
    
    @Test
    void testFilter_TextContent() {
        String originalContent = "My password is secret123";
        String sanitizedContent = "My password is *********";
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.TEXT_PLAIN)
                .body(originalContent);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(sanitizationService.sanitizeRequest(originalContent, "text/plain", null))
                .thenReturn(Mono.just(sanitizedContent));
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeRequest(originalContent, "text/plain", null);
        verify(filterChain, atLeastOnce()).filter(any());
    }
    
    @Test
    void testFilter_LoggingDisabled() {
        String originalContent = "{\"password\":\"secret123\"}";
        String sanitizedContent = "{\"password\":\"*********\"}";
        
        // 禁用日志记录
        securityProperties.getSanitization().getRequest().setLogSanitization(false);
        
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/data")
                .contentType(MediaType.APPLICATION_JSON)
                .body(originalContent);
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        
        when(sanitizationService.sanitizeRequest(originalContent, "application/json", null))
                .thenReturn(Mono.just(sanitizedContent));
        when(filterChain.filter(any())).thenReturn(Mono.empty());
        
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();
        
        verify(sanitizationService).sanitizeRequest(originalContent, "application/json", null);
        verify(filterChain, atLeastOnce()).filter(any());
        // 不应该记录审计事件
        verify(auditService, never()).recordEvent(any(SecurityAuditEvent.class));
    }
}