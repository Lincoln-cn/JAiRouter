package org.unreal.modelrouter.security.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import org.unreal.modelrouter.exception.AuthenticationException;
import org.unreal.modelrouter.filter.SpringSecurityAuthenticationFilter;
import org.unreal.modelrouter.security.audit.SecurityAuditService;
import org.unreal.modelrouter.security.config.properties.SecurityProperties;
import org.unreal.modelrouter.security.constants.SecurityConstants;
import org.unreal.modelrouter.security.model.SecurityAuditEvent;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * API Key认证过滤器的单元测试
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ApiKeyServiceInterface apiKeyServiceInterface;

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private WebFilterChain filterChain;

    private SecurityProperties securityProperties;
    private SpringSecurityAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.getApiKey().setEnabled(true);
        securityProperties.getApiKey().setHeaderName("X-API-Key");
        
        filter = new SpringSecurityAuthenticationFilter(securityProperties, null, null);
        
        // 使用lenient模式避免不必要的stubbing警告
        lenient().when(auditService.recordEvent(any(SecurityAuditEvent.class))).thenReturn(Mono.empty());
        lenient().when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    @Test
    void testFilter_Success() {
        // 准备测试数据
        ApiKeyInfo apiKeyInfo = createTestApiKey("test-key", Arrays.asList("read", "write"));
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-API-Key", "valid-api-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟服务调用
        when(apiKeyServiceInterface.validateApiKey("valid-api-key")).thenReturn(Mono.just(apiKeyInfo));
        when(apiKeyServiceInterface.updateUsageStatistics("test-key", true)).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        // 验证认证上下文已设置
        assertEquals(apiKeyInfo, exchange.getAttributes().get(SecurityConstants.API_KEY_INFO_ATTRIBUTE));
        assertEquals("test-key", exchange.getAttributes().get(SecurityConstants.AUTHENTICATED_USER_ID));
        assertEquals(Arrays.asList("read", "write"), exchange.getAttributes().get(SecurityConstants.USER_PERMISSIONS));

        // 验证服务调用
        verify(apiKeyServiceInterface).validateApiKey("valid-api-key");
        verify(apiKeyServiceInterface).updateUsageStatistics("test-key", true);
        verify(filterChain).filter(exchange);
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }

    @Test
    void testFilter_MissingApiKey() {
        // 准备测试数据 - 没有API Key的请求
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 执行测试
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        // 验证响应状态
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());

        // 验证服务没有被调用
        verify(apiKeyServiceInterface, never()).validateApiKey(anyString());
        verify(filterChain, never()).filter(any());
        // 缺少API Key的情况下，审计服务应该被调用
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }

    @Test
    void testFilter_InvalidApiKey() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-API-Key", "invalid-api-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟服务调用 - 返回认证异常
        when(apiKeyServiceInterface.validateApiKey("invalid-api-key"))
                .thenReturn(Mono.error(AuthenticationException.invalidApiKey()));

        // 执行测试
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        // 验证响应状态
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());

        // 验证服务调用
        verify(apiKeyServiceInterface).validateApiKey("invalid-api-key");
        verify(filterChain, never()).filter(any());
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }

    @Test
    void testFilter_InsufficientPermissions() {
        // 准备测试数据 - API Key只有read权限，但请求是POST
        ApiKeyInfo apiKeyInfo = createTestApiKey("test-key", List.of("read"));
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .header("X-API-Key", "valid-api-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟服务调用
        when(apiKeyServiceInterface.validateApiKey("valid-api-key")).thenReturn(Mono.just(apiKeyInfo));

        // 执行测试
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        // 验证响应状态
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());

        // 验证服务调用
        verify(apiKeyServiceInterface).validateApiKey("valid-api-key");
        verify(filterChain, never()).filter(any());
        // 权限不足的情况下，审计服务应该被调用
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }

    @Test
    void testFilter_AdminPermissionAllowsAll() {
        // 准备测试数据 - API Key有admin权限
        ApiKeyInfo apiKeyInfo = createTestApiKey("admin-key", List.of("admin"));
        MockServerHttpRequest request = MockServerHttpRequest
                .delete("/api/test")
                .header("X-API-Key", "admin-api-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟服务调用
        when(apiKeyServiceInterface.validateApiKey("admin-api-key")).thenReturn(Mono.just(apiKeyInfo));
        when(apiKeyServiceInterface.updateUsageStatistics("admin-key", true)).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        // 验证认证上下文已设置
        assertEquals(apiKeyInfo, exchange.getAttributes().get(SecurityConstants.API_KEY_INFO_ATTRIBUTE));

        // 验证服务调用
        verify(apiKeyServiceInterface).validateApiKey("admin-api-key");
        verify(apiKeyServiceInterface).updateUsageStatistics("admin-key", true);
        verify(filterChain).filter(exchange);
    }

    @Test
    void testFilter_ExcludedPath() {
        // 准备测试数据 - 健康检查端点
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/actuator/health")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 执行测试
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        // 验证直接通过，没有进行认证
        verify(apiKeyServiceInterface, never()).validateApiKey(anyString());
        verify(filterChain).filter(exchange);
        verify(auditService, never()).recordEvent(any());
    }

    @Test
    void testFilter_AuthorizationHeader() {
        // 准备测试数据 - 使用Authorization头
        ApiKeyInfo apiKeyInfo = createTestApiKey("test-key", List.of("read"));
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("Authorization", "Bearer valid-api-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟服务调用
        when(apiKeyServiceInterface.validateApiKey("valid-api-key")).thenReturn(Mono.just(apiKeyInfo));
        when(apiKeyServiceInterface.updateUsageStatistics("test-key", true)).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        // 验证认证成功
        assertEquals(apiKeyInfo, exchange.getAttributes().get(SecurityConstants.API_KEY_INFO_ATTRIBUTE));
        verify(apiKeyServiceInterface).validateApiKey("valid-api-key");
        verify(filterChain).filter(exchange);
    }

    @Test
    void testFilter_EmptyPermissions() {
        // 准备测试数据 - API Key没有配置权限（应该允许所有操作）
        ApiKeyInfo apiKeyInfo = createTestApiKey("test-key", Collections.emptyList());
        MockServerHttpRequest request = MockServerHttpRequest
                .post("/api/test")
                .header("X-API-Key", "valid-api-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟服务调用
        when(apiKeyServiceInterface.validateApiKey("valid-api-key")).thenReturn(Mono.just(apiKeyInfo));
        when(apiKeyServiceInterface.updateUsageStatistics("test-key", true)).thenReturn(Mono.empty());

        // 执行测试
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        // 验证认证成功（空权限列表应该允许所有操作）
        assertEquals(apiKeyInfo, exchange.getAttributes().get(SecurityConstants.API_KEY_INFO_ATTRIBUTE));
        verify(apiKeyServiceInterface).validateApiKey("valid-api-key");
        verify(filterChain).filter(exchange);
    }

    @Test
    void testFilter_ServiceException() {
        // 准备测试数据
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/test")
                .header("X-API-Key", "valid-api-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        // 模拟服务调用 - 抛出非认证异常
        when(apiKeyServiceInterface.validateApiKey("valid-api-key"))
                .thenReturn(Mono.error(new RuntimeException("Database connection failed")));

        // 执行测试
        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        // 验证响应状态
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());

        // 验证服务调用
        verify(apiKeyServiceInterface).validateApiKey("valid-api-key");
        verify(filterChain, never()).filter(any());
        verify(auditService).recordEvent(any(SecurityAuditEvent.class));
    }

    @Test
    void testPermissionMapping() {
        // 测试不同HTTP方法的权限映射
        ApiKeyInfo readOnlyKey = createTestApiKey("read-key", List.of("read"));
        ApiKeyInfo writeKey = createTestApiKey("write-key", List.of("write"));
        ApiKeyInfo deleteKey = createTestApiKey("delete-key", List.of("delete"));

        // GET请求需要read权限
        testPermissionForMethod(readOnlyKey, HttpMethod.GET, true);
        testPermissionForMethod(writeKey, HttpMethod.GET, false);

        // POST请求需要write权限
        testPermissionForMethod(readOnlyKey, HttpMethod.POST, false);
        testPermissionForMethod(writeKey, HttpMethod.POST, true);

        // DELETE请求需要delete权限
        testPermissionForMethod(readOnlyKey, HttpMethod.DELETE, false);
        testPermissionForMethod(deleteKey, HttpMethod.DELETE, true);
    }

    private void testPermissionForMethod(ApiKeyInfo apiKeyInfo, HttpMethod method, boolean shouldSucceed) {
        MockServerHttpRequest request = MockServerHttpRequest
                .method(method, "/api/test")
                .header("X-API-Key", "test-api-key")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        lenient().when(apiKeyServiceInterface.validateApiKey("test-api-key")).thenReturn(Mono.just(apiKeyInfo));
        if (shouldSucceed) {
            lenient().when(apiKeyServiceInterface.updateUsageStatistics(apiKeyInfo.getKeyId(), true)).thenReturn(Mono.empty());
        }

        StepVerifier.create(filter.filter(exchange, filterChain))
                .verifyComplete();

        if (shouldSucceed) {
            assertEquals(apiKeyInfo, exchange.getAttributes().get(SecurityConstants.API_KEY_INFO_ATTRIBUTE));
            verify(filterChain).filter(exchange);
        } else {
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
            verify(filterChain, never()).filter(exchange);
        }

        // 重置mock
        reset(filterChain, apiKeyServiceInterface);
        lenient().when(auditService.recordEvent(any(SecurityAuditEvent.class))).thenReturn(Mono.empty());
        lenient().when(filterChain.filter(any())).thenReturn(Mono.empty());
    }

    /**
     * 创建测试用的API Key信息
     */
    private ApiKeyInfo createTestApiKey(String keyId, java.util.List<String> permissions) {
        return ApiKeyInfo.builder()
                .keyId(keyId)
                .keyValue("test-api-key-value")
                .description("测试API Key")
                .enabled(true)
                .permissions(permissions)
                .build();
    }
}