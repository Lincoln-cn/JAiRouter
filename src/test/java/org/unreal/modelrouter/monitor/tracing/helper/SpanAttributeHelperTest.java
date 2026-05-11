package org.unreal.modelrouter.monitor.tracing.helper;

import io.opentelemetry.api.trace.Span;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.RequestPath;
import org.springframework.web.server.ServerWebExchange;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * SpanAttributeHelper 单元测试
 *
 * @author JAiRouter Team
 * @since 2.27.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SpanAttributeHelper 单元测试")
class SpanAttributeHelperTest {

    private SpanAttributeHelper spanAttributeHelper;

    @Mock
    private Span span;

    @Mock
    private ServerHttpRequest request;

    @Mock
    private ServerWebExchange exchange;

    @Mock
    private ServerHttpResponse response;

    @Mock
    private RequestPath requestPath;

    @BeforeEach
    void setUp() {
        spanAttributeHelper = new SpanAttributeHelper();
    }

    private void setupRequestPath(String path) {
        when(requestPath.value()).thenReturn(path);
        when(request.getPath()).thenReturn(requestPath);
    }

    @Test
    @DisplayName("设置 HTTP 请求属性 - 正常请求")
    void setHttpAttributes_shouldSetAllAttributes() {
        // Given
        setupRequestPath("/api/test");
        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(URI.create("http://localhost:8080/api/test?param=value"));
        when(request.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());
        // 使用真实的 InetSocketAddress 而不是 unresolved
        InetSocketAddress remoteAddress = new InetSocketAddress("192.168.1.1", 8080);
        when(request.getRemoteAddress()).thenReturn(remoteAddress);

        // When
        spanAttributeHelper.setHttpAttributes(span, request);

        // Then
        verify(span).setAttribute("http.method", "GET");
        verify(span).setAttribute("http.url", "http://localhost:8080/api/test?param=value");
        verify(span).setAttribute("http.scheme", "http");
        verify(span).setAttribute("http.host", "localhost");
        verify(span).setAttribute("http.target", "/api/test");
        verify(span).setAttribute("http.client_ip", "192.168.1.1");
    }

    @Test
    @DisplayName("设置 HTTP 请求属性 - 带 User-Agent")
    void setHttpAttributes_shouldSetUserAgent() {
        // Given
        setupRequestPath("/api/test");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("User-Agent", "Mozilla/5.0");
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getURI()).thenReturn(URI.create("http://localhost/api/test"));
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(null);

        // When
        spanAttributeHelper.setHttpAttributes(span, request);

        // Then
        verify(span).setAttribute("http.user_agent", "Mozilla/5.0");
    }

    @Test
    @DisplayName("设置 HTTP 请求属性 - 带 Content-Length")
    void setHttpAttributes_shouldSetContentLength() {
        // Given
        setupRequestPath("/api/test");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("Content-Length", "1024");
        when(request.getMethod()).thenReturn(HttpMethod.POST);
        when(request.getURI()).thenReturn(URI.create("http://localhost/api/test"));
        when(request.getHeaders()).thenReturn(headers);
        when(request.getRemoteAddress()).thenReturn(null);

        // When
        spanAttributeHelper.setHttpAttributes(span, request);

        // Then
        verify(span).setAttribute("http.request_content_length", 1024L);
    }

    @Test
    @DisplayName("设置 HTTP 响应属性 - 正常响应")
    void setResponseAttributes_shouldSetAllAttributes() {
        // Given
        org.springframework.http.HttpHeaders responseHeaders = new org.springframework.http.HttpHeaders();
        responseHeaders.setContentLength(2048L);
        when(response.getStatusCode()).thenReturn(HttpStatus.OK);
        when(response.getHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponse()).thenReturn(response);

        // When
        spanAttributeHelper.setResponseAttributes(span, exchange, 100L);

        // Then
        verify(span).setAttribute("http.status_code", 200);
        verify(span).setAttribute("http.response_time_ms", 100L);
        verify(span).setAttribute("http.response_content_length", 2048L);
    }

    @Test
    @DisplayName("设置 HTTP 响应属性 - 错误状态码")
    void setResponseAttributes_shouldSetErrorStatusCode() {
        // Given
        org.springframework.http.HttpHeaders responseHeaders = new org.springframework.http.HttpHeaders();
        when(response.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
        when(response.getHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponse()).thenReturn(response);

        // When
        spanAttributeHelper.setResponseAttributes(span, exchange, 50L);

        // Then
        verify(span).setAttribute("http.status_code", 500);
        verify(span).setAttribute("http.response_time_ms", 50L);
    }

    @Test
    @DisplayName("获取客户端 IP - X-Forwarded-For 头部")
    void getClientIp_shouldExtractFromXForwardedFor() {
        // Given
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Forwarded-For", "192.168.1.100, 10.0.0.1");
        when(request.getHeaders()).thenReturn(headers);

        // When
        String clientIp = spanAttributeHelper.getClientIp(request);

        // Then
        assertEquals("192.168.1.100", clientIp);
    }

    @Test
    @DisplayName("获取客户端 IP - X-Real-IP 头部")
    void getClientIp_shouldExtractFromXRealIp() {
        // Given
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("X-Real-IP", "10.0.0.50");
        when(request.getHeaders()).thenReturn(headers);

        // When
        String clientIp = spanAttributeHelper.getClientIp(request);

        // Then
        assertEquals("10.0.0.50", clientIp);
    }

    @Test
    @DisplayName("获取客户端 IP - 远程地址")
    void getClientIp_shouldUseRemoteAddress() {
        // Given
        when(request.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());
        // 使用真实的 InetSocketAddress 而不是 unresolved
        InetSocketAddress remoteAddress = new InetSocketAddress("172.16.0.1", 12345);
        when(request.getRemoteAddress()).thenReturn(remoteAddress);

        // When
        String clientIp = spanAttributeHelper.getClientIp(request);

        // Then
        assertEquals("172.16.0.1", clientIp);
    }

    @Test
    @DisplayName("获取客户端 IP - 无可用地址")
    void getClientIp_shouldReturnNullWhenNoAddress() {
        // Given
        when(request.getHeaders()).thenReturn(new org.springframework.http.HttpHeaders());
        when(request.getRemoteAddress()).thenReturn(null);

        // When
        String clientIp = spanAttributeHelper.getClientIp(request);

        // Then
        assertNull(clientIp);
    }

    @Test
    @DisplayName("提取请求属性 - 完整请求")
    void extractRequestAttributes_shouldExtractAllAttributes() {
        // Given
        setupRequestPath("/api/users/123");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.add("User-Agent", "TestAgent/1.0");
        headers.add("Content-Length", "512");
        when(request.getMethod()).thenReturn(HttpMethod.PUT);
        when(request.getURI()).thenReturn(URI.create("https://example.com/api/users/123"));
        when(request.getHeaders()).thenReturn(headers);
        // 使用真实的 InetSocketAddress 而不是 unresolved
        InetSocketAddress remoteAddress = new InetSocketAddress("203.0.113.1", 443);
        when(request.getRemoteAddress()).thenReturn(remoteAddress);

        // When
        Map<String, Object> attributes = spanAttributeHelper.extractRequestAttributes(request);

        // Then
        assertEquals("PUT", attributes.get("http.method"));
        assertEquals("https://example.com/api/users/123", attributes.get("http.url"));
        assertEquals("/api/users/123", attributes.get("http.path"));
        assertEquals("https", attributes.get("http.scheme"));
        assertEquals("example.com", attributes.get("http.host"));
        assertEquals("203.0.113.1", attributes.get("http.client_ip"));
        assertEquals("TestAgent/1.0", attributes.get("http.user_agent"));
        assertEquals(512L, attributes.get("http.request_content_length"));
    }

    @Test
    @DisplayName("提取交换属性 - 包含响应信息")
    void extractExchangeAttributes_shouldIncludeResponseInfo() {
        // Given
        setupRequestPath("/test");
        org.springframework.http.HttpHeaders requestHeaders = new org.springframework.http.HttpHeaders();
        org.springframework.http.HttpHeaders responseHeaders = new org.springframework.http.HttpHeaders();
        responseHeaders.setContentLength(1024L);

        when(request.getMethod()).thenReturn(HttpMethod.GET);
        when(request.getURI()).thenReturn(URI.create("http://localhost/test"));
        when(request.getHeaders()).thenReturn(requestHeaders);
        when(request.getRemoteAddress()).thenReturn(null);
        when(exchange.getRequest()).thenReturn(request);
        when(exchange.getResponse()).thenReturn(response);
        when(response.getStatusCode()).thenReturn(HttpStatus.CREATED);
        when(response.getHeaders()).thenReturn(responseHeaders);

        // When
        Map<String, Object> attributes = spanAttributeHelper.extractExchangeAttributes(exchange);

        // Then
        assertEquals(201, attributes.get("http.status_code"));
        assertEquals(1024L, attributes.get("http.response_content_length"));
        assertEquals("GET", attributes.get("http.method"));
    }
}